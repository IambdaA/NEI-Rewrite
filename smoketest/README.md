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
