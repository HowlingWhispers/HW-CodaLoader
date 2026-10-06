# CodaLoader roadmap

## 0.0.1 Foundation

- [x] `coda.mod.json` metadata
- [x] JAR discovery
- [x] metadata validation
- [x] duplicate mod-id rejection
- [x] required dependency validation
- [x] dependency ordering and cycle detection
- [x] one classloader per mod
- [x] `CodaMod` initialization entrypoint
- [x] standalone Hello Coda proof mod
- [x] pin first target to Minecraft Java `26.4-snapshot-3`

## 0.0.2 Minecraft bootstrap

- [ ] resolve the official Mojang version metadata for `26.4-snapshot-3`
- [ ] resolve the official client JAR and libraries without repackaging Minecraft
- [ ] launch the vanilla client through CodaLoader
- [ ] identify the earliest safe hook point
- [ ] add a transformation pipeline before Minecraft classes are defined
- [ ] keep vanilla startup working with an empty `mods/` directory

## 0.0.3 First CodaAPI hooks

- [ ] lifecycle events
- [ ] command registration
- [ ] first safe registry hook
- [ ] crash diagnostics showing which Coda mod failed

## Ground rules

CodaLoader does not sit on Fabric, Forge, NeoForge, or Quilt. We may study public techniques and Minecraft behavior, but the loader/runtime contracts and API are ours.
