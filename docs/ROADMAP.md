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


## Future: CML identity, ownership verification, and Discord linking

- [ ] add a one-time Microsoft/Minecraft ownership-verification flow
- [ ] use Microsoft/Minecraft only to prove the player owns Minecraft Java, not as CML's primary social identity
- [ ] create a CML/Howling Whispers account with its own immutable internal player UUID
- [ ] issue CML-managed sessions after ownership has been verified
- [ ] add `/link discord` as an in-game command, intended to be run after the player reaches singleplayer
- [ ] generate a short-lived Discord pairing code/link from `/link discord`
- [ ] complete Discord OAuth on the Howling Whispers service and bind the Discord user ID to the CML account
- [ ] use the immutable Discord user ID internally while displaying the player's current Discord username/display name
- [ ] add an online CML profile/avatar editor independent of the vanilla Minecraft skin system
- [ ] allow CML profiles to describe custom appearance data such as species, colors, ears, tail, cosmetics, and badges
- [ ] sync CML profile/avatar data for singleplayer and future CML-aware multiplayer servers
- [ ] expose linked-account/profile status through future `/cml` commands
- [ ] keep ownership credentials/tokens private and never store raw Microsoft passwords
- [ ] keep vanilla online-mode compatibility explicit: normal Mojang-authenticated servers still require the official Minecraft session identity unless the server is CML-aware

Planned identity model:

```text
Microsoft / Minecraft  -> ownership proof
CML account            -> Howling Whispers player identity
Discord account        -> linked social identity
CML profile            -> avatar / appearance / cosmetics
```

## Ground rules

CodaLoader does not sit on Fabric, Forge, NeoForge, or Quilt. We may study public techniques and Minecraft behavior, but the loader/runtime contracts and API are ours.
