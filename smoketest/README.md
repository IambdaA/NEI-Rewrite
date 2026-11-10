# Smoke tests (Fabric dev client, 26.2)

`smoketest/run.sh [script]` starts the Fabric dev client with a test-only driver mod (`fabric/src/smoketest`, never in the
release jar), runs the script, then checks the log. It prints the driver output, any problems in the log, crash reports and
the screenshots, and ends with `RESULT: PASS`, `FAIL` or `TIMEOUT` (exit code 0 / 1 / 2).

- Run dir: `fabric/run-smoketest` (its own world `neitest`, creative, cheats on; separate from `fabric/run`).
- Window 1280x720 at GUI scale 2, so script coordinates are 640x360 GUI coordinates.
- Screenshots: `fabric/run-smoketest/nei-smoketest/shots/<name>.png`.
- While it runs, append lines to `fabric/run-smoketest/nei-smoketest/commands.txt` to drive it live.
- Don't touch the mouse over the window during a run; the driver moves the real cursor.

Scripts: `basic.txt` (search, R), `v130.txt` (the 1.3.0 features: wrench modes, toolbar avoiding other mods' buttons,
settings pages, every data dump in every format, scroll bar dragging, effect boxes, Alt box, + closing the window, toasts).
NEI's config is reset before each run (`SMOKE_KEEP_CONFIG=1` keeps it); the world is kept, scripts set gamemode etc.

Commands: `world`, `wait <ticks>`, `inv`, `close`, `screen`, `move x y`, `click x y [left|right|middle] [shift|ctrl|alt]`,
`drag x1 y1 x2 y2`, `mousedown x y`, `mouseup x y`, `scroll x y n`, `key <name> [shift|ctrl|alt]`, `press <key>` /
`release <key>` (hold Shift / Ctrl / Alt across steps), `type <text>`, `cmd <command>`, `fakewidget x y w h` / `fakewidget off`
(a button on every container screen, standing in for another mod's sidebar; reopen the screen), `areas` (the areas NEI's
panels keep clear of), `shot <name>`, `log <text>`, `quit`. `#` starts a comment. One command runs per tick.

NEI's recipe view is an overlay, so `screen` still reports the container screen while it is open: check it with `shot`.

## Real games (every version and loader)

Every tree also builds the driver as its own small mod, for testing in a real game with other mods installed (never put
it in a normal game: it creates a world and drives the client). Build it with `./gradlew :neoforge:smoketestJar
:fabric:smoketestJar` (26.x), `:neoforge:smoketestJar :fabric:remapSmoketestJar` (1.21.1) or `:forge:reobfSmoketestJar
:fabric:remapSmoketestJar` (1.20.1), and put it next to NEI in a **test** instance's `mods` folder. It runs
`<game dir>/nei-smoketest/script.txt`, then lines appended to `nei-smoketest/commands.txt`; results go to
`nei-smoketest/out.log`, screenshots to `nei-smoketest/shots/`.

The loader-neutral part is `common/src/smoketest/.../SmokeCore.java`; private game methods are found by their Mojang, SRG
or intermediary names. `search`, `finditem <name>`, `clickhere`, `clickinv <slot>` and `cheatmode <0|1|2>` make a script
independent of the window size and of the instance's NEI settings: `fixes-all-versions.txt` runs unchanged everywhere.

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
