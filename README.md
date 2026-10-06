# CodaLoader

CodaLoader is a from-scratch Minecraft mod loader and bootstrap project targeting **Minecraft Java Edition 26.4 Snapshot 3**.

It does **not** depend on Fabric, Forge, NeoForge, Quilt, or another mod loader.

## Current milestone: 0.0.2-bootstrap

The current prerelease moves beyond the standalone loader foundation and attempts the first real Minecraft launch path.

Normal launch:

1. loads CodaLoader mods from `run/mods`,
2. resolves the exact target from Mojang's official version manifest,
3. reuses matching files from the official `.minecraft` installation when possible,
4. downloads any missing client, libraries, natives and assets,
5. verifies Mojang-provided hashes/sizes,
6. creates `run/game`,
7. starts vanilla Minecraft.

The first bootstrap test intentionally uses a local offline identity named **CodaPlayer**. Microsoft account/session handoff comes after vanilla startup is proven reliable.

## Downloads

Compiled builds are published on the repository's [GitHub Releases](https://github.com/HowlingWhispers/HW-CodaLoader/releases) page.

For Windows, place these two files together and double-click the BAT:

```text
CodaLoader-0.0.2-bootstrap.jar
Launch-CodaLoader.bat
```

Java 25+ is required by Minecraft 26.4 Snapshot 3. Java 26 is supported.

## Runtime layout

```text
run/
├── config/
├── mods/
├── game/
└── runtime/
    ├── versions/
    ├── libraries/
    ├── assets/
    └── natives/
```

`runtime/` is CodaLoader's materialized Minecraft runtime. `game/` is the isolated Minecraft working directory so snapshot testing does not touch normal worlds by default.

## Foundation retained

CodaLoader still provides:

- `coda.mod.json`
- mod JAR discovery
- metadata validation
- dependency validation/order
- dependency-cycle detection
- isolated mod classloaders
- `CodaMod` entrypoints
- `CodaContext`

## Important current boundary

**Minecraft mod injection is not wired yet.** The 0.0.2 goal is narrower: prove that our own JAR can prepare and launch vanilla Minecraft. Once that is green on a real Windows machine, the next layer inserts CodaLoader into Minecraft's class-loading path and begins exposing CodaAPI lifecycle/registry hooks.
