# Lunar Client Mod Installer

A GUI installer that bundles and deploys Java agent–based mods for Lunar Client 1.7 and 1.8.

## Packaging the JAR with mods

The build bundles whatever JARs are in the `libs/` directory directly into the fat JAR as classpath resources.

### 1. Place your mod JARs in `libs/`

```
libs/
  mod-loader.jar     ← required: the Java agent that loads mods at runtime
  hitsound-mod.jar   ← mod copied to agentmods on install
  timerr.jar         ← mod copied to agentmods on install
```

Replace or add any JAR here. The file names matter — they must match what `ModInstaller.java` references in `extractResource()` and the log strings.

### 2. Build the fat JAR

```bash
./gradlew jar
```

This produces `build/libs/mod-installer.jar`. The Gradle build does two things:

- `processResources` copies every JAR from `libs/` into the output resources root, making them available as `/filename.jar` on the classpath.
- The `jar` task merges all runtime dependencies (just Gson) and those resources into a single executable fat JAR.

### 3. Verify bundling

To confirm a mod is inside the built JAR:

```bash
jar tf build/libs/mod-installer.jar | grep "\.jar"
```

You should see each file from `libs/` listed at the root of the archive.

### 4. Run the installer

```bash
java -Dsun.java2d.d3d=false -jar build/libs/mod-installer.jar
```

Or on Windows, use `run.bat` after building.

## Adding a new mod

1. Drop the `.jar` into `libs/`.
2. In `ModInstaller.java`, add an `extractResource("/your-mod.jar", AGENTMODS_DIR.resolve("your-mod.jar"))` call inside `doInstall()`, and a matching `deleteIfExists` call inside `doUninstall()`.
3. Rebuild with `./gradlew jar`.
