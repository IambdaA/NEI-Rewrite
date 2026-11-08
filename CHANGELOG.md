# Changelog

## 1.3.1

- Fixed: from the second cheat click on, the stack NEI gave you was thrown into the world as soon as you let go of the mouse (the same happened to any stack on the cursor clicked over NEI's panels)
- As in NEI: clicking a panel while holding a stack deletes it in Cheat Mode (or Utility Mode with Delete), a right click deletes one item; otherwise the stack is dropped. Over a panel, the held stack's tooltip says "Click to delete" / "Click to drop"
- Cheat Mode: a click on a fluid gives its bucket, as JEI does (fluids without a bucket still open their recipes)
- The `+` and autocrafting fill several sets when a recipe uses a tool that stays in the grid (GregTech's hammers, files, saws ...), as many as the tool has durability left; before, one craft at a time

## 1.3.0 (26.1.2, NeoForge and Fabric)

- After joining a world, a chat message asks you to report issues on GitHub (the word is a link); its "Turn off this message" link switches it off, Inventory > Release Notice in the settings switches it back on
- New Report Issues button on the first settings screen: opens NEI Rewrite's issue tracker on GitHub
- Fixed: the settings screen did not show button rows (Tools > Data Dumps)
- The cheat toolbar keeps clear of what other mods draw beside the inventory (the FTB Quests / FTB Teams sidebar buttons, areas mods register with JEI, other mods' widgets): a button that would cover one moves to the next free spot, and the bookmark panel starts below the toolbar's last row
- As in NEI, Tools > Data Dumps: Item Panel can be written as CSV, NBT or Json and NEI Handlers as CSV or Json (the mode button left of Dump)
- As in NEI: holding Shift over a crafting chain group's strip shows Required Crafts, Missing Items and the Ingredients you already have (compared with your inventory; unlike NEI, intermediate items in your inventory do not lower the crafts)
- The scroll bars of the recipe window (infinite scroll, the machines column in Scroll mode) can be dragged
- Fluids that their mod shows as an item of its own (NTM - Continuum's droplet): NEI shows that item's icon, name and tooltip, R / U find its recipes, and NEI's own duplicate entry of the fluid is left out of the item list
- Fluids without a block (GregTech) are named as their mod names them ("Bio Diesel") instead of by their id
- As in NEI: the + closes the recipe window as soon as anything was put into the grid (before, a recipe with a missing ingredient kept it open)
- The greyed out rows of the settings screen now work (all but Tools > Load handlers / catalysts from Config, which read 1.7.10 files). Every new option starts at NEI's default:
  - As in NEI, Search Widget: Widget Position (Center / Right), Hide Until Searching, Autofocus (No / Yes / Extended), Search Format (Old Format swaps % and @ and resets the search modes)
  - As in NEI, Bookmarks Panel: Visibility, REI Style Animation (new bookmarks pop up), Show Recipe Tooltips in Bookmarks (a bookmarked recipe's result shows the recipe), Show Recipe Marker (R / background) and its colour, Show Handler Icon, Bookmark items with recipe, Bookmark recipe with count, Tree Zoom without Ctrl; hovering a crafting chain group's strip lists what the chain makes and needs
  - As in NEI, Recipes GUI: JEI Style Tabs, Creative or JEI tab style, JEI Style Recipe Catalysts (None / Grid / Scroll; None also stops a machine's uses from listing everything it makes, as Ctrl does), Cycled Ingredients Tooltip ("Accepts following" grid), Use Infinite Scroll, Max Height, Require holding shift to move items when overlaying recipe, Scroll Pages, Stop cycling while hovering (Shift always stops it), Handler Info Widget (Shift + D)
  - As in NEI, Favorites: Visibility, Show Recipe Tooltips in Right Panel / in Recipe Gui, Generate Single-Recipe Favorites, Depth
  - As in NEI: Item Panel Scale, Show Fluid Temperature / State, Lock overlays between dimensions, World > Panels Settings (drag the bookmark and item panels' edges)
  - Commands: the toolbar and the give action send the command templates of the Commands page (defaults are today's commands, e.g. `/weather {1}`); a template that does not start with `/` turns its button off, as in NEI
  - As in NEI, Tools > Data Dumps: Items, Blocks, Potions, Enchantments, Biomes, Item Panel and NEI Handlers write CSV files to `dumps/`
- New option "Require holding shift to move items when overlaying recipe" (off by default here, NEI's default is on): on, a plain click on a crafting recipe's `+` only shows the recipe faded in the crafting grid and Shift + click moves the items. Off, a click moves as many sets as fit, Ctrl + click one set
- Fluid tooltips in NEI's panels now show what the fluid's mod says about it (NTM's traits, fuel values, help line), read through NeoForge's fluid tooltip event / Fabric's fluid tooltip. NEI's Temperature / State lines are shown only for vanilla fluids (water, lava)
- U on a machine lists the recipes it makes whatever JEI Style Recipe Catalysts is set to (NEI ties this to the column; here None only hides the column). Ctrl while looking up still leaves them out
- Fixed: Shift + the bookmark key over a recipe kept its amounts; as in NEI it does so only with Ctrl or with "Bookmark recipe with count" on
- As in NEI: Inventory settings have the Utilities row (which toolbar buttons Utility Mode shows) and the Gamemodes row (which gamemodes the gamemode button cycles through, now with Adventure), the options Show Hotkeys and Show Hotkeys Help Text, and NEI's dark slate buttons
- As in NEI: the wrench button has three modes, Recipe, Utility and Cheat Mode, shown by a white, gold or red wrench; Ctrl + click on it cycles them and its tooltip says which is on. Utility Mode shows the toolbar's Delete and Magnet buttons, also in survival
- As in NEI: World > Highlight Tooltips is a screen with a Shown / Hidden button and a sample box you drag to place the tooltip; World > Presets List opens the presets window
- As in NEI: the item panels leave out the slots under the potion effect boxes of the inventory (option "Ignore overlap with potion effect HUD" under Inventory > Bookmarks Panel turns it off)
- As in NEI: holding Alt over an inventory slot shows one box (name, hotkeys, the item's other lines) instead of vanilla's tooltip plus a second box; vanilla's slot tooltips and the effect boxes are no longer covered by the panels
- The settings screen follows NEI's option tree and wording: Inventory / World pages, NEI's page order, labels and button texts (Enabled / Disabled, Top / Bottom, Show / Hide ...)
- The settings are now a full screen like NEI's options screen (list with the dirt bands and a scrollbar, Back button, item sorter page), instead of a window over the inventory. Not included: NEI's per-world overrides (W buttons, the Global button is shown greyed) and the panel-drag screen
- As in NEI: the settings have every page of NEI's options screen (Inventory, World, Commands, Tools) in NEI's order. Options that NEI Rewrite has no feature for yet are shown greyed out and do nothing, so the screen can be compared with NEI's
- The item panels, the history / craftables rows and the bookmark panel keep away from what other mods draw beside the inventory (the sidebar buttons of FTB Quests / FTB Library, a recipe book, buttons other mods add): item slots under such an area are left out and clicks there go to the other mod, the bookmark panel shrinks to the biggest free rectangle of its edge. Areas come from the exclusion areas mods register with JEI plus any visible widget that sticks out of the inventory window
- New option "UI Style": NTNH-Approximate (default, slate buttons that approximate the look of the NTNH pack), GTNH-Modernity (the dark buttons of Modernity-GTNH) or Vanilla (classic grey). NEI takes this look from the resource pack's button texture

## 1.1.2

- **Fluid amounts** (NTM): a fluid in a recipe now carries its amount in millibuckets. JEI-drawn recipes show it on the fluid slot and in the tooltip, bookmark groups and the crafting tree count and show it (`250mB`, `2.5B`; the new option "Fluid amounts in mB only" keeps `2500mB`, off by default)
- **Fluids that are items** (NTM - Continuum draws every fluid as the one item `hbm:fluid_icon`): R / U, bookmarks, favorites and trees treat each fluid as its own entry (ids use JEI's subtype identity). A tree's sub-recipe row keeps only the result the parent needs, first in its row. The heart's tooltip names the selected result
- New key **T** (not in NEI): over an item or fluid, types its name into an open Applied Energistics terminal's search field
- Fixed: the dark squares floating at the top left while hovering a recipe's heart
- Fixed: R on a fluid listed the recipes of every fluid of the mod
- Known: Continuum's oil refinery gives no fluid amounts (its recipes do not state them), so its bookmark math is off

## 1.1.1

- **Fluids that are items** (NTM - Continuum draws every fluid as the one item `hbm:fluid_icon`): R / U on such a fluid now only finds recipes with that fluid. Before, it found the recipes of every fluid of the mod. The match uses JEI's own subtype identity of the stack.
- The JEI bridge is on for 26.1.2 on both loaders (JEI 29.53.0.114); JEI's own panels are switched off. This version is for Minecraft 26.1.2 only
- Recipe lookups of NEI's own fluid display items skip "any fluid" slots

## 1.1.0

Fixes and small additions.

- **Fabric**: 26.2 is now one project with a NeoForge and a Fabric version (same features, including the JEI bridge). Mods can register with NEI through a Fabric entrypoint (`notenoughitems` / `NEIPlugin`)
- The options file is now `config/notenoughitems-client.json` on both loaders (the old NeoForge `.toml` is not read, options start at their defaults once)
- Fluids: the fluid display items no longer depend on the item registry. They now keep their own ids, so bookmarks, history,
  recipe lookups and the item panel keep working on a server whose registry sync drops them. A fluid that cannot be drawn is
  reported once in the log
- JEI-drawn recipes: R / U / bookmark now work on fluids under the mouse (before, only item stacks were found)
- Favorites: Shift + F (the favorite-item key) over a result in the recipe window favorites that recipe for that result, so a
  recipe whose result is not its first one (oil refinery: unsaturated hydrocarbons) can be used by recipe trees. The item tooltip of
  a recipe result shows this key just above the mod name
- New option "Single favorite counts for all its results" (off): a recipe tree uses the only favorited recipe that makes an ingredient,
  even when it was favorited under another of its results. Several candidates: nothing is guessed
- New option "Choose favorite result with the mouse wheel" (off): NEI's way, the wheel over the heart changes which result it favorites;
  the chosen result is greyed with a heart on it
- Item list log line: how many fluid display items exist and how many the item registry still holds

## 1.0.0

First release for Minecraft 26.2 / NeoForge 26.2.0.90.

- Item panel with NEI's search syntax and modes, sorting, collapsible items, zoom, quantity field, presets
- Recipe and usage lookup, recipe window with tabs, station column, recipe search, history panel
- Recipe transfer (`+`) with missing-ingredient marking, autocrafting, pulling items from containers
- Bookmarks (pages, groups, todo lists, recipes, crafting chains), crafting tree window, chat links, tree screenshots
- Subsets, craftables panel, enchantment and potion creator windows, settings window
- Cheat tools: toolbar, delete mode, magnet, extended creative inventory, mob spawner items, mob spawn overlay
- JEI bridge: recipes of modded machines and fluids through JEI plugins, `+` through JEI transfer handlers
- AE2 terminal search sync
- Mod API: `NEIRegisterEvent`, `API.registerRecipeSource`, `addCatalyst`, `registerRecipeTransferHandler`, subsets, hiding, aliases
- Villager trade tab (learned from the villagers you open)
