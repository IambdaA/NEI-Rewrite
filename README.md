# NEI Rewrite

> **IMPORTANT: recipes of modded machines need [JEI](https://modrinth.com/mod/jei) installed.**
> NEI Rewrite shows vanilla recipes by itself. For machines from other mods (AE2, Create, Mekanism ...) it reads the
> recipes from JEI's plugins, so install JEI next to it. JEI's own screens are switched off; you only see NEI.

An **unofficial** rewrite of the [GTNH fork of NotEnoughItems](https://github.com/GTNewHorizons/NotEnoughItems) for
Minecraft 26.2 on NeoForge, by Iambda. It follows NEI's design and rules, but the code is new. It aims to look and work
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
button's settings window.

Not affiliated with the GTNH team. See `PORTING_NOTES.md` for the porting log and what is not ported.

## Requirements

- Minecraft 26.2, NeoForge 26.2.0.90 or newer
- [JEI](https://modrinth.com/mod/jei) 30 or newer: **needed to see the recipes of modded machines** and for the `+` in modded
  crafting screens (optional, vanilla recipes work without it)
- Works on the client alone. Install it on the **server too** if you want recipe lookup on a multiplayer server: the
  server sends the recipes (without it you only see recipes the server shares with the recipe book).

## For mod makers

NEI Rewrite has NEI's mod API (`codechicken.nei.api.API`). Subscribe to `NEIRegisterEvent` on your mod event bus (client
side only) and call the API from the listener:

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

Also: `hideItem(String searchRule)`, `setOverrideName`, `addItemListEntry` / `addItemVariant`, `addSubset(name, ItemFilter)`.
`registerBookmarkContainerHandler(screenClass, handler)` lets the bookmark panel pull items out of your container screen.
Not carried over from NEI: GUI handlers and overlays, key binds and options, sort options, stack stringify handlers, fluid renderers. A mod that already has a JEI plugin needs nothing: with
JEI installed its recipes show up in NEI, and its JEI recipe transfer handlers make the "+" work in its screens.
There is no separate API jar: compile against the mod jar (`compileOnly`).

## Building

```
./gradlew build
```

The jar is in `build/libs/`. Needs JDK 25.

## License and credits

LGPL-3.0-or-later, the same license as NotEnoughItems (see `LICENSE.txt`). This is a modified version of the original.

- NEI Rewrite by Iambda
- Original NotEnoughItems by Chicken-Bones, GTNH fork by mitchej123 and the
  [GTNH team](https://github.com/GTNewHorizons/NotEnoughItems)
- Parts of the search and panel design are inspired by [JustEnoughItems](https://github.com/mezz/JustEnoughItems) by mezz
  (MIT License, Copyright (c) 2014-2015 mezz)
- The GUI textures in `assets/notenoughitems/textures/gui` come unchanged from the GTNH NotEnoughItems repository

Minecraft names come from the official Mojang mappings, see
[Mojang.md](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md).
