# NEI Rewrite

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
button's settings window.

Not affiliated with the GTNH team. See `PORTING_NOTES.md` for the porting log and what is not ported.

## Requirements

- Minecraft 26.2 with NeoForge 26.2.0.90 or newer, or Fabric Loader 0.19+ with Fabric API
- [JEI](https://modrinth.com/mod/jei) 30 or newer: **needed to see the recipes of modded machines** and for the `+` in modded
  crafting screens (optional, vanilla recipes work without it)
- Works on the client alone. Install it on the **server too** if you want recipe lookup on a multiplayer server: the
  server sends the recipes (without it you only see recipes the server shares with the recipe book).

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

## Credits and original work

NEI Rewrite is a **derivative work**. It is based on **NotEnoughItems (NEI)**; the credit for the design, the features and the look
belongs to the original authors:

- **NotEnoughItems** by **Chicken-Bones** (original author): <https://github.com/Chicken-Bones/NotEnoughItems>
- **NotEnoughItems, GregTech: New Horizons fork** by **mitchej123** and the **GTNH team**, the version this mod follows: <https://github.com/GTNewHorizons/NotEnoughItems>
- **JustEnoughItems (JEI)** by **mezz** (MIT License, Copyright (c) 2014-2015 mezz): parts of the search and panel design are
  inspired by it, and NEI Rewrite reads other mods' recipes through JEI's plugin API: <https://github.com/mezz/JustEnoughItems>

The GUI textures in `assets/notenoughitems/textures/gui` come unchanged from the GTNH NotEnoughItems repository.
NEI Rewrite is made by **Iambda** and is not affiliated with or endorsed by the original authors or the GTNH team.

## How NEI Rewrite differs from the original

- **A new implementation, not a copy of the old mod.** The original is a Forge mod for Minecraft 1.7.10. NEI Rewrite is new Java
  code written for modern Minecraft on NeoForge and Fabric. It follows NEI's behaviour, look and search rules.
- **Recipes come from the game and from JEI.** The original reads recipes through its own recipe handler system (and GregTech: New
  Horizons' machine handlers). NEI Rewrite reads vanilla recipes from the game's recipe system and the recipes of other mods from
  JEI's plugins, so modded machines need JEI installed. There are no GregTech / GTNH-specific recipe handlers.
- **Tags instead of the ore dictionary.** The `$` search matches item tags.
- **Own settings and keys.** Options are kept in a config file and edited in the wrench button's settings window; hotkeys are normal
  Minecraft key binds. NEI's 1.7.10 config files are not read.
- **A smaller mod API.** Same idea as NEI's `API` class, but mods register through a NeoForge event (`NEIRegisterEvent`) or a Fabric entrypoint (`NEIPlugin`). NEI's 1.7.10 GUI handler and overlay APIs
  are not carried over.
- **Not everything is ported.** See `PORTING_NOTES.md` for what is missing.

## License

LGPL-3.0-or-later, the same license as the original NotEnoughItems (see `LICENSE.txt`). This is a modified version of the original.

Minecraft names come from the official Mojang mappings.
