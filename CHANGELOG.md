# Changelog

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
