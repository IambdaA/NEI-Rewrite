# Publishing checklist

The store texts are in `store/` (`modrinth.md`, `curseforge.html`, `summary.txt`); the changelog is `CHANGELOG.md`.

## Before the first upload

1. `./gradlew build`, then run `neoforge/build/libs/notenoughitems-neoforge-26.2-1.1.2.jar` in a clean 26.2 NeoForge instance and `fabric/build/libs/notenoughitems-fabric-26.2-1.1.2.jar` (with Fabric API) in a Fabric one.
2. Start a **dedicated server** with the jar and join it: with the mod on both sides, on the client only and on the server only.
3. Make the GitHub repository public, then add `issueTrackerURL` and `displayURL` to `neoforge/src/main/templates/META-INF/neoforge.mods.toml`
   and a source link to the store texts.
4. `jei_version` in `gradle.properties` is pinned (30.47.0.240); raise it when you test against a newer JEI.
5. Upload each jar as **Beta** (one NeoForge file, one Fabric file).

## Project settings

| Field | Value |
|---|---|
| Project name | NEI Rewrite |
| Slug (not `nei`, Modrinth already has the original NEI under it) | `nei-rewrite` |
| License | LGPL-3.0-or-later |
| Environment | client required; server optional |
| Loader / game version | NeoForge and Fabric / 26.2 |
| Dependencies | JEI: optional |
| Icon | `common/src/main/resources/logo.png` |
| Summary | `store/summary.txt` |
