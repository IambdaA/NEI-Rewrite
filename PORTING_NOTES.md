# NotEnoughItems → NeoForge 26.2: porting notes

Unofficial port of the GTNH fork of NotEnoughItems (LGPL-3.0-or-later, see LICENSE.txt).

## Build

Requires JDK 25 (the MDK's toolchain setting) and network access to the NeoForge Maven.

    ./gradlew build          # jar in build/libs
    ./gradlew runClient      # dev client

Target: Minecraft 26.2, NeoForge 26.2.0.90 (from the official MDK), ModDevGradle 2.0.148.

## Status

| Area | State |
|---|---|
| Item panel, paging, search (`@mod #tooltip $tag &id -not a b a\|b "quotes" regex`), tooltips | **confirmed working in game on 26.2.0.90** |
| Panel in the creative inventory, recipe / usage lookup, bookmarks | **confirmed working** |
| Stage 4 UI: bookmarks as left panel, NEI-style recipe window with tabs; left click = recipes, right click = uses, shift + click = give, click outside closes | **confirmed working** (GUI polish still wanted) |
| Give items with cheats off | does not work (survival `/give`); parked |
| Stage 5: item variants (enchanted books, potions, ... from the creative search tab) | **confirmed working** |
| Stage 5: rebindable keys (Options > Controls > NotEnoughItems) | **confirmed working** |
| Stage 5: hold Alt over an item for a hotkey help box | **confirmed working** |
| Stage 5: bookmark amounts (shown on the item, Shift + wheel to change, saved in the bookmarks file) | **confirmed working** |
| Stage 5: recipe transfer, the green `+` in the recipe window (crafting table and 2x2 inventory grid) | **confirmed working** |
| Stage 5b: custom item tooltip (name, Hold ALT for hotkeys, mod name) | **confirmed working** |
| Stages 7-16: bookmark pages, groups, todo view, whole-recipe bookmarks, crafting chain groups, crafting tree window (stats panel, search), copy hotkeys, Shift + drag sorting | **confirmed working** (see the stage sections below) |
| Stage 17: recipe collapse (Alt + click) | **confirmed working** |
| Stage 18: recipe / group borders, dragging items between todo lists, Ctrl + hover on collapsed roots | **confirmed working** |
| Stage 19: collapsible items, craftables panel, item zoom, quantity field, presets, enchantment modifier + potion creator windows, mouse wheel transfer, highlight tooltips (HUD), settings window (19d) | **confirmed working** |
| Stage 20: recipe window keys, search history, bookmark keys, hide NEI | **confirmed working** |
| Stage 21: JEI recipe bridge (all mods' machines via JEI's runtime, fluid display items) | **confirmed working** (21b to 21g) |
| Stage 23: NEI's mod API (`api/API`, `NEIRegisterEvent`) | **confirmed working** |
| Stage 24: world hotkeys, search focus key, item panel sorting, history / subsets options | **confirmed working**; `SortOrder` unit-tested outside the game |
| Stage 22: release prep (icon, metadata, README, store texts in `RELEASE.md`, server-safety fix) | **confirmed working** |
| Stages 34-37: mob overlay, spawner items, delete / magnet, smelting animation | **confirmed working** |
| Stage 38: shift+drop / mass transfer, overlay Ctrl multiplier, NEI search modes + colours + expression mode, subset item columns, bookmark Ctrl/Shift amounts, villager trades, presets history | **not compiled yet** (parse-checked; search tested standalone) |
| Syntax of all source files | checked with the Java parser (no syntax errors); API names are NOT checked |
| Giving items with cheats off, IMC, catalyst call and transfer-handler call in the mod API | not started / parked |
| Stage 38: the NEI mod API (stage 23), tree link, screenshot, pull items | **confirmed working** (see their stages) |

## Controls

| Input | Action |
|---|---|
| Left click an item (either panel, or inside the recipe window) | its recipes |
| Right click an item | recipes that use it |
| Shift + click an item in a panel | give it: left = full stack, right = one (creative or cheats on) |
| `R` / `U` over any item | recipes / uses (rebindable) |
| `A` over any item | add / remove bookmark (rebindable) |
| `B` | show / hide the bookmark panel (rebindable) |
| Backspace in the recipe window | back to the previous item (rebindable) |
| Arrow keys / wheel in the recipe window | change recipe page (wheel over the tabs scrolls the tabs) |
| Hold Alt over an item | list the hotkeys that apply to it |
| `<` / `>` or wheel at the bottom of the bookmark panel | switch bookmark page (NEI "namespace") |
| Left drag on the thin strip left of the bookmarks | put those rows in a group (start on a group to resize it) |
| Right drag on the strip | take rows out of their group |
| Click a group's strip | todo view (one item per row); Alt + click collapses / expands it |
| Ctrl + wheel over a bookmark | change its amount (NEI's key; was Shift in earlier stages) |
| Green `+` in the recipe window | fill the crafting grid from your inventory; ingredients you lack are tinted red |
| Heart in the recipe window | favorite recipe for its result (stage 9) |
| `T` over a crafting chain group's strip | open that group's crafting tree window (rebindable, NEI `bookmark.open_crafting_tree`) |
| `Shift + T` over the heart | save the recipe tree as a group and open its tree window |
| In the tree window: wheel / Shift + wheel / Ctrl + wheel | scroll / scroll sideways / zoom; drag the background to pan |
| In the tree window: Ctrl + wheel over a root | change how many times its recipe is made (Alt: a stack per step) |
| In the tree window: Escape or the inventory key | leave the tree |
| Escape, or click outside everything | close the recipe window |

## UI layout

Right: item list (page row on top, search box under it). Left: bookmarks (page row at the bottom). Centre: the recipe window,
a grey vanilla-style panel over the dimmed inventory, with one tab per recipe type on its left edge, a title bar, a
`< Page n/m >` row and as many recipes as fit. The recipe window is an overlay drawn by `RecipeOverlay`, not a separate
screen, so the side panels stay visible and clickable.

Known differences from NEI (as of stage 12; most are ported since): `+` only works for the crafting table and the 2x2 inventory grid.

## Recipe lookup: how it works and its limits

- The server must opt in to sending recipes. `RecipeSync` does that for every recipe type, so **NEI has to be installed on the
  server** for lookup to work on multiplayer. Singleplayer and LAN hosts are fine.
- Recipes are read through vanilla `RecipeDisplay`/`SlotDisplay` (what the recipe book uses). Vanilla crafting (shaped,
  shapeless), smelting-style, stonecutting and smithing show full ingredients. Any other display (modded machines) shows its
  result and station only, no ingredients, until a per-mod converter is added.
- Tags and fuels cycle through their options once a second. Fuel slots are not indexed for "uses", otherwise every fuel
  would list every furnace recipe.
- If the screen says "No recipe data from the server", the server did not send recipes (check the log line
  "NEI received N recipes from the server").

## First compile: what is most likely to need fixing

I could not read the 26.2 sources, so these calls are best guesses. Each is tagged `PORT-CHECK` in the code.
Run `./gradlew compileJava` and fix in this order; the errors should point at exactly these lines.

1. `client/ItemPanel.java` and `client/NEIClientEvents.java`: `GuiGraphicsExtractor` package and the drawing calls
   `fill`, `text`, `item`, `setTooltipForNextFrame`. (Names come from the NeoForge 26.x screens docs; overload
   signatures were not shown.)
2. `client/NEIClientEvents.java`: `ScreenEvent` accessors. Likely changes: `getMouseButtonEvent()`,
   `getKeyEvent()`, `getCharacterEvent()`, `getScrollDeltaY()`, `getGuiGraphics()`, `getPartialTick()`.
   Also `AbstractContainerScreen#getGuiLeft()/getXSize()`.
3. `item/ItemEntry.java`: `getTooltipLines(Item.TooltipContext, Player, TooltipFlag)`, and the tag path
   `stack.typeHolder().tags()` / `TagKey#location()` (may now be `identifier()`; 26.x renamed
   `ResourceLocation` to `Identifier`).

## Known gaps / behavior differences from NEI

- Item list is one default stack per registry item. Variants (enchanted books, potions, ...) need creative-tab
  contents; planned for stage 2.
- Tooltip search (`#`) builds tooltips lazily on the search worker thread. If a mod's tooltip code is not
  thread safe this should move to the client thread.
- Not yet ported from NEI search: expression-mode (ANTLR) search, recipe tokens (`< >`), alternate space modes,
  alias/untranslated-name matching, collapsible item groups.
- Item list is built once on first container screen open; it is not rebuilt on language change or resource reload.
- Panel is hidden when there is less than one slot of free width beside the container screen.

## Suggested stage order

1. Get stage 1 compiling and running; confirm panel, paging, search, Escape/inventory-key behavior.
2. Networking + cheat mode (give item, delete item): needs NeoForge payload registration on both sides.
3. Recipe/usage lookup on top of the 26.x recipe system (data-driven; `RecipeManager` is no longer a global).
4. Bookmarks, then the recipe handler API and overlays.

## Stage 3 compile checklist (new, unverified calls)

Everything marked `PORT-CHECK` is a guess. The ones added in stage 3, most likely to fail first:

1. `recipe/RecipeConverter.java`: the accessors on the five display records (`width()`, `ingredients()`, `ingredient()`,
   `fuel()`, `input()`, `template()`, `base()`, `addition()`, `duration()`, `experience()`, `result()`,
   `craftingStation()`) and `SlotDisplay#resolveForStacks(ContextMap)`. The docs only show `resolve(ContextMap, factory)`; if
   `resolveForStacks` does not exist, use `resolve(context, new SlotDisplay.ItemStackContentsFactory())` and collect the stream.
2. `recipe/RecipeIndex.java`: `SlotDisplayContext.fromLevel(level)`.
3. `recipe/ClientRecipes.java`: `RecipeMap#byType`.
4. `client/RecipeScreen.java`: `Screen` constructor shape, `Gui#setScreen`, `MouseButtonEvent`/`KeyEvent` packages.
5. `NEIActions.java`: `getMenu().setCarried(...)` and `connection.sendCommand(...)` (the latter was also in stage 2).
6. `Bookmarks.java`: `FMLPaths` package. `client/NEIClientEvents.java`: `getSlotUnderMouse()`.

## Stage 4 compile checklist (new, unverified calls)

1. `client/NEIClientEvents.java`: `MouseButtonEvent#hasShiftDown()` (used for shift-click give).
2. `client/ItemPanel.java`, `client/RecipeOverlay.java`: `Font#width(String)`.
3. `RecipeScreen` no longer exists; its `Screen`/`setScreen`/input-record guesses are gone with it.
4. If you changed any files to get stage 3 compiling, those fixes may need re-applying to the files stage 4 replaces:
   `client/ItemPanel.java`, `client/NEIClientEvents.java`, `recipe/RecipeConverter.java` (small edit).

## Stage 5 compile checklist (new, unverified calls)

Sources for these guesses: the NeoForge 26.1 key mapping docs (`KeyMapping`, `isActiveAndMatches`), and a third-party 26.2 port
PR for the container-click rename. None were checked against 26.2 sources.

1. `recipe`-side: none. `ItemList.java`: `ItemStackLinkedSet.createTypeAndComponentsSet()`,
   `CreativeModeTabs.tryRebuildTabContents(FeatureFlagSet, boolean, HolderLookup.Provider)`,
   `CreativeModeTabs.searchTab().getSearchTabDisplayItems()`, `level.enabledFeatures()`, `level.registryAccess()`.
   If variants are missing in game, look for the warning "Could not read creative tab contents" in the log.
2. `client/NEIKeys.java`: `KeyMapping.Category` constructor, `KeyMapping` constructor order, `getTranslatedKeyMessage()`.
3. `client/NEIClientEvents.java`: `InputConstants.getKey(KeyEvent)`, `ScreenEvent.KeyReleased.Pre#getKeyEvent()`.
4. `client/RecipeTransfer.java`: `ContainerInput` (renamed from `ClickType` in 26.2, with `PICKUP` and, I assume,
   `QUICK_MOVE`), `MultiPlayerGameMode#handleContainerInput(int, int, int, ContainerInput, Player)`,
   `Slot#getContainerSlot()`, `AbstractContainerMenu#containerId`, `#getCarried()`.
5. `client/ItemPanel.java`: `ItemStack#copyWithCount(int)` (also used in `NEIActions`, which compiled).

Behaviour to check in game: recipe transfer when the grid is occupied, with a full inventory, with tag ingredients (planks),
and shapeless vs shaped; the `+` should only appear in a crafting table or the player inventory.

## Tooltip (stage 5b)

Panel and recipe-window tooltips are now drawn by `client/ItemTooltip.java` instead of the built-in
`setTooltipForNextFrame(font, stack, x, y)`, which showed nothing in game. Layout: name, "Hold ALT for hotkeys", the item's
normal extra lines (enchantments, potion effects), mod name in blue italic. Holding Alt swaps in the hotkey box. Container
slots keep vanilla's own tooltip.

New unverified calls: `MutableComponent#withStyle(UnaryOperator<Style>)`, `Style#withColor(int)`, `Style#withItalic(boolean)`,
`Font#width(Component)`. Not supported by this tooltip: tooltip images / custom `TooltipComponent`s from other mods, F3+H
advanced tooltips.

## Bookmark pages and groups (stage 7, from GTNH NEI's BookmarkPanel / BookmarkGrid / BookmarkGridGenerator)

Checked against the GTNH source: NEI's `< 1/2 >` at the bottom of the bookmark panel switches **namespaces** (pages), and a
**group** is a block of rows marked on a 7 px strip left of the grid. Stage 6's named groups were not NEI's feature and are
gone.

- `bookmark/BookmarkLayout.java` ports the slot generator (a group starts on a new row, todo view = one item per row, a
  collapsed group shows its first row) and `GroupingItem#prepareGroups`. `bookmark/BookmarkGrid.java` ports `createGroup`.
  Both are plain Java and were tested outside the game.
- `Bookmarks.java` ports `BookmarkStorage` page handling: one spare empty page after the last used one, empty pages dropped
  as you leave them. File format changed (old files and stage 6 files still load).
- `client/BookmarkPanel.java` draws the panel like NEI: page row on top, strip with the group brackets (grey `0xFF666666`,
  faint line for ungrouped rows), page row at the bottom; slot hover highlight is NEI's `0xEE555555`.
- Not ported yet: recipe bookmarks and crafting-chain groups (right click on the strip does nothing), Shift + drag sorting,
  the `P` pull button, bookmark quantity step with Ctrl + Alt, per-page remembered scroll.
- New unverified calls: `ScreenEvent.MouseButtonReleased.Pre` (`getMouseX/getMouseY/getButton`). Drag is read from the render
  loop, so no `MouseDragged` event is needed.

### Group strip tooltip (stage 7b)

Hovering the strip shows a "Bookmarks Group" tooltip with "Hold ALT for hotkeys"; Alt swaps in the hotkey box
(`LMB + Drag` Create/Include Group; inside a group also `RMB + Drag` Remove/Exclude Group, `LMB` Toggle Group Mode,
`Alt + LMB` Toggle Collapse/Expand). Wording from NEI's lang file. NEI also lists group quantity, crafting chain and sorting
there; those are left out until ported. NEI's item hotkey lines use its own names (e.g. "LMB"); this port's item help still
says "Left click" and could be aligned.

### Hotkey wording (stage 7c)

All hotkey help lines now use NEI's lang strings and key names (`LMB`, `RMB`, `SHIFT + LMB`, `CTRL + Scroll`, "Recipe to make this
item", "Recipes that use this item", "Add Bookmark", "Remove Item", "Change Quantity"). Lines NEI does not list were removed
(show/hide bookmarks, Esc, back). One deliberate difference: `SHIFT + LMB` "Give item (creative / cheats)" describes this port's
give binding; NEI gives with a plain click while cheating.

## Recipe bookmarks (stage 8)

As in NEI (`ShortcutInputHandler#saveRecipeInBookmark`, `BookmarkGrid#addRecipe/removeRecipe`):
- `SHIFT + A` over a recipe in the recipe window adds the whole recipe (result, then ingredients) to the bookmarks, or
  removes it if it is already there. Amounts start at the recipe's counts (NEI's default `bookmarkRecipeWithCount = true`; with Ctrl or not).
- Known differences: plain `A` on an item in the recipe window should save it as an item linked to that recipe (NEI default `bookmarkItemsWithRecipe = true`) - this port saves a plain item; `SHIFT + A` adds all results of a recipe, NEI only the hovered one; the "Remove Recipe" hotkey line shows on every bookmark slot, NEI only on recipe slots.
- `A` over a bookmark slot removes it; removing a recipe's result removes the whole recipe (`SHIFT + A` removes the
  recipe from any of its slots).
