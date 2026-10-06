# CodaLoader architecture

CodaLoader starts with a deliberately small, Minecraft-independent core. The point is to make mod discovery and initialization boring and testable before bytecode transformation enters the picture.

## Foundation pipeline

1. `CodaBootstrap` chooses the game directory.
2. `CodaLoader` scans `<game>/mods` for JAR files.
3. `MetadataReader` reads `coda.mod.json` from each JAR.
4. Metadata is validated, including the exact Minecraft target.
5. `DependencySorter` rejects missing dependencies and cycles, then determines initialization order.
6. Each mod receives its own `URLClassLoader`, parented by the loader/API classloader.
7. The declared entrypoint is instantiated and must implement `CodaMod`.
8. The mod receives an immutable `CodaContext` and initializes.

## Current isolation rule

Each mod has its own classloader. `depends` currently means initialization ordering and presence validation only. Cross-mod class linking is intentionally not implemented yet. That will need an explicit dependency classloader policy rather than accidental classpath leakage.

## Minecraft boundary

The core does not launch, patch, remap, or transform Minecraft yet. The first game integration target is exactly `26.4-snapshot-3`. Minecraft-facing code belongs in a separate bootstrap/transform layer so the core can still be exercised without starting the game.

## Runtime dependencies

None. Metadata parsing is handled by the small internal `MiniJson` parser so CodaLoader does not drag a JSON framework into Minecraft's classpath.
