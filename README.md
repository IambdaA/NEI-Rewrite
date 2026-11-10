# NEI Rewrite

**Download on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/unoffical-nei-rewrite)**

> **IMPORTANT: recipes of modded machines need [JEI](https://modrinth.com/mod/jei) installed.**
> NEI Rewrite shows vanilla recipes by itself. For machines from other mods (AE2, Create, Mekanism ...) it reads the
> recipes from JEI's plugins, so install JEI next to it. JEI's own screens are switched off; you only see NEI.

An **unofficial** rewrite of the [GTNH fork of NotEnoughItems](https://github.com/GTNewHorizons/NotEnoughItems) for
Minecraft 26.2 on **NeoForge and Fabric**, by Iambda. It follows NEI's design and rules, but the code is new. It aims to look and work
like NEI in GregTech: New Horizons.

## Features

- Item panel with NEI's search syntax (`@mod #tooltip $tag &id %subset -not "quotes" regex`, expression mode), item sorting,
  collapsible items, item zoom, quantity field, presets
- Recipe and usage lookup (click an item, or R / U), recipe window with tabs, station column and recipe search
- Recipe transfer (the `+` button) into crafting grids with missing ingredients marked, autocrafting, pulling items from
  containers
- Bookmarks with pages, groups, todo lists, whole-recipe bookmarks, crafting chains, the crafting tree window with stats,
  chat links and tree screenshots
- Item subsets, hidden items, history panel, craftables panel
- Cheat tools: toolbar (time, weather, heal, game mode), delete mode, item magnet, extended creative inventory, mob spawner
  items, mob spawn overlay (F7), enchantment and potion creator windows
- With JEI: recipes of every mod that has a JEI plugin (fluids included), and the `+` in modded crafting screens such as
  the AE2 crafting terminal
- AE2 terminal search sync (type in the terminal, NEI's search follows, and the other way round)
- A mod API for other mods (see below)

Hold **Alt** over an item for its hotkeys. Keys are under Options > Controls > NotEnoughItems, options in the wrench
button's settings screen, which follows NEI's options screen page by page (Inventory, World, Commands, Tools). Only Tools >
Load handlers / catalysts from Config stay greyed out: they read NEI's 1.7.10 files.

Not affiliated with the GTNH team. See `PORTING_NOTES.md` for the porting log and what is not ported.

## Requirements

- Minecraft 26.2 with NeoForge 26.2.0.90 or newer, or Fabric Loader 0.19+ with Fabric API
- [JEI](https://modrinth.com/mod/jei) 30 or newer: **needed to see the recipes of modded machines** and for the `+` in modded
  crafting screens (optional, vanilla recipes work without it)
- Works on the client alone. Install it on the **server too** if you want recipe lookup on a multiplayer server: the
  server sends the recipes (without it you only see recipes the server shares with the recipe book).

## Guide

Everything here works in the inventory and in every container screen (chests, crafting tables, machines). Hold **Alt** over
an item, a bookmark group's strip, a subset or a recipe's heart to see the keys that work there. Every key can be changed
under *Options > Controls > Key Binds > NotEnoughItems*; the keys below are the defaults.

### The screen

- **Item list** (right): every item and fluid in the game, with a page row above it. The **search field** is at the bottom centre.
- **Bookmarks** (left): the items, recipes and groups you bookmarked.
- **Wrench button** (bottom left): the settings and the three modes. **Book button**: shows or hides the bookmarks (also **B**).
- **Toolbar** (top left, Utility and Cheat Mode): time, rain, heal, game mode, delete, magnet, enchanting, potions.
- **Subsets bar** (top centre): menus of item groups.
- **History** and **craftables** rows under the item list.
- **O** hides all of NEI until you press it again.

### Modes (the wrench)

Ctrl + click the wrench to switch modes. Its colour shows the mode, its tooltip names it, and a plain click opens the settings.

- **Recipe Mode** (white wrench): a left click on an item of the list shows its recipes, a right click its uses.
- **Utility Mode** (gold): adds the toolbar's Delete and Magnet buttons, also in survival. *Inventory > Utilities* in the
  settings picks which buttons it shows.
- **Cheat Mode** (red): a left click gives the item (as many as the quantity field says, a stack while it is empty), a right
  click gives one. Shift + left / right click shows recipes / uses. The whole toolbar is shown. Needs creative mode or
  operator rights.

### Item list

- **Pages**: the arrows above the list, the mouse wheel, or **Page Up / Page Down** over the list.
- **Quantity field** (under the list): how many items a cheat click gives. Type digits, use the wheel or the - / + buttons
  (Shift: steps of 10, Ctrl: 64).
- **Collapsible items**: similar items (spawn eggs, music discs, potions ...) share one slot. **Alt + click** opens or
  closes a group. The groups are search lines in `config/notenoughitems-collapsible.txt`, which you can edit.
- **Item zoom**: **Shift + Z** switches it on. The item under the mouse is drawn big beside the window.
- **Sorting**: the item sort page of the settings orders the list (Minecraft first, by mod, by id, by name).
- **Subsets** (bar at the top centre): menus of item groups (by mod, tools, armour, and mods' own subsets). Left click shows
  a subset, right click hides it, double click shows only that subset, and a double click on the bar shows everything again.
  The items in a menu: left click shows one, right click hides it, Ctrl + click gives it (Cheat Mode), Shift + click
  searches for it. Hidden items are kept in `config/notenoughitems-hidden.txt`.
- **Presets** (*World > Presets List*): named lists of items that hide items, remove them together with their recipes, or
  make a subset or a collapsible group of your own.
- **History panel**: the items whose recipes or uses you looked up, in rows under the list.
- **Craftables panel** (off by default, *Inventory > Craftables Panel*): what you can craft right now from your
  inventory, favorites first.
- Mob spawners in the list show their mob.
- **A stack on the cursor** clicked onto NEI's panels is deleted in Cheat Mode or creative mode (a right click deletes
  one item), otherwise it is dropped. The tooltip says which.

### Search

Type in the search field (**F** focuses it). Words separated by a space must all match.

- `@mod` searches mod names, `#text` tooltips, `&id` registry ids, `$tag` item tags and `%subset` subsets. Plain text
  searches the name.
- `-word` or `!word` leaves matches out, `"quoted text"` keeps a space, and `a b | c` means (a and b) or c.
- By default the text is a regular expression (*Search > Search Mode: Regex*). The other modes: Plain, Extended (`?` is one
  character, `*` anything, `r/regex/` a regular expression) and Extended+ (also `{ }` groups, `/regex/` and `\` escapes).
- Each prefix can be set to Always (also searched without the prefix), Prefix or Never. A space can mean AND, a plain space,
  or AND-X (every word in the same kind of text). Search Format: Old Format swaps `%` and `@`.
- **Up / Down** while typing go through your earlier searches. The text is coloured by its syntax.
- Options: Widget Position (centre or right), Hide Until Searching (empty list until you type), Autofocus.
- With an Applied Energistics terminal open, its search and NEI's search stay the same, whichever one you type in.

### Recipes and uses

- **R** over any item shows its recipes and **U** its uses: in the item list, your inventory, the bookmarks, the recipe
  window and the crafting tree. Fluids work too.
- **Tabs** above the window are the recipe types. The **machines column** on its left shows what makes that type; click a
  machine for its recipes. U on a machine also lists everything it makes (hold Ctrl while looking up to leave that out).
- **Left / Right** turn the pages and **Up / Down** change the recipe type. The wheel turns pages (Shift + wheel, or the
  wheel over the title row, changes the type). **Backspace** goes back to the previous lookup.
- The **magnifier** button keeps only the recipes with an item matching your search.
- **Shift + D** shows a box with what is known about the recipe type (turn on *Recipes GUI > Handler Info Widget* first).
- Ingredients that cycle through alternatives stop while the mouse is on them or Shift is held. Their tooltip lists every
  accepted item, favorite ingredients first.
- The **+** button moves the recipe into the open crafting grid: a click moves as many sets as fit, Ctrl + click one set.
  While the mouse is on it, the ingredients you lack are red and the ones you have green. With *Require holding shift to
  move items* on, a click only shows the recipe faded in the grid and Shift + click moves the items.
- The **heart** makes the recipe its item's favorite (see Favorites).
- **Villager trades**: a tab with the trades of every villager you opened in this session.
- **Fluid amounts** show in buckets from 1000 mB on (`250mB`, `2.5B`); *Fluid Amounts in mB Only* keeps millibuckets.
- **With JEI**: the recipes every mod gives JEI, JEI's information pages and, on 26.2 and 26.1.2, its fuel pages.
- Options: tab style, machines column (None, Grid or Scroll), infinite scroll instead of pages, maximum height.

### Bookmarks

- **A** over an item bookmarks it; **A** over a bookmark removes it. **Ctrl + A** bookmarks it with its amount.
- In the recipe window, **Shift + A** bookmarks the item together with its recipe and **Ctrl + Shift + A** with the
  recipe's amounts. A bookmark that remembers a recipe is marked with an R (or the recipe type's icon, *Show Handler
  Icon*); Shift + A over it removes the recipe.
- Click a bookmark for its recipes or uses (Cheat Mode: gives it).
- **Ctrl + wheel** changes a bookmark's amount, **Ctrl + Alt + wheel** in bigger steps. **Shift + drag** moves it.
- The arrows at the top turn the bookmark pages. The row at the bottom switches between separate bookmark lists; there is
  always an empty one after the last.
- The **P** button beside the bottom row pulls every bookmarked item out of the open chest (Shift + click: only what is
  missing).
- Options: World Specific (separate bookmarks per world or server), pop-up animation, recipe tooltips, the recipe marker
  and its colour.

### Groups and crafting chains

The thin strip left of the bookmarks shows the groups.

- **Drag with the left button** along the strip to put rows in a group; drag with the right button to take rows out.
- **Click** the strip to switch the group between a grid and a todo list (one item per row). **Alt + click** collapses
  it, **Shift + A** removes it, **Shift + drag** moves it, **Ctrl + wheel** changes how many times it is made.
- **Right click** the strip to make the group a **crafting chain** (green strip): its bookmarked recipes are linked, so
  the amounts add up through every step. Hovering the strip lists what the chain makes and needs. Holding **Shift** shows
  Required Crafts, Missing Items and the ingredients you already have. A tool that wears down (GregTech's hammers, files,
  saws ...) shows the durability the chain takes from it, in blue.
- Over a group's strip:
  - **V** takes its items out of the open chest, **Shift + V** only what is missing.
  - **Ctrl + L** sends the group to chat.
- Over a crafting chain's strip:
  - **T** opens its crafting tree.
  - **Shift + C** autocrafts it, **Shift + Ctrl + C** only what is missing.
  - **G** (Cheat Mode only) gives everything the chain still lacks, plus what its machine and furnace steps make, so the
    rest can be autocrafted. A tool comes as enough whole tools for the durability the chain takes, a fluid as filled buckets.

### Crafting tree

A window that draws a crafting chain as a tree: every recipe is a box, with the boxes of its ingredients below it. Open it
with **T** over a crafting chain's strip, or **Shift + T** over a recipe's heart.

- The wheel scrolls, Shift + wheel scrolls sideways and Ctrl + wheel zooms (*Tree Zoom without Ctrl* zooms with the wheel
  alone). Drag the background to move around.
- Ctrl + wheel over a top box changes how many to make, Ctrl + Alt + wheel in stacks.
- Click a box for its recipes (left) or uses (right). The + in that recipe window makes that recipe the box's recipe.
- The small recipe icon in a box's corner removes its recipe. The arrow under a box hides or shows everything below it.
- **A** over a box bookmarks the item, **Ctrl + A** with its amount.
- Toolbar: use your inventory for the maths (a snapshot, click again to clear), fit the tree to the window, collapse or
  expand everything, collapse what has nothing left to craft, the stats panel (results, remainders, ingredients and
  crafts needed and available, recipe types; choose which sections show), **save the tree as a PNG** in `screenshots/`,
  and send the group to chat.

### Favorites

- The **heart** on a recipe makes it the favorite recipe of its result. For a recipe that makes several things,
  **Shift + F** over one result favorites the recipe for that result.
- **Shift + F** over an ingredient in the recipe window makes it a favorite ingredient: it is listed first (marked F)
  wherever a recipe accepts several items.
- **Shift + F** over an item in the item list, or **Shift + A** over a heart, saves the favorite recipe tree as a crafting
  chain in the bookmarks: the item's favorite recipe and the favorite recipes of its ingredients, down to the *Depth* set
  under Favorites. **Shift + T** over a heart opens that tree.
- Items and machines with a favorite recipe show it in their tooltip, and the craftables panel lists favorites first.
- Options: World Specific, Generate Single-Recipe Favorites (an item made by only one recipe uses it), Depth, Single
  Favorite Counts for All Results, Choose Result with Mouse Wheel.

### Crafting help

- **Shift + S** over an item fills the open crafting grid with its bookmarked or favorite recipe, **Shift + Ctrl + S**
  with the bookmark's amount.
- **Autocrafting**: with a crafting table or your inventory open, **Shift + C** over a bookmarked recipe, a crafting chain
  or a recipe in the recipe window crafts it, every step from the bottom up. **Shift + Ctrl + C** crafts only what is
  missing. It stops and says why in chat when something is missing. Machine and furnace steps cannot be autocrafted (in
  Cheat Mode, G gives their results). A tool that stays in the grid is used for as many sets as its durability allows.
- **Pulling**: **V** over a bookmark or a group's strip takes its items out of the open chest, barrel, shulker box or ender
  chest into your inventory, **Shift + V** only what is missing.
- **Mouse wheel transfer**: the wheel down over a slot moves one item to where Shift + click would send it, the wheel up
  pulls one in. Shift + click an empty slot while holding a stack to move every stack of that kind there. Shift + click
  outside the window (or Shift + the drop key over a slot) throws every stack of that kind.

### Copy and share

- **Ctrl + C** copies an item's name, **Ctrl + X** its id and **Ctrl + D** its tags.
- **Ctrl + L** sends an item, a bookmarked recipe or a whole group to chat, and **Ctrl + Shift + L** in the recipe window
  sends the recipe. Players with NEI Rewrite click the message to add it to their bookmarks (needs the mod on the server).
- **T** over an item, with an Applied Energistics terminal open, types its name into the terminal's search.

### Cheat tools

The toolbar needs creative mode or operator rights; it sends normal commands, so the server's permissions decide.

- **Time** (dawn, noon, dusk, midnight), **rain** and **heal**.
- **Game mode**: cycles survival, creative, creative+ and adventure (*Inventory > Gamemodes* picks which).
- **Creative+**: the inventory key opens an extended creative inventory with 54 more slots, kept by the server. Ctrl +
  wheel in the world cycles its rows, the inventory and the hotbar.
- **Delete**: click it to switch delete mode on, then click a slot to delete its stack (right click: one item, Shift +
  click: every item of that kind). Shift + click the button deletes your whole inventory; clicking it with a stack on the
  cursor deletes that stack.
- **Magnet**: pulls dropped items towards you.
- **Enchanting** (**X** over an item): every enchantment that fits the item, at any level up to 255.
- **Potions** (**P**): a potion, splash potion, lingering potion or tipped arrow with any effects, levels and durations.
- **Mob spawn overlay** (**F7**, in the world): an X on every block around you where monsters can spawn, red at any time
  and yellow only at night.
- **Highlight tooltips** (**numpad 0**, in the world): the name of the block you look at. *World > Highlight Tooltips*
  places it.
- World keys (unbound by default): dawn, noon, dusk, midnight, rain, heal and creative, while no screen is open.
- *Commands* page of the settings: the commands the toolbar and the give action send, for servers with other commands.

### Settings

The wrench opens a settings screen with NEI's pages:

- **Inventory**: NEI on / off, the mode, Utilities, Gamemodes, item sort, and the pages Search, Bookmarks Panel, Recipes
  GUI, Subsets, History, Craftables, Collapsible Items, Item Zoom, Favorites and Tooltips. Then Item Panel Scale, mouse
  wheel transfer, the hotkey help, the quantity field, Autocrafting, **UI Style** (NTNH-Approximate, GTNH-Modernity or
  Vanilla buttons) and the release notice.
- **World**: Highlight Tooltips, **Panels Settings** (drag the edges of the bookmark and item panels), Presets List, the
  overlays and the mob overlay's range.
- **Commands**: the command templates.
- **Tools > Data Dumps**: writes the items, blocks, potions, enchantments, biomes, the item panel (CSV, NBT or JSON) and
  the recipe types (CSV or JSON) to `dumps/`.
- **Report Issues** (first page) opens the issue tracker.
- Options are kept in `config/notenoughitems-client.json`, bookmarks in `config/notenoughitems-bookmarks.txt`.

### Other mods

- **JEI**: NEI reads every mod's recipes, information pages and recipe transfer handlers from JEI's plugins, so the + works
  in modded crafting screens too. JEI's own screens are switched off.
- **Applied Energistics 2**: search sync, T types into the terminal, and the + works in the crafting terminal (with JEI).
- **Other mods' buttons**: the panels and the toolbar move aside for what other mods draw beside the inventory (the FTB
  Quests / FTB Library sidebar, the recipe book, areas mods register with JEI).
- **Fluids**: in the item list and in recipes with their amounts; a cheat click gives a bucket. Fluids that a mod shows as
  an item of its own (NTM - Continuum) use that item's icon, name and tooltip.

### Multiplayer

- NEI Rewrite works on the client alone. Install it on the **server too** to look up every recipe the server knows (without
  it you see only what the server shares with the recipe book) and for delete mode, the magnet, creative+ and chat links.
- Cheat Mode and the toolbar need creative mode or operator rights.

### All keys

| Key | Where | What |
| --- | --- | --- |
| R / U | over an item | Recipes / uses |
| A | over an item | Add or remove a bookmark (Ctrl: with amount) |
| Shift + A | recipe window / bookmark / group strip / heart | Bookmark with recipe (Ctrl: with amounts) / remove the recipe / remove the group / save the recipe tree |
| B | anywhere | Show or hide the bookmarks |
| O | anywhere | Hide or show all of NEI |
| F | anywhere | Focus the search field |
| Up / Down | search field | Earlier searches |
| Page Up / Page Down | item list, bookmarks | Turn the page |
| Left / Right, Up / Down | recipe window | Page, recipe type |
| Backspace | recipe window | Back |
| Shift + D | recipe window | Recipe type info (Handler Info Widget option) |
| Shift + F | item list / recipe window | Save the favorite recipe tree / favorite ingredient or favorite recipe for this result |
| T | group strip / item | Crafting tree / type the name into an AE2 terminal |
| Shift + T | heart | Crafting tree of the favorite |
| Shift + C | bookmark, group strip, recipe | Autocraft (Ctrl: only what is missing) |
| G | crafting chain strip, Cheat Mode | Give the missing ingredients |
| V | bookmark, group strip | Pull items from the open chest (Shift: only missing) |
| Shift + S | over an item | Fill the crafting grid (Ctrl: with amount) |
| Ctrl + L | item, bookmark, group strip | Send to chat (Shift in the recipe window: the recipe) |
| Ctrl + C / X / D | over an item | Copy name / id / tags |
| Shift + Z | anywhere | Item zoom on / off (hold, zoom in and zoom out keys unbound) |
| X | over an item | Enchanting window |
| P | anywhere | Potion window |
| F7 | in the world | Mob spawn overlay |
| Numpad 0 | in the world | Highlight tooltips |
| Alt | over an item, strip, subset or heart | Show its keys |

## For mod makers

NEI Rewrite has NEI's mod API (`codechicken.nei.api.API`).

**NeoForge:** subscribe to `NEIRegisterEvent` on your mod event bus (client side only) and call the API from the listener:

```java
modBus.addListener((NEIRegisterEvent e) -> {
    API.hideItem(new ItemStack(MyItems.SECRET.get()));          // out of the item panel, recipes still show it
    API.setAliases(new ItemStack(MyItems.WRENCH.get()), "spanner");
    API.addSubset("MyMod.Machines", machineStacks);             // Subsets menu, dotted path, any case
    API.registerRecipeSource("mymod", () -> MyRecipes.views()); // list of RecipeView, called on the client thread
    API.addCatalyst("Shaped Crafting", new ItemStack(MyBlocks.WORKBENCH.get())); // shown in that tab's station column
    API.registerRecipeTransferHandler(MyMenu.class, new MyTransferHandler());    // makes the recipe window's "+" work in your screen
});
```

**Fabric:** declare an entrypoint named `notenoughitems` in your `fabric.mod.json` that implements
`codechicken.nei.api.NEIPlugin` and call the same API from `register()`:

```json
"entrypoints": { "notenoughitems": ["com.example.mymod.MyNEIPlugin"] }
```

Also: `hideItem(String searchRule)`, `setOverrideName`, `addItemListEntry` / `addItemVariant`, `addSubset(name, ItemFilter)`.
`registerBookmarkContainerHandler(screenClass, handler)` lets the bookmark panel pull items out of your container screen.
Not carried over from NEI: GUI handlers and overlays, key binds and options, sort options, stack stringify handlers, fluid renderers. A mod that already has a JEI plugin needs nothing: with
JEI installed its recipes show up in NEI, and its JEI recipe transfer handlers make the "+" work in its screens.
There is no separate API jar: compile against the mod jar (`compileOnly`).

## Building

```
./gradlew :neoforge:build      # jar in neoforge/build/libs/
./gradlew :fabric:build        # jar in fabric/build/libs/
./gradlew :neoforge:runClient  # dev client
./gradlew :fabric:runClient
```

One project, three parts: `common/` (all the mod's code, plain Minecraft code with no loader API), and `neoforge/` and
`fabric/` (the glue for each loader; each also compiles `common/`). Needs JDK 25. The Fabric jar needs Fabric API next to it at runtime.

## Source and bug reports

Download: <https://www.curseforge.com/minecraft/mc-mods/unoffical-nei-rewrite>. Source code: <https://github.com/IambdaA/NEI-Rewrite>. Please report problems at <https://github.com/IambdaA/NEI-Rewrite/issues>, together with your `latest.log`.

## Credits and original work

NEI Rewrite is a **derivative work**. It is based on **NotEnoughItems (NEI)**; the credit for the design, the features and the look
belongs to the original authors:

- **NotEnoughItems** by **Chicken-Bones** (original author): <https://github.com/Chicken-Bones/NotEnoughItems>
- **NotEnoughItems, GregTech: New Horizons fork** by **mitchej123** and the **GTNH team**, the version this mod follows: <https://github.com/GTNewHorizons/NotEnoughItems>
- **JustEnoughItems (JEI)** by **mezz** (MIT License, Copyright (c) 2014-2015 mezz): parts of the search and panel design are
  inspired by it, and NEI Rewrite reads other mods' recipes through JEI's plugin API: <https://github.com/mezz/JustEnoughItems>

The GUI textures in `assets/notenoughitems/textures/gui` come unchanged from the GTNH NotEnoughItems repository.

The button styles of the setting UI Style are drawn in code with plain fills and include no texture of another project:
**NTNH-Approximate** approximates the slate buttons of the **NTNH** modpack (<https://github.com/NTNewHorizons/NTNH>), and
**GTNH-Modernity** approximates the dark UI of **Modernity-GTNH** (<https://github.com/ModernityGTNH/Modernity-GTNH>). Both are
made from colours seen in screenshots; neither project's files are included.
NEI Rewrite is made by **Iambda** and is not affiliated with or endorsed by the original authors or the GTNH team.

## How NEI Rewrite differs from the original

- **A new implementation, not a copy of the old mod.** The original is a Forge mod for Minecraft 1.7.10. NEI Rewrite is new Java
  code written for modern Minecraft on NeoForge and Fabric. It follows NEI's behaviour, look and search rules.
- **Recipes come from the game and from JEI.** The original reads recipes through its own recipe handler system (and GregTech: New
  Horizons' machine handlers). NEI Rewrite reads vanilla recipes from the game's recipe system and the recipes of other mods from
  JEI's plugins, so modded machines need JEI installed. There are no GregTech / GTNH-specific recipe handlers.
- **Tags instead of the ore dictionary.** The `$` search matches item tags.
- **Own settings and keys.** Options are kept in a config file and edited in the wrench button's settings screen; hotkeys are normal
  Minecraft key binds. NEI's 1.7.10 config files are not read.
- **A smaller mod API.** Same idea as NEI's `API` class, but mods register through a NeoForge event (`NEIRegisterEvent`) or a Fabric entrypoint (`NEIPlugin`). NEI's 1.7.10 GUI handler and overlay APIs
  are not carried over.
- **Not everything is ported.** See `PORTING_NOTES.md` for what is missing.

## License

LGPL-3.0-or-later, the same license as the original NotEnoughItems (see `LICENSE.txt`). This is a modified version of the original.

Minecraft names come from the official Mojang mappings.
