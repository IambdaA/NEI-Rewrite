# Test driver (development only)

`common/src/smoketest` plus a small entry class per loader (`*/src/smoketest`) build NEI's test driver: its own mod jar,
never part of NEI's jar and never meant for a normal game (it creates a world and drives the client). It is for testing
NEI in a real game with other mods installed, in a **test** instance.

1. Build NEI (`./gradlew build`) and the driver (`./gradlew :forge:reobfSmoketestJar :fabric:remapSmoketestJar`).
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
