# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

```bash
./gradlew jar          # produces build/libs/mod-installer.jar (fat JAR with bundled mods)
./gradlew build        # compile + assemble
```

The project targets Java 8. There are no tests.

## Run

```bash
# Windows convenience script (after building):
run.bat

# Cross-platform:
java -Dsun.java2d.d3d=false -jar build/libs/mod-installer.jar
```

The `-Dsun.java2d.d3d=false` flag is required — the app re-launches itself with this flag if it is missing (see `main()`).

## Architecture

Single-class Swing GUI application (`ModInstaller.java`) that installs/uninstalls Java agent–based mods for Lunar Client.

**Install flow:**
1. Extracts `mod-loader.jar` (a Java agent) from the fat JAR into `%APPDATA%\.minecraft\agentloader\`.
2. Patches the Lunar Client `profile.json` for 1.8 and/or 1.7 (user-selectable) by adding a `-javaagent:` JVM argument pointing to `mod-loader.jar`. A `.bak` backup is taken before the first patch.
3. Sets `advancedMode: true` in `~/.lunarclient/settings/launcher.json` so Lunar Client respects the custom JVM args.
4. Creates `%APPDATA%\.minecraft\agentmods\` and extracts `hitsound-mod.jar` and `timerr.jar` into it. The mod loader reads from that directory at runtime.

**Uninstall flow:** Restores the `.bak` profile backups, then deletes the mod JARs and `mod-loader.jar`. The Uninstall button is only enabled when at least one `.bak` file exists.

**Bundled artifacts** (`libs/` → packaged into the fat JAR via `processResources`):
- `mod-loader.jar` — Java agent that loads mods from the agentmods directory
- `hitsound-mod.jar` — block-hit sound mod
- `timerr.jar` — MCC timer overlay mod

Resources are loaded at runtime via `Class.getResourceAsStream("/filename.jar")`.

**Key path constants** (all resolved at static-init time):
- `AGENTLOADER_DIR` — `%APPDATA%\.minecraft\agentloader\`
- `AGENTMODS_DIR` — `%APPDATA%\.minecraft\agentmods\`
- `PROFILE_18/17` — `~/.lunarclient/profiles/lunar/{version}/profile.json`
- `LAUNCHER_JSON` — `~/.lunarclient/settings/launcher.json`

On non-Windows systems, `APPDATA` falls back to `~/AppData/Roaming` (the app is Windows-targeted despite the fallback).
