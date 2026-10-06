# CodaLoader

CodaLoader is a from-scratch Minecraft mod loader and modding API project for **Minecraft Java Edition 26.4 Snapshot 3**.

This foundation intentionally does **not** depend on Fabric, Forge, NeoForge, Quilt, or another mod loader.
It begins with the boring-but-important loader plumbing before touching Minecraft bytecode.

## Foundation milestone

`0.0.1-foundation` currently provides:

- `coda.mod.json` metadata embedded in each mod JAR
- Mod JAR discovery from a `mods/` directory
- Metadata validation
- Duplicate mod-id rejection
- Required dependency validation
- Topological dependency ordering and cycle detection
- One classloader per mod
- `CodaMod` initialization entrypoint
- A small immutable `CodaContext`
- A pinned target constant for Minecraft `26.4-snapshot-3`
- A zero-external-dependency JSON parser used only for loader metadata
- A runnable `hello-coda` example mod

## Intentionally not here yet

Minecraft itself is **not launched or transformed yet**. That is the next layer.
The foundation is tested independently first so failures in discovery/classloading are not confused with Minecraft bootstrap failures.


## Downloads

Compiled builds are published on the [GitHub Releases](https://github.com/HowlingWhispers/HW-CodaLoader/releases) page.

The current `v0.0.1-foundation` build is a **prerelease**: its executable JAR proves CodaLoader's standalone mod-loading foundation, but it does not launch Minecraft yet.

For Windows, download `Launch-CodaLoader.bat` and the CodaLoader JAR into the same folder, then double-click the BAT file. It creates `run\mods` automatically, finds the versioned CodaLoader JAR beside itself, and launches it through Java.

Future Minecraft-capable builds and CodaLoader mods will use Releases as the binary source of truth, with HW-Landing planned as the friendly Downloads / Mods frontend.

## Requirements

- Java 21+ for the standalone foundation demo
- Java 25+ when CodaLoader is attached to Minecraft 26.4 Snapshot 3
- `javac` and `jar` on PATH

## Run the demo

Linux/macOS:

```bash
./scripts/build-demo.sh
```

Windows PowerShell:

```powershell
./scripts/build-demo.ps1
```

Expected ending:

```text
[CodaLoader] Target Minecraft: 26.4-snapshot-3
[CodaLoader] Found 1 mod(s).
[CodaLoader] Loading hello_coda 0.0.1
[HelloCoda] Pawprint confirmed. CodaLoader can load me. 🐾
[CodaLoader] Ready. 1 mod(s) initialized.
```

## Mod metadata

A mod JAR places `coda.mod.json` at its root:

```json
{
  "schema": 1,
  "id": "hello_coda",
  "name": "Hello Coda",
  "version": "0.0.1",
  "entrypoint": "dev.howlingwhispers.examples.hellocoda.HelloCodaMod",
  "minecraft": "26.4-snapshot-3",
  "depends": []
}
```

## Design rule

Minecraft-facing hooks will live above the loader core. The core must remain testable without starting Minecraft.

## First game target

The first pinned game build is **Minecraft Java 26.4 Snapshot 3** (`26.4-snapshot-3`). The loader core stays deliberately independent of Minecraft internals; the next milestone is the Minecraft bootstrap/transform layer for that exact build.
