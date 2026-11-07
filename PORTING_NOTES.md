# NEI Rewrite 1.21.1 (multiloader): porting notes

## Layout

```
common/     all the mod's code and assets. Plain Minecraft code (mojmap): no NeoForge, no Fabric API.
neoforge/   NeoForgeEntry (@Mod, payloads, server events), NeoForgeClient (client @Mod, all screen/tick/render events),
            NeoPlatform / NeoClientPlatform, accesstransformer.cfg, mods.toml template
fabric/     FabricEntry, FabricClient (Fabric API callbacks), FabricPlatform / FabricClientPlatform, five mixins,
            accesswidener, fabric.mod.json
pending/    the JEI bridge (NEIJeiPlugin.java), parked until stage 2
```

Each loader module adds `../common/src/main/java` and `../common/src/main/resources` to its own source sets, so `common/` has no
build of its own and is compiled once per loader.

## The loader seam (everything in `common` that a loader fills in)

| Seam | What it covers |
|---|---|
| `Platform` / `NotEnoughItems.init` | config dir, mod display name, mod loaded, send to server, server has channel, send to player, client has channel |
| `ClientPlatform` (`HOLDER`) | create a hotkey (GUI-only or not), fluid sprite + tint |
| `ClientHooks` | the one door for client events: setup, tick, screen init, render pre/post, mouse press/post/release/scroll, key press/release, char typed, screen opening, world scroll, HUD, world render |
| `ScreenEv` | one event object (cancelable) so the handlers in `NEIClientEvents` do not care where it came from |
| `NEINetwork.TO_SERVER / TO_CLIENT` | the packets, each loader registers them with its own network API |
| `Cfg` | own small JSON config (no `ModConfigSpec`, which Fabric does not have) at `config/notenoughitems-client.json` |
| `api/NEIPlugin` (Fabric) and `NEIRegisterEvent` (NeoForge) | NEI's `IConfigureNEI.loadConfig()` |

## What changed from the 26.x code, and why

- **Drawing:** 1.21.1 draws the GUI with a depth buffer (26.x draws in call order). All drawing goes through `Gfx`: fills ignore
  depth, text is lifted over items (z +250), textures are drawn without depth test, `Gfx.layer` clears depth before a window.
- **Recipes:** 1.21.1 sends every recipe to every client, so `ClientRecipes` reads the connection's recipe manager; no sync event
  and no server install needed for lookup.
- **Key table (Fabric):** vanilla holds one mapping per key. NEI hotkeys act only in screens and use keys such as A and R, so on Fabric `KeyTable.fix()` (called after registering and from a `resetMapping` tail hook) takes GUI-only mappings out of vanilla's key table again. NeoForge uses key conflict contexts instead.
  Fabric `KeyMappingMixin` (resetMapping tail) keeps GUI-only mappings out of vanilla's key table (NEI compares keys itself). NeoForge uses key
  conflict contexts instead.
- **Reflection removed** (permission check, tree screenshot): Fabric runs intermediary names at runtime, name-based reflection breaks.
- **Slot under mouse:** `getSlotUnderMouse()` is a NeoForge patch, so the code reads the `hoveredSlot` field (access transformer / widener).
- **Smithing recipes:** the template/base/addition fields are package-private; access transformer / widener.
- **Mixins on Fabric** (NeoForge has events for these): `ContainerScreenMixin` (render pre, cancel), `KeyboardHandlerMixin` (typed char),
  `MinecraftMixin` (screen opening), `MouseHandlerMixin` (wheel with no screen), `KeyMappingMixin` (resetMapping tail).
- Fluid display items are added at `RegisterEvent` (NeoForge) or in the client initializer (Fabric; if the registry is already frozen
  there the mod logs a warning and goes without them).

## Status: stage 1 confirmed working on NeoForge and Fabric

