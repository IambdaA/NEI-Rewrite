# ⚠️ MODDED MACHINE RECIPES REQUIRE JEI ⚠️

> **Want recipes for AE2, Create, Mekanism or any other mod's machines? Install [Just Enough Items (JEI)](https://modrinth.com/mod/jei) too.**
> NEI Rewrite shows vanilla recipes on its own and reads every other mod's recipes from JEI's plugins.
> **Without JEI, modded machine recipes will not show** (and the `+` button will not work in modded crafting screens such as the AE2 crafting terminal).
> JEI's own screens are switched off, so you only ever see NEI.

---

## NEI Rewrite

An **unofficial rewrite of the GTNH fork of NotEnoughItems** for Minecraft 1.21.1 on NeoForge and Fabric. It looks and works like NEI in GregTech: New Horizons.

*Based on [NotEnoughItems (GregTech: New Horizons fork)](https://github.com/GTNewHorizons/NotEnoughItems) by Chicken-Bones, mitchej123 and the GTNH team. See **Credits & original work** below.*

### Features

- **Item panel** with NEI's search syntax: `@mod`, `#tooltip`, `$tag`, `&id`, `%subset`, `-not`, `"quotes"`, regex and expression mode
- **Recipe and usage lookup**: click an item, or press `R` / `U`. Recipe window with tabs, station column and recipe search
- **Recipe transfer** (the `+` button) into crafting grids, missing ingredients marked red, **autocrafting**, pulling items from containers
- **Bookmarks** with pages, groups, todo lists, whole-recipe bookmarks and crafting chains
- **Crafting tree window** with stats, chat links and tree screenshots
- **Item subsets**, hidden items, history and craftables panels, item zoom, presets
- **Cheat tools**: toolbar (time, weather, heal, game mode), delete mode, item magnet, extended creative inventory, mob spawner items, mob spawn overlay (`F7`), enchantment and potion creator
- **With JEI**: recipes of every mod that has a JEI plugin (fluids included) and the `+` in modded crafting screens
- **AE2 terminal search sync**: type in the terminal and NEI's search follows, and the other way round
- **Mod API** for other mods

Hold **Alt** over an item to see its hotkeys. All keys are under *Options > Controls > NotEnoughItems*; the wrench button opens the settings window.

### Requirements

- Minecraft **1.21.1** with **NeoForge 21.1.x**, or **Fabric Loader 0.16+ with Fabric API**
- [JEI](https://modrinth.com/mod/jei) for 1.21.1 to see **modded machine recipes** (optional: vanilla recipes work without it)

### Multiplayer

Works on the client alone. Install it on the **server too** to get recipe lookup for everything the server knows, and for the delete mode, item magnet and extended creative inventory (they need the mod on the server and creative mode or operator rights). JEI's recipe transfer in modded screens also needs the mod's JEI part on the server.

### Status

Beta. Not everything from NEI is ported yet (for example giving items with cheats off). Please report bugs together with your `latest.log`.

### Credits & original work

NEI Rewrite is a **derivative work**. It is based on **NotEnoughItems (NEI)**, and the credit for the design, the features and the look belongs to the original authors:

- **NotEnoughItems** by **Chicken-Bones** (original author): <https://github.com/Chicken-Bones/NotEnoughItems>
- **NotEnoughItems, GregTech: New Horizons fork** by **mitchej123** and the **GTNH team**, the version this mod follows: <https://github.com/GTNewHorizons/NotEnoughItems>
- **JustEnoughItems (JEI)** by **mezz** (MIT license): parts of the search and panel design are inspired by it, and NEI Rewrite reads other mods' recipes through JEI's plugin API: <https://github.com/mezz/JustEnoughItems>

The original NEI is open source under **LGPL-3.0-or-later**. NEI Rewrite is released under the same license and is a modified, rewritten version of it. The GUI textures come unchanged from the GTNH repository. NEI Rewrite is made by **Iambda** and is **not affiliated with or endorsed by** the original authors or the GTNH team. Please do not report problems of this mod to them.

### How NEI Rewrite differs from the original

- **A new implementation, not a copy of the old mod.** The original is a Forge mod for Minecraft 1.7.10. NEI Rewrite is new Java code written for modern Minecraft on NeoForge and Fabric. It follows NEI's behaviour, look and search rules, and the GUI textures come unchanged from the GTNH repository.
- **Recipes come from the game and from JEI.** The original reads recipes through its own recipe handler system (and GregTech: New Horizons' machine handlers). NEI Rewrite reads vanilla recipes from the game's recipe system and the recipes of other mods from JEI's plugins, so modded machines need JEI installed. There are no GregTech / GTNH-specific recipe handlers.
- **Tags instead of the ore dictionary.** The `$` search matches item tags, which replaced the ore dictionary in modern Minecraft.
- **Own settings and keys.** Options are kept in a config file and edited in the wrench button's settings window; hotkeys are normal Minecraft key binds. NEI's 1.7.10 config files are not read.
- **A smaller mod API.** The API class is the same idea as NEI's, but mods register through a NeoForge event (`NEIRegisterEvent`) or a Fabric entrypoint (`NEIPlugin`). NEI's 1.7.10 GUI handler and overlay APIs are not carried over.
- **Not everything is ported.** A few NEI features are still missing (for example giving items with cheats off). See the changelog.
