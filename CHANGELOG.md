# Changelog

## 1.3.1

- Fixed: from the second cheat click on, the stack NEI gave you was thrown into the world as soon as you let go of the mouse (the same happened to any stack on the cursor clicked over NEI's panels)
- As in NEI: clicking a panel while holding a stack deletes it in Cheat Mode (or Utility Mode with Delete), a right click deletes one item; otherwise the stack is dropped. Over a panel, the held stack's tooltip says "Click to delete" / "Click to drop"
- Cheat Mode: a click on a fluid gives its bucket, as JEI does (fluids without a bucket still open their recipes)
- The `+` and autocrafting fill several sets when a recipe uses a tool that stays in the grid (GregTech's hammers, files, saws ...), as many as the tool has durability left; before, one craft at a time
- Fixed: with an older JEI 15 than NEI is built with (JEI 15.20 in All the Mods 9), NEI's item list stayed empty

## 1.3.0 (1.20.1, Forge and Fabric)

- After joining a world, a chat message asks you to report issues on GitHub (the word is a link); its "Turn off this message" link switches it off, Inventory > Release Notice in the settings switches it back on
- New Report Issues button on the first settings screen: opens NEI Rewrite's issue tracker on GitHub
- Fixed: the settings screen did not show button rows (Tools > Data Dumps)
- The cheat toolbar keeps clear of what other mods draw beside the inventory (the FTB Quests / FTB Teams sidebar buttons, areas mods register with JEI, other mods' widgets): a button that would cover one moves to the next free spot, and the bookmark panel starts below the toolbar's last row
- As in NEI, Tools > Data Dumps: Item Panel can be written as CSV, NBT or Json and NEI Handlers as CSV or Json (the mode button left of Dump)
- As in NEI: holding Shift over a crafting chain group's strip shows Required Crafts, Missing Items and the Ingredients you already have (compared with your inventory; unlike NEI, intermediate items in your inventory do not lower the crafts)
- The scroll bars of the recipe window (infinite scroll, the machines column in Scroll mode) can be dragged
- Fluids that their mod shows as an item of its own (NTM - Continuum's droplet): NEI shows that item's icon, name and tooltip, R / U find its recipes, and NEI's own duplicate entry of the fluid is left out of the item list
- Fluids without a block (GregTech) are named as their mod names them ("Bio Diesel") instead of by their id
- The greyed out rows of the settings screen now work (all but Tools > Load handlers / catalysts from Config, which read 1.7.10 files). Every new option starts at NEI's default:
  - As in NEI, Search Widget: Widget Position (Center / Right), Hide Until Searching, Autofocus (No / Yes / Extended), Search Format (Old Format swaps % and @ and resets the search modes)
  - As in NEI, Bookmarks Panel: Visibility, REI Style Animation (new bookmarks pop up), Show Recipe Tooltips in Bookmarks (a bookmarked recipe's result shows the recipe), Show Recipe Marker (R / background) and its colour, Show Handler Icon, Bookmark items with recipe, Bookmark recipe with count, Tree Zoom without Ctrl; hovering a crafting chain group's strip lists what the chain makes and needs
  - As in NEI, Recipes GUI: JEI Style Tabs, Creative or JEI tab style, JEI Style Recipe Catalysts (None / Grid / Scroll; None also stops a machine's uses from listing everything it makes, as Ctrl does), Cycled Ingredients Tooltip ("Accepts following" grid), Use Infinite Scroll, Max Height, Require holding shift to move items when overlaying recipe, Scroll Pages, Stop cycling while hovering (Shift always stops it), Handler Info Widget (Shift + D)
  - As in NEI, Favorites: Visibility, Show Recipe Tooltips in Right Panel / in Recipe Gui, Generate Single-Recipe Favorites, Depth
  - As in NEI: Item Panel Scale, Show Fluid Temperature / State, Lock overlays between dimensions, World > Panels Settings (drag the bookmark and item panels' edges)
  - Commands: the toolbar and the give action send the command templates of the Commands page (defaults are today's commands, e.g. `/weather {1}`); a template that does not start with `/` turns its button off, as in NEI
  - As in NEI, Tools > Data Dumps: Items, Blocks, Potions, Enchantments, Biomes, Item Panel and NEI Handlers write CSV files to `dumps/`
- New option "Require holding shift to move items when overlaying recipe" (off by default here, NEI's default is on): on, a plain click on a crafting recipe's `+` only shows the recipe faded in the crafting grid and Shift + click moves the items. Off, a click moves as many sets as fit, Ctrl + click one set
- Fluid tooltips in NEI's panels now show what the fluid's mod says about it (NTM's traits, fuel values, help line), read through JEI's fluid tooltip on Forge 1.20.1 / Fabric's fluid tooltip. NEI's Temperature / State lines are shown only for vanilla fluids (water, lava)
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
- As in NEI: the + closes the recipe window as soon as anything was put into the grid (before, a recipe with a missing ingredient kept it open)

## 1.1.2 (1.20.1, Forge and Fabric)

First release for Minecraft 1.20.1, with everything of the 1.21.1 version (1.0.0 to 1.1.2). One code base for both loaders
(`common/`) with a small glue module for each. JEI 15 is required (NEI does not work without it: JEI's own panels would overlap
NEI's).

- Everything of the 1.21.1 version: item panel and search, recipe and usage lookup, recipe transfer, bookmarks, crafting trees,
  subsets, cheat tools, extended creative inventory, the T key for AE2 terminals, favorites per result, fluid amounts
- JEI bridge: recipes of modded machines, recipes of fluid display items and the `+` in modded crafting screens come from JEI 15.
  JEI 15 has no switch to turn its GUI off, so NEI turns JEI's ingredient list and bookmark overlay off through JEI's own toggle
  state and takes the room of JEI's buttons away (the reflection is guarded: if JEI changes, NEI logs it and carries on)
- JEI fluid ingredients (Forge fluid stacks, Fabric fluids) become NEI's fluid display items, with their amount
- Mods that show every fluid as one item with NBT (NTM Continuum's `hbm:fluid_icon`): each fluid keeps its own id; history records the fluid
- Forge: packets are optional channels (a client or server without the mod can still connect); the container screen's frame comes
  from Forge's own getters. Key mappings are GUI-only, so NEI's keys never take a game key
- Fabric: NEI's key and mouse listeners run before JEI's; keys NEI or JEI bound to the same key as a game control (A = walk left) are
  given back to the game; the creative inventory shows NEI
- Items are NBT in 1.20.1 (not data components): fluid and item identity use the stack's tag
