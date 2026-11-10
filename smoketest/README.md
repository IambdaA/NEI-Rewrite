# Test driver (development only)

`common/src/smoketest` plus a small entry class per loader (`*/src/smoketest`) build NEI's test driver: its own mod jar,
never part of NEI's jar and never meant for a normal game (it creates a world and drives the client). It is for testing
NEI in a real game with other mods installed, in a **test** instance.

1. Build NEI (`./gradlew build`) and the driver (`./gradlew :neoforge:smoketestJar :fabric:smoketestJar`).
2. Put both jars into the test instance's `mods` folder.
3. Write the commands to run into `<game dir>/nei-smoketest/script.txt` and start the game. More commands can be appended to
   `nei-smoketest/commands.txt` while it runs; results go to `nei-smoketest/out.log` (and the game log, prefix
   `[NEI-SMOKE]`), screenshots to `nei-smoketest/shots/`. End with `quit`.

Commands (coordinates are GUI-scaled): `world` (create or open the world `neitest`: creative, cheats on), `wait <ticks>`,
`inv`, `close`, `screen`, `carried`, `move x y`, `click x y [left|right|middle] [shift|ctrl|alt]`, `drag x1 y1 x2 y2`,
`scroll x y amount`, `key <name> [mods]`, `press` / `release <key>`, `type <text>`, `cmd <command>`, `use` (right-click
the block the player looks at), `shot <name>`, `areas`, `fakewidget x y w h | off`, `log <text>`, `quit`. Independent
of the window size and of the instance's NEI settings: `search <text>`, `finditem <name>`, `clickhere [right]`,
`clickinv <player slot> [right]`, `cheatmode <0|1|2>`.

Screenshots are in window pixels (GUI coordinate = pixel / GUI scale). Don't touch the mouse while a test runs: the driver
puts its mouse position back before keys and screenshots, but real mouse movement still gets in between.

## Checks (`full.txt`)

`smoketest/full.txt` tests every feature with checks that pass or fail by themselves; the driver ends with
`QUIT errors=N passes=N fails=N` and every failed check is a `FAIL ...` line in `out.log` (`run.sh` lists them).

- `expect <value> <op> <expected>`: ops `==`, `!=`, `contains`, `!contains`, `>`, `<`, `>=`, `<=`. A value is a game probe
  (`carried`, `carriedcount`, `inv:<item id>`, `screen`, `tooltip` (what NEI drew as its tooltip last frame), `chat` (chat
  lines since `chatmark`), `clipboard`, `raining`, `gamemode`, `health`, `daytime`, `file:<path>`) or a path into NEI's
  classes: `NEIClientEvents.overlay.page`, `Bookmarks.grid().items.get(0).amount`, `NEIConfig.ZOOM_ENABLED.get()`.
- `call <path>` runs a method (test setup, e.g. `call NEIConfig.QUANTITY.set(5)`); `section <name>` names the checks.
- Positions from NEI itself: `finditem [ITEMS|BOOKMARKS|HISTORY|CRAFTABLES] <name>`, `clickhere [right]`,
  `clickinv` / `moveinv <player slot>`, `clickpath` / `movepath <x path> <y path> <dx> <dy>` (e.g. the recipe window's
  `plusHits.get(0)`), `clickbottom <x> <distance from the bottom>` (wrench, book, presets), `toolbar <name> [n]`,
  `scroll here <amount>`.
- Clicks move first and press on the next tick, letter keys also type their character (unless Ctrl / Alt is held), and
  `close` closes a screen as Esc does, so the game sees what a player's hand would do.

## Recipe mods (sections 24 and up)

These sections need a mod's recipes and skip themselves where the mod is missing, so `full.txt` still runs unchanged in
every instance.

- `requires <item id> [text | !text]` skips the rest of the section unless that item exists (and its mod's name does, or
  with `!` does not, contain the text): `requires hbm:liquidator_helmet continuum`, `requires gtceu:lv_cutter`.
- Recipe window: `tab <text>` opens the tab whose title contains the text. `findslot [N:]<name>` moves onto the (Nth)
  recipe slot showing that item, also in recipes drawn by their JEI layout, and turns pages when this one has fewer. `recipes <item id>` logs an item's
  recipes. `favor <item id> <tab text> [ingredient text]` makes the first recipe of that tab the item's favorite.
- Waiting and scrolling: `waituntil <path> <text> <max checks>` waits until a probe contains the text. `scrolluntil <path>
  <text> <max>` scrolls where the mouse is until it does (e.g. the favorite wheel's choice).
- Crafting chains: `givechain <*text|@name|id> [n]` gives an ingredient of the newest chain. `givemissing` gives everything
  its Shift view lists as missing.
- `moveby <dx> <dy>` moves the mouse from where it is (e.g. to the bookmark after the one `finditem` found).
- `unless <item id>` skips the rest of the section where that item exists (e.g. a vanilla recipe a mod changes).
- `bookmarkrecipes <n> [mod id]` bookmarks the first n items (of that mod) that have a recipe, each with its first recipe
  (for measuring the bookmark panel). Probes: `fps` (frames in the last second), `hotkeys` (the last Alt help box drawn:
  `title|key: text|...`).
- Logging: `probe <value>` logs a probe's value; `listitems [max]` logs the item list's entries.
- Probes: `standin:<id>` is the item a fluid entry is drawn as (NTM - Continuum's droplet) or `none`. The newest crafting
  chain's tooltip totals are `chainin:<id>` (an ingredient; for a tool its durability use), `chainout:<id>`,
  `chaincraft:` / `chainmissing:` / `chainhave:<id>` (the Shift view), `chaintool:<id>`, `chainuses:<id>` (how many crafts
  use a tool) and `chaingroups`. Ids match a part with `*text` and an item name with `@name`.