Both loaders compile and run (NeoForge 21.1.172, Fabric Loader 0.16.10, Fabric API 0.115.0+1.21.1, Loom 1.10-SNAPSHOT, ModDevGradle 2.0.78, Gradle 8.14.3 were confirmed by the user's build).

Fabric: Fabric's per-screen events may be cleared on resize or not; every registration therefore carries a generation number.
The typed-character mixin passes `char`, so characters outside the basic plane are cut to one UTF-16 unit (the search box does not need them).

## Stages

1. (this) common + NeoForge + Fabric, everything except the JEI bridge
2. JEI bridge for 1.21.1 (needs the exact JEI 19.x file names)
3. 1.20.1: Forge + Fabric added to this same project

## Credits text
Same credits / "how it differs" sections as the 26.x projects (store texts, README, mods.toml, fabric.mod.json contributors).


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

## Stage 56f (1.21.1): the 26.2 stages 55-56f brought over
- Request: the user has no FTB mods on 26.2, so the stage 56f work (NTNH-Approximate buttons, panels that keep away from other mods' inventory widgets) is sent for 1.21.1; because 1.21.1 had none of stages 55-56e either, those came along: the NEI-style full-screen settings (stage 55, 56b-56e), the Alt tooltip / panel layering fixes (stage 56) and the UI Style option.
- Method: the 26.2 files that changed between 1.1.2 and stage 56f (NEIConfig, NEIClientEvents, Toolbar, Draw, ItemPanel, ItemTooltip, ClientHooks, HotkeyHelp, HighlightTips, BookmarkPanel, SubsetMenu, RecipeOverlay, CraftingTreeOverlay, NEIJeiPlugin) were merged three ways (1.21.1 file, translated 26.2 1.1.2 file, translated 26.2 stage 56f file; the translation is mechanical: `GuiGraphicsExtractor` -> `GuiGraphics`, `Identifier` -> `ResourceLocation`, `g.fill / text / item / itemDecorations / fillGradient` -> `Gfx.*`, `pushMatrix` -> `pushPose`, `gui.screen()` -> `screen`). The translated 1.1.2 file matched the 1.21.1 file within a few lines everywhere, so the merge was clean except `NEIClientEvents` (two spots, resolved by hand). New files: `SettingsScreen`, `ColorPickerPopup`, `HighlightTipsScreen`, `EffectAreas`, `GuiAreas` (translated the same way, then the input methods rewritten by hand: `render`, `mouseClicked(double, double, int)`, `mouseReleased`, `mouseDragged(double, double, int, double, double)`, `keyPressed(int, int, int)`, `charTyped(char, int)`). `SettingsWindow` is deleted (nothing uses it; `PresetsWindow` and `CreatorWindow` still use `ModalWindow`).
- Differences from 26.2 in the drawing code: `Gfx.fillGradient` (new, `RenderType.guiOverlay()`), `Draw.dirt` blits through `Gfx.blit` (`textures/gui/menu_background.png`), `ClientHooks.renderBackground` runs the panels with the pose stack set to identity at the current z (the container events come with the pose moved to the window's corner; JEI does the same), and `NEIClientEvents.onRender` still starts with `Gfx.layer` (1.21.1 draws with depth testing).
- Where the panels are drawn (stage 56's layering): NeoForge `ContainerScreenEvent.Render.Background` at `HIGHEST` (JEI 1.21.1 uses this event for its own panels on container screens), Fabric `ScreenMixin` (end of `Screen.renderMenuBackground`, and right after the first `fillGradient(IIIIII)` of `renderTransparentBackground`, JEI's two injection points). One tooltip while the Alt box is up: NeoForge `RenderTooltipEvent.Pre` cancelled at `HIGHEST` (JEI uses the same event), Fabric `GuiGraphicsMixin` cancels `renderTooltipInternal` at HEAD (JEI's own mixin has the same target). `fabric/.../notenoughitems.mixins.json` lists both new mixins.
- GuiAreas: unchanged from 26.2 (JEI exclusion areas through `IJeiRuntime#getScreenHelper().getGuiExclusionAreas`, potion boxes, foreign `AbstractWidget`s; `ItemPanel` hides slots under them and `contains` is false there; the bookmark panel is laid out in `GuiAreas.fit`). JEI 19 for 1.21.1 has that API (checked in the JEI 1.21.1 source).
- Not built here (no Minecraft jars). Parser pass over `common`: the only javac errors are missing Minecraft types and their knock-on errors. Not checked: the exact pose / depth behaviour of drawing the panels at the Background event in 1.21.1 (items are drawn with depth testing; JEI draws its item grid at the same point), that `ScreenMixin`'s two injection points apply in the Fabric 1.21.1 jar (taken from JEI's mixin, not run), the look of the settings screen and the stub rows, and the FTB Quests / FTB Library behaviour (the reason for sending 1.21.1).

## Stage 57 (1.21.1): the 26.2 Stage 57 and its fixes brought over
- Request: port the user-confirmed 26.2 Stage 57 (every greyed out settings row works; + moves items by default; U on a machine always lists its recipes; fluid tooltips with the mod's own lines) to 1.21.1.
- How: `translate.py` (26.2 -> 1.21.1 names) and `git merge-file` per file onto the 1.21.1 stage56f tree. Base: the 26.2 stage57-wip tree (56f + Group 1) for files Group 1 did not touch; for the Group 1 files the 26.2 1.1.2 tree (the exact 26.2 56f tree was not available). NEIConfig differed from 26.2 only by the Group 1 stubs and was taken whole; SettingsScreen and the new PanelSettingsScreen were taken from 26.2 with the input methods turned back into 1.21.1's (`mouseClicked(double, double, int)`, `keyPressed(int, int, int)`, `charTyped(char, int)`, `render`); NEIClientEvents was merged against the WIP and Group 1 (bookmarks visibility from the config, search widget position, AutoFocus) added by hand. 1.21.1's own changes were kept (history keyed by registry id, `Gfx.layer`, the screen log line).
- 1.21.1 differences: no `FluidTooltipEvent` in NeoForge 21.1, so `NeoPlatform.fluidTooltip` asks JEI's fluid renderer (`NEIJeiPlugin.fluidTooltip`, needs JEI; without it only NEI's Temperature / State lines show). NTM-Continuum's fluids are `hbm:fluid_icon` items, so their NTM lines come from the item's own tooltip as before. Time button: `Level.getDayTime()`. Creative tabs: `Gfx.sprite`. A tooltip's item blocks (Accepts, recipe card, chain lists) start a new depth layer (`Gfx.layer`) so the panel items under the tooltip cannot hide them.
- Checked: `./gradlew --offline build` here, NeoForge and Fabric jars built with no errors; the 56f tree was compiled first unchanged. Every Stage 57 feature found in the merged sources.
- Not checked in the game: all of it. Most likely to need a look: depth order in the new tooltip blocks and the recipe card, the creative tab sprite, scaled slots, the Panels Settings screen's input on 1.21.1, JEI's fluid tooltip lines.
