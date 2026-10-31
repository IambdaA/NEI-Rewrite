# Changelog

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
