# NEI Rewrite 1.20.1 (multiloader): porting notes

## Layout

```
common/     all the mod's code and assets. Plain Minecraft code (mojmap): no Forge, no Fabric API.
forge/      ForgeEntry (@Mod, server events), ForgeClient (client half: screen/tick/render events, keys, fluid items),
            ForgePlatform / ForgeClientPlatform, ForgeNet / ForgeClientNet (event channels), NEIRegisterEvent, mods.toml
fabric/     FabricEntry, FabricClient (Fabric API callbacks), FabricPlatform / FabricClientPlatform / FabricClientNet, KeyTable,
            five mixins, accesswidener, fabric.mod.json
```

Each loader module adds `../common/src/main/java` and `../common/src/main/resources` to its own source sets, so `common/` has no
build of its own and is compiled once per loader. This is the 1.21.1 project moved back to 1.20.1.

## What 1.20.1 changes compared with 1.21.1

- **No payload codecs:** `NEIPacket` writes itself into a `FriendlyByteBuf` and `PacketType(id, reader)` reads it back. Forge: one
  `EventNetworkChannel` per packet id (accepts any remote version and a missing channel, like JEI's own); Fabric: one channel id per
  packet with `ServerPlayNetworking` / `ClientPlayNetworking` (reads the buffer at once, runs the handler on the game thread).
- **Items are NBT:** `getTag()` / `hasTag()` / `NbtUtils.compareNbt` instead of data components; `Stacks.withCount` for `copyWithCount`;
  `setHoverName`; `getTooltipLines(player, TooltipFlag.NORMAL)`; `new ResourceLocation(...)`.
- **Container screen frame:** `leftPos`/`topPos`/`imageWidth`/`imageHeight`/`hoveredSlot` are protected. `ClientPlatform` has
  `guiLeft/guiTop/guiWidth/guiHeight/hoveredSlot`, `Frame` is the common helper. Forge answers with its public getters
  (`getGuiLeft()` ... `getSlotUnderMouse()`), Fabric widens the fields (`notenoughitems.accesswidener`).
- **Smithing recipes:** only the public `SmithingRecipe.isTemplateIngredient / isBaseIngredient / isAdditionIngredient` are used (the
  slots are the registry items that fit), so no access transformer is needed.
- **Forge key mappings:** `new KeyMapping(name, KeyConflictContext.GUI, ...)` for GUI-only keys.
- **Forge events:** `TickEvent.ClientTickEvent` (END), `ScreenEvent.*` (HIGHEST for mouse press, wheel and key press),
  `ScreenEvent.Opening` (cancelable) for the creative inventory, `InputEvent.MouseScrollingEvent`, `RenderGuiEvent.Post`,
  `RenderLevelStageEvent`. `ScreenEvent.MouseScrolled` / `MouseScrollingEvent` have one `getScrollDelta()` in 1.20.1.
- **Forge reobfuscation:** ModDevGradle Legacy (`net.neoforged.moddev.legacyforge` 2.0.148) develops with Mojang names and reobfuscates the jar
  to SRG; JEI's Forge jar is SRG and goes through `modRuntimeOnly` (remapped for the dev client).
- **Fabric mixins:** same five as 1.21.1, same names (`render(GuiGraphics, int, int, float)`, `charTyped(long, int, int)`, `onScroll`,
  `setScreen`, `resetMapping`); `KeyboardHandler.charTyped` takes an `int` code point in 1.20.1 (JEI's own mixin confirms it).
- **JEI 15:** `IGuiProperties` has getters (`getScreenClass()`, `getGuiLeft()` ...), `IRecipeCategoriesLookup`, `createRecipeLayoutDrawable`
  returns `Optional`; Fabric's `IJeiFluidIngredient.getFluid()` (no `getFluidVariant`), amount in droplets (81 per mB); Forge's fluid ingredient
  is `FluidStack` (mB). The jar names are `jei-1.20.1-common-api`, `jei-1.20.1-forge`, `jei-1.20.1-common-api-intermediary`, `jei-1.20.1-fabric-api`,
  `jei-1.20.1-fabric`.

## Stage 44 / version 1.1.2 (1.20.1)

- `common-1.20.1` (earlier work) became `common/` of this project: the 1.21.1 `common/` patch applied (history fix, fluid ids,
  `TerminalSearchSync`, wildcard fluids) and converted to NBT; `NEIJeiPlugin` and `JeiGui` rewritten for JEI 15 (checked against the JEI
  1.20.1 branch sources: `IGuiProperties`, `Internal.getClientToggleState`, `ScreenHelper.guiScreenHandlers`).
- Loader modules written against the Forge 1.20.1 branch sources (events, `NetworkRegistry.newEventChannel`, `ModLoader.get().postEvent`,
  the `getGuiLeft()` patch, `KeyMapping` constructors) and the Fabric API 1.20.1 branch sources (screen, networking, rendering events).
- Versions in `gradle.properties`: Forge 47.4.23 (JEI's own dev version), Fabric Loader 0.19.4 and Fabric API 0.92.11+1.20.1 (what JEI 15 is built
  with; the mod itself only needs loader 0.15), `jei_version=15.+`, Gradle 9.7.0, Loom 1.18.3, Java 17 toolchain.
- Not built here (no Minecraft jars, the build hosts are not reachable): the Java files were only syntax-checked with `javac`. Everything is
  unverified against 1.20.1 until the user builds it.


### Status (1.20.1): confirmed working on Forge and Fabric
- The user built and played stage 1 on both loaders with JEI 15 and reported that it works. Version 1.1.2 is the release version.
- Not covered by that report (no detail was given): every item of the test checklist (server join with client-only fluid items, packet
  features on a dedicated server, NBT identity of potions / books / fluids, JEI-drawn recipes) was not confirmed one by one.

## Older stages (from the 26.x and 1.21.1 projects, kept for context)

## Stage 42: fluid display items no longer depend on the item registry
- Report (26.2, NeoForge 26.2.0.90, on a modded server): fluids cannot be bookmarked, have no uses / recipes, are missing from bookmark groups and draw blank in bookmark trees, although the log said "fluids with recipes: 199, with uses: 202".
- Suspected cause (NOT confirmed): after joining a server that does not have the client-only display items, the item registry no longer holds them, so every `BuiltInRegistries.ITEM.getKey(item)` answers `minecraft:air` (ids, bookmarks, history, panel list all go through that).
- Change: `FluidDisplay` remembers the id of each display item (`add(item, fluid, id)`, `idOf(item)`); every `BuiltInRegistries.ITEM.getKey(...).toString()` for stacks now calls `FluidDisplay.idOf`; `ItemEntry.registryId` / `numericId` use it; `ItemList.load` adds the display items from `FluidDisplay.items()` even if the registry lacks them.
- New log lines to read: `NEI item list: N items, F fluid display items made, R of them still in the item registry` (R < F confirms the cause) and `NEI cannot draw fluid <id>: no still sprite` (once per fluid; says the blank slots are a drawing problem instead).
- Untested: parse-checked only.


## Stage 42b: favorite result choice, fluids in JEI-drawn recipes
- Report: viewing a recipe drawn by JEI, R / U over another fluid did nothing (the layout only reported item stacks under the mouse); and a recipe favorited with the heart was stored under its FIRST result, so a recipe tree could not find it for an ingredient that is a later result (e.g. unsaturated hydrocarbons, the 1st / 4th output).
- As in NEI (`GuiFavoriteButton`): while the mouse is on a recipe's heart, the mouse wheel changes which result the heart is about (`selectedResult`); that result is greyed with the heart on it (`drawItemOverlay`), the heart shows whether the recipe is that result's favorite, and the click favorites it for that result (`RecipeBookmarks.toggleFavorite(view, itemId)`). Alt over the heart lists "MOUSE WHEEL: Change favorite result" when the recipe has several results.
- JEI-drawn recipes: `JeiDrawn.itemAt` now uses the slot under the mouse (items and fluids; a fluid is its display item), so R / U / bookmark work on fluids. `Drawn.highlightResult` marks the chosen result with JEI's `IRecipeSlotView.drawHighlight`.
- Not changed (as in NEI): a favorite is per item; a tree only uses the favorite recipe of the ingredient itself.
- Untested: parse-checked only.


## Stage 42d: favorite for a chosen result without the wheel
- Report: a recipe favorited with the heart is stored under its first result only (NEI does the same), so Shift + A (recipe tree) did not find it for an ingredient that is a later result (oil refinery: unsaturated hydrocarbons is the 4th output). The user wants no scrolling, and doubts that "any favorite that makes it" is right (a better recipe may be favorited elsewhere).
- Main fix: Shift + F (`favorite_item`) over a RESULT slot of a recipe in the recipe window favorites that recipe for that result (`RecipeOverlay.favoriteItemKey`, extends NEI's key, which over an ingredient with alternatives favorites the item); again removes it. The item's own favorite always wins in trees. Alt help lists "Favorite Recipe for this Result" over a result.
- Option "Single favorite counts for all its results" (`favoriteAnyResult`, default OFF): `RecipeBookmarks.favoriteKeyFor(item)` = the item's own favorite, else the ONE favorited recipe that has it among its results; with two or more candidates nothing is guessed. Used by the recipe tree, favorite-tree save, Shift + overlay, chat link.
- The stage 42b wheel over the heart (NEI's `selectedResult`) is the option "Choose favorite result with the mouse wheel" (`favoriteWheel`, default OFF).
- Untested: parse-checked only.


## Stage 42e / version 1.1.0
- The item tooltip of a recipe result (recipe window, `Where.RECIPE`, `HotkeyHelp.recipeResult`) gets one more line in the same box, just above the mod name: "SHIFT + F: Favorite recipe for this result" (`ItemTooltip.favoriteLine`). Not a separate box.
- Version 1.1.0 (`gradle.properties`, `CHANGELOG.md`, `RELEASE.md` jar names).
- Multiloader: the JEI bridge (`NEIJeiPlugin`, `JeiGui`) is in `pending/jei/` again: the multiloader build has no JEI dependency yet, so it must not be in `common/`. It is uncompiled, written from JEI 26.2 source (JEI 19.x names unverified).
- Untested: parse-checked only.

## Stage 43 / version 1.1.2 (1.21.1)

- Ported the 26.x changes to this project: 21 files patched in `common/` (fluid ids with JEI subtype identity, amounts, T key,
  mB-only option, lead-result bookmark rows, R lookup focus).
- New `jei/NEIJeiPlugin.java` (JEI 19 API: `ResourceLocation`, `createRecipeCatalystLookup`, `createRecipeLayoutDrawable` returning
  `Optional`) and `jei/JeiGui.java`. JEI 19 has no `IJeiFeatures.disableJeiGui()`, so `JeiGui.hide()` sets
  `Internal.getClientToggleState()` overlay/bookmark off by reflection.
- NeoForge: JEI repo, `compileOnly` common-api + neoforge-api, `localRuntime` neoforge jar, optional `jei` dependency in `neoforge.mods.toml`;
  `NeoPlatform.fluidOf/fluidAmount`.
- Fabric: Loom remaps, JEI Fabric jars are intermediary, so `modCompileOnly` / `modRuntimeOnly`; `jei_mod_plugin` entrypoint;
  `FabricPlatform.fluidOf/fluidAmount` by reflection (amount / 81).
- `neo_version` 21.1.235. `jei_version=19.+` is dynamic until the exact version of the user's build is known.
- Not compiled here (no Minecraft jars): everything above is unverified against 1.21.1 until the user builds it.

### Toolchain bump (1.21.1, after the first build report)
- First build failed: "Mod was built with a newer version of Loom (1.18.2), you are using Loom (1.10.5)". Loom's cache is shared between
  projects, and the user's other projects had already written 1.21.1 files with Loom 1.18.2. Now: Loom `net.fabricmc.fabric-loom-remap`
  1.18.3 (the remapping plugin id of Loom 1.18+, still mojmap + intermediary JEI jars), ModDevGradle 2.0.148, Gradle 9.7.0 (Loom 1.18.3 needs it; the 26.x projects use 9.5.0 with Loom 1.17.20).
- Not built here.

### First play test (1.21.1)
- JEI 19 draws `configButton`, `bookmarkButton` and `historyButton` whenever it has valid GUI properties, whatever the toggle state says.
  `JeiGui.hide(screenHelper)` replaces JEI's handler for `AbstractContainerScreen` (map `guiScreenHandlers` of the library `ScreenHelper`, by
  reflection, once the runtime is up: JEI's own GUI plugin registers in an order we cannot rely on) with one that reports the whole screen
  as the GUI, so JEI has no room left for its panels and buttons.
- `NEIClientEvents.addHistory` used the item only, so every fluid of `hbm:fluid_icon` became the plain item ("None") and replaced the others.
  It now records the lookup's focus stack (`RecipeOverlay.pendingFocus()`) and dedupes by id. The same bug is in the 26.x projects.
- Input priority: Fabric `EARLY` phase before the default phase (`addPhaseOrdering`) for key press, click and scroll; NeoForge `EventPriority.HIGHEST`.
- Open: "NEI does not show in creative" is not understood yet; a log line per screen class now prints the panel layout.
- Not built here.

### Second play test (1.21.1 Fabric, JEI 19.70.0.464)
- Log: JEI version is 19.70.0.464 (kept dynamic as `19.+` on purpose). NEI panel code ran on `class_481` (creative) but nothing was visible.
- `FabricClient.live` compared a global init counter: creative opens inside the survival screen's init, so the survival init event comes last
  and the creative screen was "dead". The counter is per screen now (WeakHashMap) and the screen must be the open one; a log line says
  when the open screen is not live.
- `KeyTable.fix` handled only NEI mappings sitting in vanilla's table; it now also takes JEI's (`key.jei.*`) out, gives the key back to a vanilla
  mapping first, and runs again every 20 ticks. Logs once what it changed, or that the table was not found.
- Not built here.
