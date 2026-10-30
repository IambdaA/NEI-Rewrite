# Changelog

## 1.1.0 (1.21.1, NeoForge and Fabric)

Fixes and small additions.

- Fluids: the fluid display items no longer depend on the item registry. They now keep their own ids, so bookmarks, history,
  recipe lookups and the item panel keep working on a server whose registry sync drops them. A fluid that cannot be drawn is
  reported once in the log
- Favorites: Shift + F (the favorite-item key) over a result in the recipe window favorites that recipe for that result, so a
  recipe whose result is not its first one (oil refinery: unsaturated hydrocarbons) can be used by recipe trees. The item tooltip of
  a recipe result shows this key just above the mod name
- New option "Single favorite counts for all its results" (off): a recipe tree uses the only favorited recipe that makes an ingredient,
  even when it was favorited under another of its results. Several candidates: nothing is guessed
- New option "Choose favorite result with the mouse wheel" (off): NEI's way, the wheel over the heart changes which result it favorites;
  the chosen result is greyed with a heart on it
- Item list log line: how many fluid display items exist and how many the item registry still holds

## 1.0.0 (1.21.1, NeoForge and Fabric)

First release for 1.21.1. One code base for both loaders (`common/`) with a small glue module for each.

- Everything of the 26.x version except the JEI bridge (recipes of modded machines, fluid display items' recipes, the `+` in
  modded crafting screens): that comes in a later stage
- Fabric: mod API through a `notenoughitems` entrypoint (`NEIPlugin`) instead of a NeoForge event
