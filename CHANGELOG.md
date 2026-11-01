# Changelog

## 1.1.2 (1.21.1, NeoForge and Fabric)

The 26.x JEI bridge and fluid work, brought to 1.21.1. JEI 19 is required (NEI does not work without it: JEI's own panels would
overlap NEI's).

- JEI bridge: recipes of modded machines, recipes of fluid display items and the `+` in modded crafting screens come from JEI.
  JEI 19 has no switch to turn its GUI off, so NEI turns JEI's ingredient list and bookmark overlay off through JEI's own toggle
  state (the reflection is guarded: if JEI changes, NEI logs it and carries on)
- JEI fluid ingredients (NeoForge fluid stacks, Fabric fluid variants) become NEI's fluid display items, with their amount
- Mods that show every fluid as one item with a component (NTM Continuum's `hbm:fluid_icon`): each fluid keeps its own id, so R/U
  on a fluid, bookmarks and the item list tell them apart
- Fluid amounts in recipes, bookmarks, bookmark groups and the crafting tree (mB below 1000, buckets from 1000). New option "Fluid
  amounts in mB only" (off) keeps millibuckets
- New key T (not in NEI): types the name of the item or fluid under the mouse into the search box of an AE2 terminal
- Recipe result shown in the heart tooltip, bookmark rows follow the lead result of a recipe, recipe highlight and amount labels
  drawn in JEI's own layout
- JEI's config, bookmark and history buttons no longer show behind NEI's (JEI 19 draws them even with its overlay off)
- History keeps the fluid: a lookup of an NTM fluid icon is recorded with its fluid instead of the plain item (named "None")
- Fabric: NEI's key and mouse listeners run before JEI's, so R / U over an item open NEI's recipes; NeoForge: highest priority
- Fabric: the creative inventory shows NEI again (the per-screen init counter was global: the survival screen's init event arrives after the creative screen's and declared it dead)
- Fabric: keys NEI or JEI bound to the same key as a game control (A = walk left) are given back to the game; checked again once a second
- NeoForge 21.1.235 is the minimum tested NeoForge; the build uses `neo_version=21.1.235`, any 21.1.x with the same API runs

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

- Everything of the 26.x version except the JEI bridge (added in 1.1.2)
- Fabric: mod API through a `notenoughitems` entrypoint (`NEIPlugin`) instead of a NeoForge event