- Todo-list groups lay a recipe out as one row: result first, its ingredients after it. Collapsed groups hide
  ingredients.
- File format: recipe items carry a trailing `type recipeId factor` (1 result, 2 ingredient).
- Recipe id here is a hash of the recipe's slots (this port has no NEI recipe ids).
- Not ported: crafting trees, "pull items", recipe tooltips in bookmarks, shift-drag sorting.

## NEI alignment pass (stage 8b)

Checked against the GTNH source and changed to match:
- Plain bookmark key adds the item with amount 0 (no number); Ctrl adds it with 1. Numbers show whenever amount > 0.
  In the recipe window the item remembers the recipe (NEI `bookmarkItemsWithRecipe`), and pressing the key on a
  bookmarked result/item of that recipe removes it.
- Ctrl + wheel changes the multiplier (`shiftItemAmount` / `shiftRecipeAmount` / `shiftGroupAmount`, min 0); Alt makes a
  step the item's stack size; over a group's strip it scales the whole group.
- Shift + bookmark key adds only the hovered result of a recipe.
- Clicks: while cheating, a plain click gives the item and Shift + click opens recipes / uses (NEI `PanelWidget#mouseUp`).
  With cheats off, or the recipe window open, click = recipes (left) / uses (right).
- Recipe window: removed my heart button (NEI has none).
- Hotkey help: bookmark panel lines depend on the slot (plain: Remove Item; recipe: Remove Recipe); item lists show
  Add Bookmark / with Count / with Recipe (& Count).
Still not ported: favorites, crafting chains/trees, copy-name hotkeys, Ctrl+LMB infinite item, shift-drag sorting, pull items.

