# Publishing checklist (1.21.1)

The store texts are in `store/` (`modrinth.md`, `curseforge.html`, `summary.txt`); the changelog is `CHANGELOG.md`.

## Before the first upload

1. `./gradlew :neoforge:build :fabric:build`, then run each jar in a clean 1.21.1 instance
   (`neoforge/build/libs/notenoughitems-neoforge-1.21.1-1.3.2.jar`, `fabric/build/libs/notenoughitems-fabric-1.21.1-1.3.2.jar`;
   the Fabric one with Fabric API).
2. Start a **dedicated server** with each jar and join it: mod on both sides, on the client only, on the server only.
3. Repository: https://github.com/IambdaA/NEI-Rewrite (issue tracker and project links are in the mod metadata, README and store texts).
4. Upload as **Beta**: the NeoForge jar as NeoForge 1.21.1, the Fabric jar as Fabric 1.21.1 (one project page per site, two files).

## Project settings

Same as the other versions (slug `nei-rewrite`, LGPL-3.0-or-later, client required, server optional). The JEI dependency is not
listed yet (the bridge is not in this version).
