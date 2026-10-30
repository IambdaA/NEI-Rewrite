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
