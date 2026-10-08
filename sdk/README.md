# HOWL SDK: first mod

H.O.W.L. means **Howling Open Works Loader**. This early SDK targets exactly
**Minecraft Java 26.4 Snapshot 3**. CodaLauncher remains the player launcher.

The SDK includes a compile-only `lib/howl-api.jar`, public API source,
an editable Hello Coda mod and build scripts for Windows, Linux and macOS.
The game supplies the API at runtime; do not bundle it into your mod JAR.

1. Install a JDK 21 or newer for compiling. Playing Snapshot 3 requires Java 25+.
2. Extract the SDK into its own folder.
3. Edit `src/dev/howlingwhispers/examples/hellocoda/HelloCodaMod.java`.
4. Build with `bash build-mod.sh` or `./build-mod.ps1` in PowerShell.
5. Copy `out/hello-coda.jar` to the HW game's `mods` folder. Remove any previous
   Hello Coda JAR first: two JARs with the same `hello_coda` ID are rejected.
6. Open CodaLauncher and play through the official Minecraft Launcher, or
   enable Settings → Local Test Mode for local development. Check the Logs
   for `Pawprint confirmed` and use **Copy All Logs** when reporting a problem.

The managed game directory is `<ApplicationData>/.howlingshispers/minecraft`.
Windows normally uses `%APPDATA%`, Linux `~/.config`, and macOS
`~/Library/Application Support`. CodaLauncher provides an Open files control.

For your own mod, change the Java package/class and the metadata together:
`resources/coda.mod.json` contains the mod ID, name, version, entrypoint,
exact Minecraft target and required mod IDs. Give every mod its own ID.
Build scripts initially output `hello-coda.jar`; rename the JAR for distribution.

`HOWL-API.md` documents the supported API and current limits. The existing
`dev.howlingwhispers.codaloader.api` Java packages and `coda.mod.json` name are
retained for compatibility. Forge/Fabric mods cannot be loaded unchanged.

For a loader-only smoke test, copy the built mod into a scratch `mods` directory
and run `java -jar /path/to/CodaLoader.jar --loader-only /path/to/scratch`.
This verifies initialization without starting Minecraft; it does not test
gameplay hooks. Avoid using a real world directory for this smoke test.