Stage 8c: the bookmark key now matches with Shift / Ctrl held (NeoForge's `isActiveAndMatches` rejected it), which
is what broke Shift + key in the recipe window. The `+` button shows for every crafting recipe and is greyed when it
does not fit the open screen's crafting grid (the 2x2 inventory grid only takes recipes up to 2x2).

## Favorites and recipe trees (stage 9)

As in NEI (`FavoriteRecipes`, `GuiFavoriteButton`, `BookmarkPanel#addGroup`, `RecipeChainMath`, `RecipeChainDetails`):
- The heart next to each recipe is NEI's favorite button (stage 8b removed it by mistake). It makes that recipe the
  favorite for its result item (one per item). Stored in `config/notenoughitems-favorites.txt`.
- `SHIFT + bookmark key` over the heart saves the recipe tree: the recipe plus, level by level (depth 9), the favorite
  recipe of each ingredient. It becomes a new group in todo list view with the crafting chain on.
- Crafting chain groups work out how many times each sub recipe must run (`bookmark/ChainMath.java`), so the amounts
  shown are the calculated ones; "xN" marks the first result of an output recipe. Right click on a group's strip
  toggles the crafting chain. Ctrl + wheel on a recipe item changes the multiplier of the whole recipe; on the strip,
  of the whole group.
- Recipe ingredients now add up identical slots (6 iron ingot slots = 6 iron ingots) and keep every item a slot accepts.
- Not ported: recipe collapse inside a chain, stats, container items (buckets), single recipe
  auto favorites (off by default in NEI), recipe handler icons (off by default in NEI).
- Amounts and markers are drawn at half size like NEI (uses `g.pose()`, marked PORT-CHECK).

## Crafting Tree window (stage 10)

As in NEI (`bookmark/tree/GuiCraftingTree`, `CraftingTreeCanvas`, `CraftingTreeGraph`, `CraftingTreeMath`, `Camera`,
`CraftingTreeState`, `CraftingTreeToolbar`). Opened with `T` over a crafting chain group's strip, or `Shift + T` over a recipe's
heart (saves the recipe tree first). It is a full-screen overlay (`client/CraftingTreeOverlay`) drawn on top of the container
screen; while it is open the container, panels and keys behind it are cut off.

What it shows: every output recipe of the group is a root; below each box hang the boxes of its recipe's ingredients,
recursively, using the other recipes of the group. A box shows how many of the item the tree needs; the small icon at its top left
is the recipe type (its station, or NEI's hammer); the +/- arrow below it hides / shows what is under it.

Interactions (all NEI's): wheel scrolls, Shift + wheel sideways, Ctrl + wheel zooms (0.1 to 8, step 1.4) around the mouse,
dragging the background pans; Ctrl + wheel over a root changes the recipe's multiplier (Alt: stack per step); click a box for its
recipes (left) / uses (right); the "+" in that recipe window (here shown for every recipe, tooltip "Use for recipe tree")
makes the recipe the one for the clicked box and drops the recipes only the old one needed; hovering the recipe icon turns it into
a red cross that removes that recipe (and what only it needed). Toolbar: inventory snapshot (the maths counts what is in the
open container's slots; boxes get a red / yellow / dark frame for missing / craftable / done, and the tooltip gets "In inventory",
"Still needed", "Can be crafted", "Extra produced"), fit to view, collapse mode (expanded / collapse finished / collapse all).
`T`, `A`, `R`, `U` over a box work as over any item.

Where the logic lives (all plain Java, tested outside the game: `T6`, `T7`):
- `bookmark/ChainMath.java` got hooks (`calculationStepTrigger`, `prepareIngredients`) and `bookmark/TreeMath.java` uses them
  to record the tree while it runs the same chain maths as the bookmark panel (verified: the activator rail example gives
  the same numbers as the bookmark panel, 6 / 6 / 2 / 1 and 18 / 18 / 6 / 3 at multiplier 3).
- `TreeGraph` (parents, children, unused recipes), `TreeLayout` (contour packing, hit tests), `TreeCamera`, `TreeState`
  (kept per group between openings), `TreeView` (everything the window can do), `ReadableNumber` (NEI's 12k / 1.5M).

Unverified calls (marked PORT-CHECK in the code), fix in `Tex.java` / the named class if they do not compile:
- `GuiGraphicsExtractor#blit(RenderPipeline, Identifier, x, y, float u, float v, w, h, texW, texH)` and
  `RenderPipelines.GUI_TEXTURED` (`Tex.blit`), `enableScissor` / `disableScissor` (`Tex.beginClip` / `endClip`).
- `ScreenEvent.Render.Pre` being cancellable (hides the container under the tree), `Options#keyInventory`,
  `ResultSlot`, `Slot#mayPickup`.
- Items and sprites drawn under a scaled `g.pose()` (zoom): if boxes look wrong when zoomed, that is the place.

Not ported (NEI has them): stats panel and its two toolbar buttons, the search field, the chat link and screenshot buttons,
Alt + click collapse of a single recipe (`collapsedRecipes`), the little recipe picture in the badge tooltip (a plain box
with the recipe type is shown), container items and fluids ("~" amounts), switching a slot's variant with Shift + wheel,
button click sounds, the toolbar buttons are drawn with fills instead of vanilla's button texture and the 16 px toolbar icons
are scaled to 8 px without smoothing (NEI smooths them).
Small differences: a left click on a box without recipes below it clears the "replace this box" mark (NEI leaves the old
one standing); the toolbar toggles last until the game closes (NEI saves them in the world).

## Layout, bottom bar, history, recipe window look (stage 11)

- **Creative panels:** the layout is now computed every frame from the screen's live `getGuiLeft()/getXSize()/width/height`,
  so a stale `ScreenEvent.Init.Post` (opening the inventory in creative swaps screens inside `init()`) cannot leave a wrong layout.
- **Bottom bar (NEI `LayoutStyleMinecraft`):** search field centered (width `min(xSize,176) - 4`, 20 high), wrench at x=2
  (opens this mod's config screen) and book at x=24 (toggles the bookmark panel), 20x20, icons from `nei_tabbed_sprites.png`.
  The wrench is always the grey (recipe mode) one; its tooltip says Cheat Mode when `cheatEnabled` is on.
- **History panel (NEI `ItemHistoryPanel`):** items whose recipes/uses were opened, newest first, no repeats, max 50, up to
  2 rows under the item grid with a dashed line above; clicks act like item panel clicks. Kept by item, not by item + NBT.
- **Recipe window (NEI `GuiRecipe`):** 24x24 tabs above the window (7 per row, arrows beyond that, wheel over the tab bar
  switches type), station column on the left (one entry per distinct crafting station among the synced recipes), title
  row and page row with 13x12 `< >` buttons, window up to 370 high, all nine crafting cells drawn.
- **Buttons:** `Draw.button` uses colours sampled from NEI 1.7.10's toolbar buttons.
- **Not ported:** villager trade tab (comes from a modpack addon, not NEI core),
  mod stations other than those named by synced recipes, NEI's own options screen (the wrench opens NeoForge's config screen).
- **New unverified calls:** `AbstractWidget#setX/setY/setWidth` (EditBox), `ModList#getModContainerById`,
  `ConfigurationScreen(ModContainer, Screen)`. `Minecraft#screen` and `Minecraft#setScreen` do not exist in 26.2 (compile error); the wrench
  finds `setScreen(Screen)` by reflection until the real location is known. `blit`, `RenderPipelines.GUI_TEXTURED` and scissor are confirmed by stage 10 running.

## Recipe search and toolbar (stage 12)

- **Recipe search (NEI `GuiRecipe#toggleSearch` / `RecipeSearchField`):** the magnifier (12x12, `nei_sprites.png` 0,76) next to the
  page row opens a 14 high field between it and the `n/m` page count; the recipe list keeps recipes where some item matches the
  text, using the item search syntax of the item list. A leading `<` searches inputs only, `>` outputs only (NEI also accepts
  these markers inside the text). Right click clears, Escape leaves the field, opening another item's recipes closes it.
- **Toolbar (NEI `LayoutStyleMinecraft`):** 18x17 buttons from the top left wrapping to the width left of the window, NEI's icons:
  delete, rain, gamemode, time (dawn / noon / dusk / midnight), heal. Everything except delete is a vanilla command through
  `ClientPacketListener#sendCommand` (`weather`, `gamemode`, `time set`, `effect give`), so server permissions apply. The bookmark panel
  starts below the toolbar. Shown in creative mode, or in survival when `toolbarInSurvival` is on (both need `cheatEnabled`; the permission check of 26.2 is not known here).
- **Not ported:** delete mode (click items to delete), shift-delete-all and the magnet (NEI does these with its server mod);
  delete only clears the cursor item, which is exact in the creative inventory only. Right click on a time button (disable a day
  phase) is NEI server-side.
- **New unverified calls:** `Player#isCreative`, `Level#isRaining`, `AbstractContainerMenu#getCarried`, the 26.2 syntax of the
  `time set <ticks>` and `weather` commands, and drawing the 512 px `nei_sprites.png` with texture size 256 (NEI's own coordinates
  are in 256 units).

## Item subsets (stage 13, from NEI's SubsetWidget)

- "Item Subsets" bar at the top centre (150 x 16, shown when the window has more than 20 pixels above it, as in NEI). Click it to open;
  hovering a row opens the menu of its children to the right; the menu closes half a second after the mouse leaves it. Left click
  shows a subset, right click hides it, double left click shows only that subset, double click on the bar shows everything, wheel
  scrolls a long menu, Alt over a row lists these keys. A row is dimmed when part of it is hidden and dark when all of it is.
- Hidden items leave the item list (`ItemList#updateFilter` skips them) and are saved in `config/notenoughitems-hidden.txt`.
- Groups (`Subsets#rebuild`): Blocks, Items (Tools > Pickaxes / Shovels / Axes / Hoes, Weapons > Swords, Armor > four pieces, Food,
  Potions), Mod > one per mod. Built from item tags (`minecraft:pickaxes` ... `minecraft:foot_armor`) and `BlockItem`.
- **Not ported:** columns of single items inside a leaf group (hide one item), the `%` search prefix and Shift + click to search a
  subset, creative tab groups, custom subset files, per-variant hiding (hiding works per registry id), the options to move or disable the bar.
- **New unverified calls:** `TagKey.create(Registries.ITEM, Identifier)`, `ItemStack#is(TagKey)`, `DataComponents.FOOD`,
  `AbstractContainerScreen#getGuiTop()`, and whether the item tags above exist under those names in 26.2.

### Stage 13b
- Subset rows get NEI's tooltip: the name and "Hold ALT for hotkeys"; Alt shows LMB / RMB / Double LMB.
- The toolbar needs permission to run operator commands (`Perms`): `hasPermissions(int)` or the newer `permissions()` form, found by reflection
  because the 26.2 API is unknown; the log says which one was used ("NEI reads command permission with ...") or that none was found
  (then the toolbar is shown anyway).

## Stage 14: copy hotkeys and tree search

As in NEI:
- **Copy hotkeys** (`ShortcutInputHandler`): Ctrl + C / X / D over an item (list, bookmarks, recipe window, tree box, slot)
  copies its name, id or tags. Rebindable under Controls. The name is quoted when it has a space (NEI's
  `quoteDropItemName`, default on). NEI's "oredict" is the item's tags here; the id has no damage suffix in 26.2.
- The Alt help box lists the three copy lines after "Recipes that use this item" (NEI order: name, oredict, id).
- **Tree search** (`GuiCraftingTree`): field at the top right of the tree window. Matching boxes stay normal, the others
  grey out, the current match is highlighted (sprite at 164,2). Enter / Shift + Enter step through the matches, opening
  collapsed boxes above the match and centring it. Right click on the field clears it.

Checked in NEI and left out:
- Pull items from a container (`bookmark.pull_items`): needs an `IBookmarkContainerHandler`, which only other mods register.
- Tree stats panel (`StatsPanel`, 755 lines), link / screenshot buttons, group sorting by Shift + drag, craft missing:
  not started. Say which to do next.
- Search escaping by pattern mode (copy name only escapes quotes).

## Stage 15: tree stats panel

As in NEI (`StatsPanel`, `StatsSectionsMenu`, `CraftingTreeToolbar`):
- **Panel** along the bottom of the tree window: labelled blocks Ingredients, Crafting, Results, Handlers; the layout is
  NEI's `layoutSections` (blocks grow wider while there is room, two rows when the window is tall). The tree gets the
  room above it. The numbers are `bookmark/TreeStats` (plain Java, tested standalone: ingredients, results, remainders,
  crafting amounts, handler counts for a 3 recipe chain).
- **Toolbar**: button 4 shows / hides the panel (icon changes while a section is off), button 5 opens the sections menu
  (seven toggles, as in NEI). Both are per group and last until the game closes, like the other toolbar toggles.
- **Click a slot** to highlight its boxes in the tree and jump to the first (Shift + click goes backwards, right click
  on the lit slot clears it). Typing in the search field replaces it. Hover: a handler shows its counts, a crafting entry
  its recipe type, an item its normal tooltip (Alt for hotkeys, R / U / bookmark keys work on it).
- Wheel over the panel scrolls it sideways when it is wider than the window.

Differences: "handler" is the recipe type's title (this port has no handler classes); no container items in the
remainders; no draggable scrollbar thumb; a crafting entry's tooltip has no recipe picture.

## Stage 16: bookmark sorting and Alt + click expand

As in NEI (`BookmarkPanel`, `SortableGroup`, `SortableItem`, `BookmarkGrid#moveGroup / moveItem`):
- **Shift + drag a group's strip** moves the group: it takes the place of the row under the mouse (before it when
  dragging up, after it when dragging down; a row in the middle of another group waits until the mouse reaches the
  group's edge).
- **Shift + drag a bookmark** moves it inside its group, before / after the one under the mouse. A recipe's result
  takes its whole recipe along in a todo or crafting group; an ingredient of a todo list stays among its recipe's items.
  The dragged slots follow the mouse. A Shift + click that does not become a drag still opens the recipes (as before).
- **Alt + click on a bookmark** expands its group when it is collapsed (the click is always taken, as in NEI).
- The Alt help boxes list "Move Position" (slots and strip) and "Toggle Collapse/Expand" (slots of a collapsed group).
- Logic in `BookmarkGrid.moveGroup / moveItem / sortUnit`, tested standalone (plain items, a todo recipe, groups up
  and down).

Not ported: dropping an item into another group of a todo list while sorting (NEI's insert-between-groups branch),
drawing the group's strip while dragging a group, and Alt + click toggling one recipe of a crafting chain
(`bookmark.recipe.toggle_collapse`): NEI feeds the collapsed recipes into the chain maths, so it needs `ChainMath`
changes; that is the next job if wanted.

## Stage 17: recipe collapse (NEI's `bookmark.recipe.toggle_collapse`)

- Alt + click on a slot of a crafting chain group folds its recipe (and the recipes made only for it) into the recipe,
  or unfolds it. Saved as `#collapse <group> <recipe key>` lines.
- `ChainMath.of(items, collapsed)`: a collapsed recipe is always an output recipe; `collapse()` ports the collapsed branch
  of `RecipeChainDetails` (top level recipes, relations, merged results / raw ingredients, `realMultiplier` from a sub chain).
- Normal view: the folded recipes' items are hidden, the recipe's results show the merged amounts. Todo view: one row
  (result first, then remainders, then raw ingredients) of stand-in slots that are not in the item list.
- `toggleCollapsedRecipe` moves a middle recipe's stored multiplier 1 <-> 0 like NEI; removing a slot of a collapsed
  recipe only works for the whole set (`removeSlot(.., true)`), as in NEI.
- Tree: a collapsed recipe is a root of its own and its folded boxes lose the collapse arrow.
- Not ported: the recipe borders around a collapsed recipe's slots, sorting stand-in slots, Ctrl + hover showing the
  current amount of a collapsed root, ItemSorter order inside the row (sorted by item id).

## Stage 18: borders, todo drag between groups, tree extras

- Borders (NEI's `calculateGroupBorders`): a collapsed group is one border (grey, or green for a crafting chain), a
  collapsed recipe of a crafting chain group another (purple). Lines are 1px; NEI's are 0.75px.
- Shift + drag of a result or plain item in a todo list works across groups (NEI's `SortableItem` crossGroup): upper /
  lower quarter of a row insert into the group above / below. A collapsed recipe's stand-in slots drag the recipe with
  everything folded into it.
- Ctrl + hover on a collapsed recipe's root in the tree shows what that recipe alone asks for.
- Item order in a collapsed row is by registry id, which is the order of the item list here (NEI's ItemSorter default).
- Not ported: the tree's link button (a chat link of the group's NBT) and screenshot button (offscreen render of the
  tree to a PNG): both need game APIs that cannot be checked without compiling.

## Stage 19: collapsible items, craftables, zoom, quantity, presets, creator windows, wheel transfer, HUD

All new code is untested in the game; calls that could not be checked are marked `PORT-CHECK`.

- **Collapsible items** (`Collapsible`, `item/ItemGroups`): similar items fold into one slot of the item list, Alt + click
  opens / closes a group, the "G" button toggles all. Default groups are the 26.2 equivalents (spawn eggs, music discs,
  splash and lingering potions), written in this port's search syntax to `config/notenoughitems-collapsible.txt`.
- **Craftables panel** (`client/Craftables`, `recipe/CraftCheck`): what the inventory can craft, above the history panel.
  Off by default (option `craftablesPanel`). Counts per item id, recalculated every 600 ms on the client thread.
- **Item zoom** (`client/ItemZoom`, `item/ZoomMath`): the hovered item enlarged in the free side area, with its name and a
  hint for the toggle key. Shift + Z toggles, hold / zoom in / zoom out keys are unbound by default. Placement and zoom
  steps are NEI's formulas. "Only solid" means a block that occludes light.
- **Quantity field** (`client/QuantityBar`, `item/QuantityMath`): - / field / + under the item list. A left click on a list
  item gives that many (empty = a full stack), a right click gives one; Shift steps 10, Ctrl 64; also the Alt step of
  bookmark amounts. One value for all worlds (NEI keeps it per world). The field is a small own text box (digits,
  Backspace, Up / Down, wheel), no cursor movement.
- **Presets** (`Presets`, `item/Preset`, `item/PresetFile`, `item/RecipeRemoval`, `client/PresetsWindow`): Hide, Remove (also
  drops recipes whose ingredient slot only holds removed items or whose results are all removed), Subset (a "Presets"
  branch in the subset menu), Group (collapsible group). File: `config/notenoughitems-presets.txt` in NEI's format with
  registry ids. The editor is a window opened by the "P" button on the bottom bar: list (enable, edit, delete, Shift + click
  moves up), editor (name, mode, enabled, items, search, "F" box). Click adds, right click removes, Ctrl = whole mod,
  Shift = everything the search shows (NEI: drag selection and Shift = same item). No drag sorting.
- **Enchantment modifier / potion creator** (`client/CreatorWindow`, `item/GiveCommands`): keys X (over an item) and P.
  They give a new item with `/give`, so they need cheats or operator permission; NEI edits the item in a slot of its own
  screen. Levels go to 255. The command component syntax is in `GiveCommands` (the one place to fix if 26.2 differs).
- **Mouse wheel transfer** (`client/FastTransfer`, `item/SlotZones`): over a slot, wheel down moves one item the way
  Shift + click would, wheel up pulls one in from the other zones; option `mouseWheelTransfer` (+ invert). Not ported: Shift +
  click mass transfer onto an empty slot and Shift + drop throw-all (they need slot click hooks).
- **Highlight tooltips** (`client/HighlightTips`): a box with the item and name of the block you look at, numpad 0 toggles,
  position by the two options `highlightTipsX / Y` (NEI drags a sample box). Off by default.
- Settings that keys and the wheel change fast (zoom, quantity, HUD) are written to the config file half a second after
  the last change (`NEIConfig.changed / flush`).
- Still parked: tree link button, tree screenshot button, pull items from containers, giving items with cheats off.
- Settings screen (stage 19c): options are grouped into sub screens (Collapsible Items, Craftables Panel, Item Quantity
  Widget, Item Zoom Panel, Mouse Wheel Transfer, Highlight Tooltips), every option has a readable label and tooltip in
  `lang/en_us.json`, and colours are `#AARRGGBB` text instead of decimal numbers. The config file layout changed (options
  moved into sections, colours became text), so the moved options start from their defaults once.
- Settings window (stage 19d, `client/SettingsWindow`, `item/ColorMath`): the wrench button opens NEI-style settings over the
  inventory instead of NeoForge's screen (still reachable by the "NeoForge screen" button in the footer). Section buttons,
  option rows with the label left and the value right (Yes / No buttons, number fields, colour swatches), tooltips, Back
  button. A colour swatch opens a picker: saturation / value square, hue bar, alpha bar, old / new swatches and a hex field.
  The rows are generated from `NEIConfig.OPTIONS`, which `NEIConfig` fills for every option it defines.

## Stage 20: more NEI keys

All rebindable under Controls (GUI context), defaults as in NEI:
- `gui.hide` (O): hides every NEI panel until pressed again (`NEIClientEvents.hidden`; not saved between sessions).
- `gui.getprevioussearch` / `gui.getnextsearch` (Up / Down) in the search box: `item/TextHistory` (copy of NEI's). The text is
  remembered whenever the box gains or loses focus.
- `recipe.prev_machine` / `next_machine` (Up / Down), `recipe.prev_recipe` / `next_recipe` (Left / Right) in the recipe window
  (`RecipeOverlay.pageKey`; the arrow keys used to be fixed).
- `bookmark.favorite` (Shift + F) over an item panel / history entry: saves the tree of its favorite recipe as a bookmark group.
- `bookmark.favorite_item` (Shift + F) over a recipe ingredient that has alternatives: favorites that item (`Favorites` entry
  `favorite_item:<id>`, no recipe; recipe trees prefer it).
- `bookmark.remove_recipe` (Shift + A) over a group's strip: removes the whole group (`Bookmarks.removeGroup`).
- `gui.overlay` (Shift + S) over an item: fills the crafting grid with its recipe (the bookmark slot's recipe, else the favorite
  recipe) through `RecipeTransfer`. Not ported: the ghost-item overlay without Shift, the multiplier with Ctrl.
- `bookmark.hide` is the existing B key. Not ported: `gui.craft_items` (autocrafting), `bookmark.pull_items`, `gui.next_tooltip`.

## Stage 21: JEI recipe bridge (backup source: JEI installed)

Plan for modded recipes (user's decision): primary = host JEI plugins ourselves with JEI's own Library (stage 22, separate
optional jar because the real JEI contains the same classes); backup = read an installed JEI (this stage).
- `recipe/RecipeSources`: plain-Java registry of extra recipe providers; `RecipeIndex.build()` merges their views.
  Vanilla displays that only have a result (modded machines `RecipeConverter` cannot read) are dropped while a source is active.
- `jei/NEIJeiPlugin` (`@JeiPlugin`, only JEI loads it): on `onRuntimeAvailable` registers a source that reads every JEI category
  (except the types NEI already reads: crafting, smelting, blasting, smoking, campfire, stonecutting, smithing, fuel, info) and
  every recipe through `IRecipeManager.createRecipeLayoutDrawable(...).getRecipeSlotsView()`, built lazily on first lookup.
- Stage 21b: each JEI recipe draws itself with JEI's own layout (`RecipeView.Drawn`, `JeiDrawn`): frame, arrow, fluids, energy text as in
  JEI. `RecipeOverlay.cellH()` sizes the cells to the tallest layout of the type; NEI's heart / "+" sit at the right edge. The view's
  slots (INPUT / OUTPUT item slots only; the machine icon is a station slot and is skipped) serve lookups, uses, bookmarks, search.
  JEI's own GUI is switched off (`configureJei` -> `disableJeiGui`) so its panels do not double NEI's. Station = first crafting
  station of the category. Layouts wider than 176 are not scaled.
- Build: `jei_version` in gradle.properties (`30.+`, pin once it resolves), compileOnly JEI API, localRuntime full JEI,
  optional `jei` dependency in mods.toml. Maven coordinates are from JEI's own build files (`mezz.jei:jei-26.2-neoforge-api`...).
- Not done: category drawing (arrows, tanks), fluids as real slots, JEI recipe transfer, hidden-recipe sync, a reload refresh.
- Stage 21c: `RecipeView.stations` (all crafting stations of the JEI category): the station column lists them, and the "uses" of a
  machine are the recipes it can make (`RecipeIndex` adds each view under its stations). Tab tooltips show the mod's display name
  (`ItemTooltip.modName`, e.g. NTM, not the namespace). Hovering a fluid / non-item slot of a drawn recipe shows the slot's tooltip
  (`Drawn.tooltip`, JEI's legacy `IRecipeSlotDrawable.getTooltip()`) through NEI's own tooltip.
- Stage 21d: fluids in the item panel, as in NEI's `ItemFluidDisplay` (`item/FluidDisplay`). Every source fluid gets its own
  registered item `notenoughitems:fluid/<mod>/<fluid>` (registered in the item `RegisterEvent`, after fluids; exists on server
  and client, never obtainable: `NEIActions.give` and the cheat click refuse it). All stack drawing goes through `Draw.stack`,
  which draws a fluid item as its tinted still sprite (code copied from JEI 26.2's FluidHelper / FluidTankRenderer). Owner mod =
  the fluid's (`FluidDisplay.ownerNamespace`: panel mod filter, subsets, tooltip). JEI fluid slots become display stacks in the
  view's slots, so recipes that make / use a fluid are found by clicking it. Fluid slot tooltips end with the mod name.
  Not done: a "Fluids" subset like NEI's creative tab (fluids are under their mods' subsets), amounts on display items,
  gases and other non-fluid ingredient types.
- Stage 21e: `GuiGraphicsExtractor#blitTiledSprite` is private in 26.2; `META-INF/accesstransformer.cfg` makes it public (descriptor from
  JEI's own AT). If the build ignores the file, uncomment `accessTransformers = ...` in build.gradle's `neoForge` block.
- Stage 21f: startup crash fix. `Fluid#getFluidType()` throws while the item registry is being filled (the `neoforge:fluid_type`
  registry is not bound yet), which also made AE2 and Storage Drawers fail in the same registration pass. Fluid display items are now
  registered without a name; `FluidDisplay.named()` sets the fluid's name as a (non-italic) custom name on the stacks, in
  `stackOf` and in `ItemList.load`.
- Stage 21g: R / U on a fluid found nothing. Added: flowing fluids map to their source's display item, and the log now says
  "NEI JEI bridge: N fluid ingredients became display items; not converted: {...}" and "fluids with recipes: X, with uses: Y".
  Cause not confirmed yet; those two lines in latest.log decide.

## Stage 22: release prep

- Server safety: the fluid display items are registered on the client only (`client/FluidRegistration`, `Dist.CLIENT`). A
  server that registered them sent them in its item registry and clients without the mod were refused ("The server sent
  registries with unknown keys"). Drawing moved to `client/FluidDraw`. `NotEnoughItems`, `RecipeSync` and `FluidDisplay`
  are the only classes that load on a server; none of them names a client class. Untested: a client with the mod has item
  entries the server lacks (should be fine in NeoForge, check by joining servers with and without the mod).
- All five GUI textures are byte-identical to the GTNH NotEnoughItems repository (checked by hash), so they fall under its
  LGPL license. `lang/en_us.json` and `logo.png` are original.
- New: `logo.png` (original icon), mods.toml description / credits / `iconFile` (`logoFile` is deprecated in NeoForge 26.2), README with credits, CI uploads the jar,
  `RELEASE.md` (store texts, changelog, publishing checklist).
- Modrinth already has a project "Not Enough Items" (slug `nei`, the original Forge NEI for old versions): publish under a
  different name and slug.

## Stage 23: NEI's mod API

- `api/API.java` is NEI's `API` for what this rewrite has: `registerRecipeSource` (NEI's `registerRecipeHandler` /
  `registerUsageHandler` in one: a recipe is found by its inputs and outputs), `hideItem(ItemStack | search rule)`,
  `setOverrideName`, `setAliases` (matched by the search field), `addItemListEntry` / `addItemVariant`, `addSubset(dotted
  name, items | ItemFilter)`. Not carried over: GUI handlers and overlays, catalysts, key binds, options, sort options,
  stringify handlers, fluid renderers, mode handlers.
- Differences from NEI: `addItemListEntry` adds a stack instead of replacing the item's default entries; name overrides
  apply to the item panel's stacks only.
- Discovery: `api/NEIRegisterEvent` (mod bus, client, fired from `FMLClientSetupEvent`) replaces `IConfigureNEI.loadConfig`.
- `RecipeSources.set(id, source, complete)`: only a source that covers every modded machine (the JEI bridge) drops vanilla
  result-only displays; a mod's own source no longer hides the other mods' entries.
- Not compiled; `ModLoader.postEvent` and the `IEventBus` constructor parameter are PORT-CHECK.

## Stage 24: keys, item sorting, panel options

- Keys (NEI `LayoutManager.tickKeyStates`, `SearchField.lastKeyTyped`): `world.dawn / noon / dusk / midnight / rain / heal /
  creative` (unbound by default, as in NEI; work only with no screen open and need the cheat setting and operator commands)
  run the toolbar's actions (`Toolbar` now has them as methods); `gui.search` (F, no modifiers) focuses the search field
  while the panel shows. `bookmark.add` was already the BOOKMARK key (A).
- Item sorting (NEI `ItemSorter`, `GuiItemSorter`): keys `minecraft, mod, id, default, name` in a saved order
  (`itemSort` option, `item/SortOrder`), each deciding when the ones before it tie. The list is now sorted like NEI's
  default (vanilla first, then mod, then registry id) instead of by mod name and id text. "Item Panel Sorting" in the
  settings window: drag a key to move it, the list is sorted again at once (`ItemList.resort`). Not carried over:
  `nei.itemsort.damage` (no damage values), `API.addSortOption`.
- Options (NEI `inventory.history.*`, `inventory.subsets.enabled`): history panel on / off, line colour, rows (1 - 5);
  subsets dropdown on / off. Not ported: history persistence and world specific history, `subsets.widgetPosition`.

## Stage 25: recipe search

- NEI's recipe filter grammar (`recipe/RecipeQuery`, pure, tested): a marker starts a clause - `<` ingredients, `>` results,
  `^` other things (the stations); plain text before the first marker looks everywhere; `&` right after a marker = every
  item must match, otherwise one is enough. Clauses are ANDed; each clause's text is the normal item search. Markers in
  quotes, between slashes or after a backslash are plain text. Items hidden through `API.hideItem` do not count.
- Text turns red when no recipe of the type matches (NEI's colours), the magnifier tooltip lists the markers.
- Up / Down in the field walk a recipe search history (separate from the item panel's, kept for the session); text is
  added when the field loses focus.
- Not ported: NEI's expression-mode and `RecipeSearchField` suggestions.

## Stage 26: recipe search tooltip

- Magnifier tooltip tidied: white title, one short line per marker (marker gold, text gray), "Hold ALT for hotkeys"; Alt shows history / clear / leave keys.

## Stage 27: history saving

- NEI `inventory.history.save` (off by default): the history panel's items are written to `config/notenoughitems-history.txt`
  (registry ids, newest first) on every lookup and read back when the item list first loads (`HistoryFile`). Items that no
  longer exist are dropped. Not ported: `inventory.history.worldSpecific` (one global list), the recipe/item search text history (done in stage 38 for the presets window; the item and recipe histories came in stages 20/26).
- Also in this zip: the recipe search tooltip shows its hotkeys while Alt is held (stage 26 follow-up).

## Stage 28: small features

- `inventory.subsets.widgetPosition` ("Widget on top" in the Subsets options): off puts a square `%` button at the left end of
  the search field (the field gets shorter) and the menus open upwards; works with no room above the window.
  `SubsetMenu.render(..., top, x, y, size)`.
- `inventory.history.worldSpecific` (default on): the saved history is one file per world / server
  (`config/notenoughitems-history-<world|server>.txt`, `HistoryFile.worldKey`); the list reloads when the world key changes (checked each time the inventory opens or a lookup is added).
  PORT-CHECK: `IntegratedServer#getWorldData().getLevelName()`, `ServerData#ip`. Bookmarks stay global.
- "Fluids" subset (fluid display items of the JEI bridge) through `API.addSubset`.
- Skipped: `gui.next_tooltip` (NEI pages tall 1.7.10 tooltips; 26.2 tooltips do not need it). JEI fluid amounts and wide
  layouts were already handled by the recipes drawing themselves.

## Stage 29: pull items from containers

- NEI's `bookmark.pull_items` (V): over a bookmark group's strip it takes the group's items out of the open container, over
  an item it takes that item (a bookmark's amount, else the quantity field's amount); Shift = only what the inventory lacks.
  The "P" button right of the namespace arrows (shown over a container screen) pulls the whole default group.
- `bookmark/PullMath` (pure, tested) is NEI's `pullBookmarkItems` maths: without recipes every item in full; with recipes
  the chain is worked out again with inventory (Shift) and container items as plain items, and what the chain uses up of
  the container's is pulled. `client/ContainerPull` does the free-slot check, the clicks and the held-item handling.
- Containers: `ContainerScreen` (chests, barrels, ender chests) and `ShulkerBoxScreen`; mods register more through
  `API.registerBookmarkContainerHandler(screenClass, IBookmarkContainerHandler)` (exact class, as in NEI).
- Difference: a whole stack is moved with one Shift + click instead of one item at a time (same result, fewer packets).
- PORT-CHECK: `ContainerScreen` / `ShulkerBoxScreen` names, `ContainerInput.QUICK_MOVE`.

### Stage 29b: recipe "+" follow-ups

- The "+" closes the recipe window once the recipe is in the grid (as NEI); it stays open when ingredients are missing or
  an error is shown, so the red highlights and the message can be read.
- Shift + left click on the "+" places as many sets as possible (NEI's `maxTransfer`): sets are planned one after another
  while every ingredient is still in the inventory and no cell would hold more than a stack; a cell keeps the item its first
  set used. The clicks per source stack and cell are grouped (pick up, right-click n times or one drop for a whole stack,
  put the rest back).

## Stage 30: crafting tree link and screenshot buttons

- Chat links (NEI `bookmark.chat_link`, tree toolbar link button): `BookmarkPayload` (pure, tested) turns a group, a recipe
  or an item into text and adds it to a bookmark page again. Ctrl + L over a group's strip, a bookmark or a panel item, or
  the tree's link button, sends it to the server (`net/LinkToServer`); the server (needs the mod, as NEI needs its server
  mod) hands the data to every client with the mod (`LinkToClient`) and announces "<name> Click to add bookmark: [..]"
  in chat. The click runs the client command `/nei_bookmark <id>`. Payloads are optional, so clients / servers without the
  mod still connect. Differences: the recipe window's Ctrl + Shift + L over the "+" is not ported.
- Tree screenshot ("Save tree as image", `client/TreeShot`): the tree is drawn alone, one screenful at a time at zoom 1,
  each read back from the finished frame (`RenderFrameEvent.Post`) and stitched into `screenshots/crafting_tree_<date>.png`.
  Differences from NEI (which renders off-screen at 2x): sharpness follows the GUI scale, the stats panel is not in the
  picture, and the picture is limited to 8000 pixels a side.
- PORT-CHECK: `PayloadRegistrar#optional/playToServer/playToClient`, `hasChannel`, `ClickEvent.RunCommand/OpenFile`,
  `HoverEvent.ShowText`, `RegisterClientCommandsEvent#getDispatcher`, `Screenshot.takeScreenshot(RenderTarget, Consumer)`,
  `NativeImage#getPixel` (ARGB), `Window#getGuiScale`.

### Stage 30c
- "Save tree as image" crashed: `Minecraft#getMainRenderTarget` is gone in 26.2 and the reflective lookup found nothing, so `Screenshot.takeScreenshot` got null. The lookup now also scans private members of `Minecraft` and `GameRenderer`, logs every render-target-like member (`TreeShot:` lines in latest.log), and cancels with a chat message instead of crashing when nothing is found.

## Stage 31: creative+ (extended creative inventory)
- NEI's "save states" no longer exist in the GTNH fork (only a leftover `save-state` permission name and `getInventoryAreas` javadoc), so nothing was ported for them.
- Creative+ as in NEI: the gamemode button cycles survival -> creative -> creative+ (the last only when the server has the mod); in creative+ the inventory key opens the "Extended Creative" inventory instead of the creative screen; Ctrl + scroll in the world cycles the 10 rows (6 extra, 3 inventory, hotbar); clicking an item in the list puts it into slots 0-53 (cheat mode).
- Server side (`net/PlusInventory`): a per-player 54-slot `SimpleContainer`, saved to `<world>/nei/creative_plus_<uuid>.json` on every change, opened as a vanilla 6-row `ChestMenu` titled "Extended Creative" (so a client without the mod can open it too). Packets (`net/PlusPackets`, optional): `Open`, `Slot`, `Cycle`; all need the player in creative mode. `net/PlusRows` is the pure row ring (tested).
- Client side (`client/CreativePlus`): intercepts `CreativeModeInventoryScreen` opening, finds the screen by its title, tracks Ctrl for the in-world scroll.
- Not ported: the armor slots left of the screen (vanilla chest layout has none), NEI's background texture, closing the screen when leaving creative+.
- Stage 31b: compile fixes. The static helper `type(String)` in `PlusPackets` was shadowed by each record's `type()` (renamed `typeOf`); `SimpleContainer#addListener` is gone in 26.2, so the store is a `SimpleContainer` subclass whose `setChanged()` triggers the save.

## Stage 32: autocrafting
- As in NEI (`AutoCraftingManager`, `gui.craft_items`): with a crafting table or the inventory crafting grid open, Shift + C over a bookmarked recipe, a crafting chain group's strip, or a recipe of the recipe window crafts it; Shift + Ctrl + C only makes what the inventory lacks. A recipe inside a crafting group crafts what it needs from the group. Option "Autocrafting" (default on).
- `bookmark/AutoCraftPlan` (pure, tested): chain maths with the inventory as stock -> steps `(recipe, runs)` bottom up. `ChainMath` got `runs`, `craftOrder`, `dependencies`. Craft items = the wanted items are made in full; missing only = the inventory's wanted items count.
- `client/AutoCraft`: tick-driven executor. Per step: `RecipeTransfer.transfer` (up to 64 sets), wait until the result slot shows the recipe's result (2 ticks stable), Shift + click it, wait until the inventory holds the new items (the client only guesses one craft), clear the grid. Stops on another screen, missing ingredients, no result, full inventory; says why in chat. Click sound at the end.
- Needs no server mod (plain container clicks), so NEI's server permission "autocraft" does not exist here.

## Stage 33: smaller leftovers
- World specific bookmarks and favorites (NEI `inventory.bookmarks.worldSpecific`, `inventory.favorites.worldSpecific`, both off by default): one file per world / server (`notenoughitems-bookmarks-<world>.txt`), a new world starts from the global file. They reload when the world or the option changes (every change is saved at once).
- Ctrl + Shift + chat link key over a recipe of the recipe window (or an item with a favorite recipe in the panel) sends the whole recipe; Ctrl + key over an item of the recipe window sends the item. Listed in the Alt help.
- The tree image now has the stats panel under the tree (one extra capture of the panel's content area).
- The extended creative inventory closes when creative+ ends. The armor slots and NEI's background texture are still not ported: the screen is a vanilla chest menu (so a client without the mod can open it), and extra slots would need our own menu type.
- Not done on purpose: giving items with cheats off (parked by the user), pulling from nearby chests (not a NEI feature).
- Stage 33b: importing a shared recipe added only its result, because the "recipe already bookmarked" check ran after the result went in and skipped the ingredients (`BookmarkPayload.addTo` now asks before adding; tested). A world specific bookmarks / favorites file is now written as a copy of the global one when first opened (as NEI copies it), so bookmarks made before turning the setting on are in every world at first and diverge per world from then on.

## Stage 34: mob spawn overlay
- As in NEI (`WorldOverlayRenderer`, key `world.moboverlay`, F7): toggles the overlay with the chat message "Mob Spawning Overlay: Off/On". In the 33 x 33 x 33 blocks around the player an X is drawn on every block where a monster could spawn: red = at any time of day (sky light below 8), yellow = only at night. Cells are skipped when the block light is over the dimension's monster limit, the biome has no monster spawns, a zombie's spawn placement is not ok there, something blocks the 0.6 x 1.8 box or there is liquid. Snow layers lift the X onto the snow (y offset 0.02 otherwise). The overlay switches off when the world is left.
- `client/MobOverlay`: cells rebuilt every 10 ticks, the lines handed to `Gizmos.line` every client tick. Chunk boundary overlay is not ported (vanilla F3+G).
- Key `NEIKeys.WORLD_MOBS` (default F7, "Mob Spawn Overlay"). Untested against the game: `Gizmos` API, `monsterSpawnBlockLightLimit`, `SpawnPlacementType#isSpawnPositionOk` are marked `PORT-CHECK`.
- Stage 34b: 26.2 has no `EntityType.ZOMBIE`; the overlay now looks the zombie up in `BuiltInRegistries.ENTITY_TYPE` by id.
- Stage 34c: nothing was drawn, because the client tick event fires after vanilla closed its per-tick gizmo scope (gizmos outside a scope are dropped). The overlay now opens `Minecraft#collectPerTickGizmos()` itself and gives each line `persistForMillis(80)`.
- Stage 34d: temporary debug chat line (columns / rejects / spots) and the failure is now shown in chat; remove once the overlay shows.
- Stage 34e: the first build never ran: `builtAt = Long.MIN_VALUE` made `time - builtAt` overflow negative. Now starts at -1000.
- Stage 34f: the overlay shows (checked in game). Removed the debug line. New option "Mob spawn overlay range" (8-32 blocks, default 16 as in NEI) for the horizontal reach; the vertical reach stays 16. The failure message stays.

## Stage 35: mob spawners, delete mode, magnet
- As in NEI (`ItemMobSpawner`, subset `Blocks.MobSpawners`): a spawner item for every mob (living mobs; the ender dragon and giant are left out, as in NEI), in the new subset Blocks > Mob Spawners. `item/MobSpawners` builds each stack through `ItemStack.CODEC` from `{id: spawner, components: {block_entity_data: {id: mob_spawner, SpawnData, SpawnPotentials}}}`, so placing it makes that mob's spawner and the vanilla tooltip shows the mob's name (searchable). Not ported: the spinning mob inside the spawner icon (`SpawnerRenderer`), the red / aqua name colour of the tooltip.
- `/give` now carries the item's components (`NEIActions.components`): a variant (potion, enchanted book, spawner ...) is given as that variant, not as the plain item. A name set through `API.setName` is left out.
- As in NEI (delete button, `NEIController` delete mode): button = delete the cursor stack; Shift = delete every slot with that item; right click = one item less; with nothing on the cursor: Shift = delete everything in the open screen, plain = delete mode on / off (white outline). In delete mode a click on a slot deletes its stack (Shift + left: all of that item, right: one item). Not in the vanilla creative inventory (as in NEI). `client/Deleting`, server `net/CheatServer`.
- As in NEI (magnet button, `updateMagneticPlayer`): toolbar toggle after the gamemode button; dropped items within 16 x 8 x 16 that fit the inventory drift to the player (NEI's speeds). The server does the moving (`ServerTickEvent`), the client's setting `magnet` is sent again on every join. NEI also moves the items on the client; here the server pushes the motion at once instead.
- Needs NEI Rewrite on the server, as NEI needs its server mod: three optional payloads (`CheatPackets`: SetSlot, DeleteAll, Magnet). The server accepts them from creative players and operators, and SetSlot only for removing items. Without the mod on the server only deleting the cursor stack works, the rest says so in chat.
- Untested against the game: marked `PORT-CHECK` (`Slot#set`, `ItemEntity#hasPickUpDelay / playerTouch`, `Entity#hurtMarked`, `Inventory#getFreeSlot / getSlotWithRemainingSpace`, `ItemStack#getComponentsPatch`, `DataResult#result`).
- Stage 35b: the item list's tooltip drops vanilla's "might lead to command execution" warning (and the empty line above it); NEI's list has none.

## Stage 36: JEI bridge leftovers
- The stage 21 line "Not done: category drawing, fluids as real slots, ..." was out of date: category drawing is JEI's own layout since 21b, fluids are display items since 21d, and a JEI restart (a reload) already rebuilds the index (`RecipeSources.set` invalidates it). JEI recipes are read lazily on first lookup, after every plugin ran, so recipes a plugin hides while JEI starts are already left out. Recipes hidden later are not followed (JEI has no event for that).
- JEI recipe transfer: a "+" under the heart of a JEI recipe in the recipe window, shown when a mod registered a JEI transfer handler for the open menu and category (`IRecipeTransferManager#getRecipeTransferHandler`). Green when it can place the recipe now, grey when it cannot (checked a few times a second, like JEI's own button). Click = one set, Shift + click = as many as possible; the window closes on success, as with NEI's crafting "+". `RecipeView.Drawn.transferState / transfer`, implemented in `NEIJeiPlugin.JeiDrawn` with the handler's six argument `transferRecipe` (checked first with `doTransfer = false`, "cosmetic" errors still allow it). JEI's own packets do the moving, so the mod's JEI part must be on the server too.
- Spawner warning (35b followed up): the vanilla "Warning: ... command execution" lines are now dropped by translation key and, if the key differs, by look (bold red header + red lines); the first spawner tooltip's lines are logged once (`Spawner tooltip line: ...`) to see what they are if they still show.

## Stage 37: progress bars, spawner polish, search, ghost items
- As in NEI (`FurnaceRecipeHandler.drawExtras`): furnace type recipes (the ones with a time line) animate. The arrow fills from left to right and the flame burns down over NEI's 48 tick cycle (`TemplateRecipeHandler.cycleticks % 48`, here the clock in 50 ms ticks). The arrow is drawn in code (`Draw.progressArrow`), the flame is Minecraft's own furnace flame sprite (`container/furnace/lit_progress`, stage 37b; the burnt part is veiled in the window's grey); the flame shows only when the recipe has a fuel slot (not for campfires). The crafting arrow stays still, as in NEI.
- As in NEI (`ItemMobSpawner.addInformation`): the mob's name in a spawner's tooltip is dark red for monsters, dark aqua for other mobs. Difference: NEI draws the mob turning inside the spawner icon; here the mob's spawn egg is drawn small in the middle of the icon (`client/SpawnerLook`, in `Draw.stack`, so only where NEI draws stacks). A live entity inside an icon needs the entity render pipeline, which could not be checked. Mobs without a `<mob>_spawn_egg` item get no badge. The mob is found from the tooltip's name line.
- As in NEI (`SubsetWidget` `%` prefix, `inventory.search.subsetsSearchMode` = Prefix): `%blocks.mobspawners` finds the items of the subsets whose path (lower case, no spaces) matches; Shift + click on a subset in the subsets bar puts `%<path>` in the search field. The mode option is not ported (always the prefix mode, NEI's default). Parsed searches are forgotten when the item list reloads.
- As in NEI (`OverlayHandler.renderObjects`): while the mouse is on a crafting recipe's "+", its ingredients show faded as ghost items over the grid's slots (`RecipeTransfer.drawGhosts`).
- (Stage 38 ported everything this line listed as not ported, see below.)
- Stage 37b: the flame is the vanilla furnace flame sprite instead of my drawing.

## Stage 38: the "not ported" leftovers
All code is parse-checked only; nothing was compiled or run in game yet.
1. As in NEI (`FastTransferManager.throwAll`): Shift + drop key over a slot throws every item of that stack's kind out of the open container (`FastTransfer.shiftDrop`). Difference: needs an empty cursor (NEI clicks anyway). Shift + click outside the window with a stack on the cursor throws all of what was picked up from that slot.
2. As in NEI (`FastTransferManager.massTransfer`): Shift + click on an empty slot with a stack on the cursor puts the stack there and fills the zone from the slot it came from (`FastTransfer.shiftClick`). Hooks: `NEIClientEvents.onMousePressed` and `onMousePressedPost`. Spreading-drag detection is not checked.
3. As in NEI (`gui.overlay`): Shift + overlay key now transfers as many sets as fit (up to 64); with Ctrl it uses the bookmark's multiplier (or amount / factor), else 1. The ghost overlay without Shift is still not ported.
4. As in NEI (`TextHistory`): Up / Down in the presets window's search keep a history; added on focus loss (`PresetsWindow.focusSearch`).
5. As in NEI (`SubsetWidget` item rows): inside a subset, columns of single items. Click = show recipes, right click = hide the item (dark), Ctrl + click gives it (cheats on), Shift + click searches it (Prefix mode). Hiding is per registry id (`Subsets.setHidden`, `Tag.own`). Hotkey help: `HotkeyHelp.Where.SUBSET`.
6. As in NEI (`SearchTokenParser`, `SearchTextFormatter`): `search/SearchParser` rewritten with NEI's pattern modes (Plain / Extended / Regex / Extended+), space modes (AND / Space / AND-X) and the expression grammar (hand-written parser, `& | ! ( )`), and the search field is coloured by provider (`client/SearchHighlight`, `EditBox#addFormatter`). NEI has no separate "suggestions" feature for the item search, only the colours. An invalid regex falls back to plain text in the classic modes and matches nothing in expression mode. Standalone-tested with stubs (not the game).
7. As in NEI (`BookmarksGridSlot`): holding Ctrl or Shift over a bookmark cell shows the REAL / SHIFT / CALCULATED amount and the ingredient / result / remainder colours (`BookmarkPanel.contextAt / shownOf`). The stage 18 notes already covered insert-between-groups, stand-in slots and borders.
8. Villager trade tab: not in NEI core but in the GTNH addon. `client/VillagerTrades`: while a merchant screen is open its offers are learned as recipes ("Villager Trade (<profession>)", emerald station, 2 inputs, 1 output) and kept for the session. Difference: trades are not listed up front.
9. As in NEI (`inventory.search.subsetsSearchMode`): the `%` / Shift + click behaviour follows the option (`SubsetMenu.searchByClick`).

New "Search Settings" section in the settings window: pattern mode, space mode, per-provider modes (mod `@`, tooltip `#`, id `&`, tag `$`, subset `%`: Always / Prefix / Never) and quote drop. The old "search regex" option is gone.
- Defaults differ from NEI: all provider modes are Prefix (NEI: tooltip / id / tag Always) and the pattern mode is Regex (NEI: Extended), so the behaviour you confirmed stays the same until you change them. Prefix redefinitions are not ported.
- Unverified calls (`PORT-CHECK`): `EditBox#addFormatter`, `Options#keyDrop`, `MerchantOffer#getBaseCostA / getCostB / getResult`, `MerchantMenu#getOffers`, `MerchantScreen#getTitle`, `ScreenEvent.MouseButtonPressed.Post`, `AbstractContainerScreen#getGuiLeft / getXSize`.
- Still not ported: giving items with cheats off, pulling from chests, the chunk boundary overlay, creative+ armor slots and background, JEI transfer polish, item sorter order inside a collapsed row.

## Stage 38b: NEI's recipe button icons
- As in NEI (`GuiFavoriteButton`, `GuiOverlayButton`): the heart and "+" buttons are 12 x 12 and draw NEI's own 9 x 10 sprites from `nei_sprites.png`: heart off (10, 76) / on (19, 76); "+" plain (46, 76), with Shift held the fill arrow (28, 76), or the red fill arrow (37, 76) when the recipe does not fit; a recipe that does not fit is drawn at half strength. Was: text characters. `RecipeOverlay.heartIcon / plusIcon`.

## Stage 38c: "+" in modded crafting screens (AE2 terminal ...)
- Cause: NEI's own crafting recipes (vanilla crafting, `NEI_COVERS`) were placed by `RecipeTransfer`, which only knows the vanilla crafting table and the 2 x 2 inventory grid; JEI's transfer handlers were only used for recipes drawn from JEI. In an AE2 crafting terminal the "+" was grey and did nothing.
- Fix: `recipe/CraftingBridge` (set by `NEIJeiPlugin.Bridge` while JEI runs). For a crafting view in a screen without a vanilla grid, the bridge finds JEI's `RecipeTypes.CRAFTING` recipe with the same result (focus on the output) and the same input items per slot (sorted item ids, tags over 100 items count as "BIG"), then reuses `JeiDrawn.transferState / transfer` (the screen's JEI handler, checked first with `doTransfer = false`). Grey "+" = no handler, no match, or the handler says it cannot; click = one set, Shift + click = as many as possible. Needs JEI installed (as before for the JEI recipes), and the mod's JEI part on the server.
- Untested: the matching, and that AE2's handler accepts a recipe layout built this way (AE2 reads the ingredient lists of the layout's slots). If it still does nothing the log line `NEI could not match a crafting recipe to JEI` or `JEI recipe transfer failed` says why.

## Stage 39: fixes and mod API
- As in NEI (`BookmarkGrid#drawGroup`): the strip left of a bookmark group is `GROUP_CHAIN_COLOR` (green, 0x6645DA75) for a crafting chain group and grey (0xFF666666) for other groups. It was always grey.
- The hotkey help box (Alt over an item) had no left / right outline; it now matches the item tooltip (box and outline).
- As in NEI (`GuiOverlayButton.drawItemOverlay`, `NEIClientUtils.presenceOverlay`): while the mouse is on a crafting recipe's "+", its ingredients show whether you have them. Each ingredient takes one item out of a copy of the screen's slots (slots you cannot take from are left out). Options (new "Recipe Window" section): item presence overlay Off / Icon (NEI's tick / cross sprites from `nei_tabbed_sprites`, bottom right of the slot) / Highlight (red = missing 0x80AA0000, green = present 0x8000AA00), and "Highlight present items". NEI's default is Icon; here the default is Highlight, as asked. Only in screens with a vanilla crafting grid (an AE2 terminal's items are not in its slots, so "missing" would be wrong); not for JEI-drawn recipes. NEI's tooltip listing the missing items on the "+" is not ported.
- As in NEI (`addRecipeCatalyst`): `API.addCatalyst(recipeTitle, machine)` adds a machine to the station column of every recipe type with that title and to the machine's "uses".
- As in NEI (`IOverlayHandler`): `API.registerRecipeTransferHandler(menuClass, RecipeTransferHandler)` makes the recipe window's "+" place recipes into a mod's own screen without JEI (`api/RecipeTransferHandler`, kept in `recipe/CraftingBridge`). A custom handler wins over the vanilla grid and JEI.
- No separate API jar: the API classes use NEI's own types (`RecipeView`, `ItemList` ...), so modders compile against the mod jar (README says so).
- Untested: all of it (parse-checked only).
- Stage 39b: the stage 37 ghost items on the "+" are now an option (Recipe Window > "Ghost items on the + button", off by default). The recipe window covers the open screen's crafting grid, so the ghosts floated over the second recipe.

## Stage 39c: AE2 terminal search sync
- Like AE2's "sync with JEI", for NEI (`client/TerminalSearchSync`, option Search Settings > "Sync with AE2 terminals", on by default): while a screen of `appeng.client.gui.me.*` is open, its search field and NEI's item search keep the same text, whichever you type in (a client tick compares both texts with the last synced ones). AE2's own sync talks to JEI's search; NEI replaces that GUI, so the terminal's first `EditBox` is read from `Screen#children()` instead. No AE2 classes are referenced. On opening, text AE2 remembered wins if it has any. Needs NEI's search field on that screen (the NEI panel shown). Untested: AE2's search field must be one of the screen's `children()`, and the first `EditBox` must be the search field (a pattern terminal has others).
- The "+" in an AE2 terminal needs JEI (AE2's handler is its JEI integration). Without JEI the message now says so. Sending AE2's own fill-grid packet from NEI needs AE2's classes (not available here); asked for the AE2 jar to read them.

## Stage 40: clean-up
- All 105 `PORT-CHECK` markers are gone. They flagged calls whose names were guessed; every one of them has compiled since and the features were checked in game. A few markers also carried a real reason (the save folder name, raw types for the wildcard recipe map, the drained keys in the toolbar ...); those stay as plain comments. The "not checked against 26.2" sentences in class docs were removed too.
- Reflection that is no longer needed was replaced by the direct call, taken from JEI's 26.2 source: `Minecraft.getInstance().gui.setScreen(screen)` (config screen) and `player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)` (`CheatServer.allowed`, server side, creative players and operators as before).
- Reflection that stays: `client/Perms` (the client player's permission level: `hasPermissions(int)` or `permissions()`, whichever exists; unknown means yes) and `client/TreeShot` (finds the window's render target by type). Both work; replacing them needs the real names (the log line `NEI reads command permission with ...` is now at debug level).
- Log: removed the one-time "Spawner tooltip line" diagnostics; the TreeShot field / method listing and the permission line are debug level now. What is still logged at info: the recipe count from the server, the index size, the JEI bridge counts, loading the item history.
- Server safety (read, not run): the only classes registered on both sides are in `net` (`NEINetwork`, `CheatServer`, `PlusInventory`) and `RecipeSync`; none imports a client class. Every other event subscriber is `Dist.CLIENT`. The new `api/RecipeTransferHandler`, `recipe/CraftingBridge` and `TerminalSearchSync` follow that. A dedicated server start was NOT tested.
- Still open `shortcut:` notes (13, kept as the known debt): see `grep -rn "shortcut:" src`.

## Stage 41: release prep
- Mod icon is the user's own drawing (`src/main/resources/logo.png`, 512 x 512). Version is 1.0.0 (`gradle.properties`, shown in the mods list, jar `notenoughitems-1.0.0.jar`).
- The mod description in `neoforge.mods.toml` starts with the JEI warning (modded machine recipes need JEI).
- New / rewritten: `README.md` (warning on top, feature list), `CHANGELOG.md`, `RELEASE.md` (checklist), `store/modrinth.md`, `store/curseforge.html`, `store/summary.txt` (one line, 122 characters). All store texts open with the JEI warning. No source link is in them: the repository URL is not known yet (also missing: `issueTrackerURL` / `displayURL` in `neoforge.mods.toml`).
- Release label suggested: Beta (a dedicated server start was never tested here).


## Credits text (stage: CurseForge credit fix)
CurseForge asked for a credit and a link to the original content in the description, and for how the project differs. `store/curseforge.html`, `store/modrinth.md`, `README.md` and the mods.toml credits now have a "Credits & original work" section (links to Chicken-Bones/NEI, GTNH NEI, JEI) and a "How NEI Rewrite differs" list.


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


## Stage 43: 26.2 is now a multiloader project (NeoForge + Fabric)
- Same layout as the 1.21.1 multiloader: `common/` (all mod code, no loader API), `neoforge/`, `fabric/`; each loader module compiles `common/` with its glue.
- Seams in `common/`: `Platform` (configDir, modName, isModLoaded, fluidName, sendToServer, serverHas, sendToPlayer, canSend), `ClientPlatform` (category, newKey, fluidColor), `ClientHooks` (the one door for the loaders), `ScreenEv` (screen event with Minecraft's own `MouseButtonEvent` / `KeyEvent` / `CharacterEvent`), own `Cfg` JSON config (`config/notenoughitems-client.json`; NeoForge's `ModConfigSpec` is gone, so the old NeoForge config file is not read any more and the NeoForge config screen button is gone), `NEINetwork.TO_SERVER / TO_CLIENT`, `ClientRecipes.set(RecipeMap)` / `clear()`, `FluidRegistration.create(sink)`, `NEIPlugin` (Fabric entrypoint).
- NeoForge glue (`neoforge/`): `NeoForgeEntry`, `NeoForgeClient` (all former `@SubscribeEvent` handlers are listeners that call `ClientHooks` / the static methods), `NeoPlatform`, `NeoClientPlatform` (fluid tint via `FluidTintSource`), `NeoClientNet` (`ClientPacketDistributor`), `api/NEIRegisterEvent`, and `jei/NEIJeiPlugin` (the JEI bridge stays NeoForge-only: it uses NeoForge's `FluidStack`).
- Fabric glue (`fabric/`, written from JEI 26.2's Fabric module, not compiled): Loom `net.fabricmc.fabric-loom` 1.17.20, Fabric API 0.158.0+26.2. Recipes: server `RecipeSynchronization.synchronizeRecipeSerializer` for every serializer, client `ClientRecipeSynchronizedEvent` -> `RecipeMap.create(...)`. Screen events through `ScreenEvents.afterExtract` + `ScreenMouseEvents` / `ScreenKeyboardEvents`; mixins (config `required: false`, so a wrong name logs instead of crashing): `AbstractContainerScreen.extractRenderState` (render-pre cancel) and `mouseClicked` RETURN, `Gui.setScreen` (creative+), `KeyboardHandler.charTyped`, `MouseHandler.onScroll`, `KeyMapping.resetMapping` (GUI-only hotkeys, see `KeyTable`). HUD: `HudElementRegistry.addLast`. Fluids: `FluidVariantAttributes` / `FluidVariantRendering`. Access widener: `blitTiledSprite`.
- Differences from the single-loader 26.2 build: MAX_DATA for chat-shared bookmarks is 30000 (Fabric's packet limit; was 60000); Ctrl for creative+ scrolling is read from GLFW; the tree screenshot frame hook on Fabric runs one frame later (queued task at the start of the next frame) instead of `RenderFrameEvent.Post`.
- Not in the Fabric build: the JEI bridge (modded machines' recipes). A Fabric JEI plugin needs the `jei_mod_plugin` entrypoint and JEI's Fabric fluid ingredient type; to do.
- Untested: parse-checked only (no Minecraft jars here).


## Stage 44: 26.1.x multiloader (from the 26.2 multiloader)
- Copy of the 26.2 multiloader with the three 26.1.x differences found by diffing the single-loader 26.2 / 26.1.2 projects: the open screen is `Minecraft.screen` (26.2: `Minecraft.gui.screen()`), `setScreen` is on `Minecraft` (so the Fabric mixin is `MinecraftMixin`, not `GuiMixin`), and the mods.toml uses `neo_version_range` / `jei_version_range`.
- `gradle.properties`: compiled against 26.1.2, accepts [26.1, 26.1.2]; jar name label `26.1.x`; NeoForge 26.1.2.114; Fabric API 0.154.2+26.1.2 (the version exists on Fabric's maven; Fabric loader / Loom versions taken from 26.2, not verified for 26.1).
- `jei_version=none`: the NeoForge JEI bridge is left out of the build until a JEI 26.1.2 version is set (as in the single-loader 26.1.x project).
- Untested: parse-checked only.

- Build fix: Fabric Loom 1.17.20 needs Gradle 9.5.0, so the wrapper is 9.5.0 now (it was 9.2.1).

- First compile fixes: `KeyMapping.getKey()` is a NeoForge addition (new seam `ClientPlatform.boundKey`; Fabric uses `KeyMappingHelper.getBoundKeyOf`); `getGuiLeft/getGuiTop/getXSize/getYSize/getSlotUnderMouse` are NeoForge extensions (deprecated for removal), so the code reads `leftPos/topPos/imageWidth/imageHeight/hoveredSlot` and both loaders widen those fields (AT / AW); `SettingsWindow` imports `Cfg`.

## Stage 45: JEI bridge on both loaders
- Report: with JEI installed on 26.1.2 (NeoForge and Fabric, and 1.21.1) both JEI's panels and NEI's showed, and NEI did not behave: the bridge (which switches JEI's own screens off and reads its recipes) was missing in those builds.
- `NEIJeiPlugin` is loader-free now and lives in `common/` (`jei/`): the fluid ingredient class (`FluidStack` on NeoForge, JEI's `IJeiFluidIngredient` on Fabric, read by reflection) comes from the new seam `Platform.fluidOf`, the mod name from `Platform.modName`. NeoForge finds the plugin by `@JeiPlugin`, Fabric by the `jei_mod_plugin` entrypoint in `fabric.mod.json`.
- Both modules compile it against `jei-<mc>-common-api` (+ the loader's api) when `jei_version` is set in `gradle.properties`; `jei_version=none` leaves the package out (then JEI's own panels stay on). 26.2: 30.47.0.240. 26.1.x: set the version of the JEI you run (file name `jei-26.1.2-<loader>-<VERSION>.jar`).
- Untested: parse-checked only; Fabric part written from JEI's Fabric module source.

## Stage 46: 26.1.x becomes 26.1.2
- JEI only exists for 26.1.2 (no 26.1 / 26.1.1), so the project, jar label and `minecraft_version_range` are 26.1.2 only; `jei_version=29.53.0.114` (the JEI bridge is on in this build). Older notes saying 26.1.x mean this project.

## Stage 47: fluid lookup (R) listed every fluid
- Report: R on an NTM fluid showed every fluid of the mod. Cause (not seen in a log, inferred): a JEI recipe with an "any fluid" slot lists all fluid display items, and the index added that recipe to each of them. `RecipeIndex.wildcardFluids` now skips slots with more than 16 fluid options (inputs and outputs). The recipe still shows when looked up by its other items.
- Stage 47b: the 16-fluid rule did not cure it (still reproduced). `RecipeOverlay.show` now logs every lookup (item, recipe count, slot option counts of the first 12) to find the real cause.
- Stage 47c: real cause found in the log: HBM (NTM) shows every fluid as the ONE item `hbm:fluid_icon` with the fluid in a data component, so the item-keyed index found 523 recipes for any of its 157 fluids. A lookup now remembers the stack (`RecipeOverlay.focusNext`) and, when the stack carries components, keeps only recipes with the same item + components (`ItemStack.isSameItemSameComponents`) in the looked-at slots. Not covered: lookups started from history/bookmarks/tree (they only have the item). The stage-47 16-fluid rule stays (harmless).
- Stage 47d: exact component equality found nothing (the panel's fluid stack lacks components the recipe's has, e.g. an amount). The match is now subset-based (`RecipeOverlay.covers`: every component of the looked-up stack must be on the recipe's stack and equal); a miss logs both component sets.
- Stage 47e: following the addon notes (JEI subtype identity): `FluidDisplay.subtypeOf` is fed by the JEI bridge (`getUid(stack, UidContext.Recipe)` -> list[1]); a lookup matches recipe stacks by that identity (fallback: component subset). Not done yet from those notes: ids `registry~base64(subtype)` for bookmarks/favorites/history/search (they still collapse all hbm:fluid_icon fluids), real JEI fluid ingredients carried with amounts, tree/favorites rules.
- Stage 48: version 1.1.1. Fixed a compile error of stage 7 on 26.1.2 (`DataComponentPatch.get` needs a getter in 26.1: the component fallback in `RecipeOverlay.covers` reads `option.get(type)` instead). The other mod's `StackIds` (registryId~base64url(JEI subtype data)) matches what `FluidDisplay.subtypeOf` does for lookups.
- Stage 49: ids are subtype-aware: `FluidDisplay.idOf(ItemStack)` = registry id, or `registry~base64url(JEI subtype data)` when JEI says the stack has a subtype (NTM fluid icons); `FluidDisplay.stackOf(id)` rebuilds the stack (stacks seen so far, else one scan of JEI's ingredient list, set by the bridge). `ItemList.find` makes entries for such ids on demand. All `idOf(x.getItem())` over stacks (bookmarks, favorites keys, tree, pull, history copies, recipe overlay) now use the stack. `give` uses the plain id. Old saved bookmarks/favorites of fluid icons keep the old plain id (clear them). Mirrors the addon's StackIds.
- Stage 50: (1) crafting-tree sub-recipes are saved with only the result the parent needs (`RecipeBookmarks.tree`: lead map, `results(view, leadId)`); the top recipe keeps all results. (2) The heart's tooltip names the selected result ("Result: <name>", "Scroll to choose the result") when the recipe has several. (3) Not done: the dark squares left of the recipe window (3 in Cracking, 4 in Oil Refinery) - not identified yet; the station column is the only thing drawn there.
- Stage 50b: fixed an ambiguous overload (results(view, null)): the String variant is now resultsFor.
- Stage 51: the dark squares floating at the top left while the heart is hovered were `highlightResult`: JEI's `drawHighlight` draws at slot positions relative to the layout, so it must be translated to `layout.getRect()` first, and it matched outputs by item (every NTM fluid is `hbm:fluid_icon`) instead of by `FluidDisplay.idOf`. Fix written by another agent and tested by the user on 26.1.2; applied to 26.2 too.
- Stage 52: fluid amounts and the terminal key. (1) Fluids carry their amount in millibuckets as the stack count: real JEI fluid ingredients (NeoForge `FluidStack.getAmount`, Fabric droplets/81 via the new `Platform.fluidAmount`), and fluids that are items (NTM - Continuum) from the "<n>mB" line of the stack's tooltip (`FluidDisplay.itemFluidAmount`, only for stacks JEI gives a subtype). Recipes/bookmarks add counts as before, so groups and the chain math now work in mB. JEI-drawn recipes get a small amount label on fluid slots; the recipe tooltip of a fluid display item names the amount; bookmark and tree amounts of fluids end in mB. (2) New key T (`terminal_name`, not in NEI): over an item or fluid, types its name into an open AE2 terminal's search field (only in a terminal screen); the existing terminal sync carries it to NEI's search.
- Stage 53: (1) the labels floating at the top left of JEI-drawn recipes: `getAreaIncludingBackground` can start at the layout origin (union with an empty background), so only its far edges are used. (2) Fluids-as-items whose recipe stack has no amount (NTM - Continuum oil refinery): the category puts it in the slot's tooltip, so `NEIJeiPlugin.slotAmount` reads "<n>mB" from there when the view is built (and the label uses the same, cached per slot); no label when the amount is unknown, instead of "1mB". (3) Fluid amounts in bookmarks/tree use `FluidDisplay.amountLabel` (250mB, 2.5B, 12B) so neighbours no longer overlap.
- Stage 54: version 1.1.2. New option `fluidAmountsInMillibuckets` ("Fluid amounts in mB only", off): `FluidDisplay.amountLabel` keeps mB from 1000 on. Release notes in CHANGELOG 1.1.2.
