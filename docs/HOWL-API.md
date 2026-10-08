# HOWL API and revival development

The HOWL SDK is an early developer kit for **Minecraft Java 26.4 Snapshot 3**.
Each release ships its matching API JAR and an editable working starter.
Install mods through CodaLauncher and use Local Test Mode for singleplayer
development. The API is still evolving; pin your loader and Minecraft target
and retest against new releases.

## Available contracts

| Contract | Current behaviour |
| --- | --- |
| `CodaMod.onInitialize(CodaContext)` | Runs once when the loader initializes a mod. It is not a world-ready event. |
| `CodaContext` | Loader/Minecraft versions, game/config directories, own mod ID and immutable loaded mod IDs. |
| `context.registerCommand(name, description, command)` | Registers a player command. The integrated-server bridge executes it on the server thread. |
| `CodaCommandContext` | Player UUID, world directory, position, replies and checked teleports. Store world/player state using these identities. |
| `context.registerScreen(id, priority, factory)` | Registers a native-screen factory; the Minecraft GUI bridge constructs/opens it on the GUI thread when requested. Registration alone does not open a screen. |
| `coda.mod.json` schema 1 | Exact Minecraft target, unique mod ID, entrypoint and required mod IDs. Missing dependencies, duplicates and dependency cycles are rejected. |

The public Java namespace remains `dev.howlingwhispers.codaloader.api`.
Use the SDK's API JAR only to compile. The loader supplies the implementation;
shipping a private API copy can break entrypoint and registry identity.

Mods have separate classloaders. `depends` controls presence and startup order;
it does not provide cross-mod Java class access or version ranges. Initialization
does not grant safe access to Minecraft's client or world threads.

## A simple command

Inside your mod's `onInitialize` method:

```java
context.registerCommand("hellohowl", "A greeting from my mod",
        (player, arguments) -> player.reply("H.O.W.L. says hello!"));
```

Use a unique command name. The bridge preserves an existing command collision
and reports it rather than replacing it. Keep command work short and store
world data under `player.worldDirectory()` with your own mod namespace.

## What revival mods still need

Block/item registration, recipes, machine ticking, persistent block entities,
inventory/fluid/energy transport, world generation and network synchronization
are not provided as a complete public SDK yet. A BuildCraft port needs those
contracts before pipes, engines and quarries can operate. The first revival
tracking issue is [BuildCraft](https://github.com/HowlingWhispers/HW-CodaLoader/issues/1).

This release supplies a working entrypoint/command starting point. It does not
make an old Forge or Fabric JAR compatible. Review each upstream project's
license and preserve required notices when adapting its code or assets.

## Reporting and publishing

Distribute your own mod JAR and its license/notices, declare the exact supported
Minecraft and loader versions, and include a short installation guide. Players
put the JAR in their HW Minecraft `mods` folder. A hosted community Workshop,
mod signing system and automatic third-party dependency installer remain planned.

For bug reports, include the loader version, mod version, exact Minecraft
target, launch mode, steps to reproduce and redacted Copy All Logs output.
Loader-only initialization tests complement a live Snapshot 3 game test;
they do not establish gameplay or networking compatibility.
