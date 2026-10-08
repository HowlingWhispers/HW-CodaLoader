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
| `context.registerServerTick(id, callback)` | Registers an authoritative server-thread callback after each native `MinecraftServer.tickServer(BooleanSupplier)` return. Server sessions have opaque IDs and 1-based tick counters; handlers are isolated and disabled after three consecutive errors. Early integration, requires live Snapshot 3 mapping verification. |
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

## Native server ticks (development API)

```java
context.registerServerTick("machine_tick", tick -> {
    // This callback runs on the integrated server thread, not the client.
    // tick.sessionId() distinguishes new server/world instances.
    // tick.tick() is a monotonically increasing tick callback counter.
});
```

The H.O.W.L. agent instruments **only** the exact named Snapshot 3 server method
`MinecraftServer.tickServer(BooleanSupplier)`. If the method name or descriptor
does not match, it logs the mismatch and **does not guess another hook**. There
is no fallback polling thread, no client tick masquerading as a server tick,
and no direct Minecraft world or inventory references in the callback yet.
Callbacks must never block the server or assume they have item/block APIs.
The automated fixture tests cover callback timing, fault isolation, repeated
ticks and new-server isolation. These tests do **not** establish live Snapshot 3
mapping compatibility.

## Single-player world inspection (development API)

When Minecraft runs an integrated single-player server, H.O.W.L. provides a
**read-only and tick-scoped** world view to server tick callbacks:

```java
context.registerServerTick("inspect", tick -> {
    if (tick.world().isEmpty()) return; // old test fixtures still work
    var world = tick.world().orElseThrow();
    var where = new CodaBlockPos(12, 64, -3);
    if (!world.isChunkLoaded("minecraft:overworld", where)) return;
    world.inventory("minecraft:overworld", where).ifPresent(chest -> {
        int items = chest.slots().stream().mapToInt(CodaInventoryView.Slot::count).sum();
        // Read-only information; no extraction or item conversion is allowed.
    });
});
```

The adapter resolves loaded level identities and already-loaded chunks, uses
Snapshot 3's named `ServerChunkCache.getChunkNow` (never `getChunk`) and
exposes only slot counts/capacities for `net.minecraft.world.Container`.
World views expire as soon as the owning server-tick callback dispatch ends.
Cross-thread access is refused. A missing native mapping raises an explicit
error rather than trying unsafe fallbacks. Unknown or unloaded chunks are
reported as absent and do not get generated.

This is the first safe bridge needed to make wooden BuildCraft pipes work with
single-player chests; it is **not a way to remove, clone, add or transfer
items**. Minecraft ItemStack components, slot restrictions, permissions and
transactional inventory transfers must be implemented before playable
extraction. This API has only instrumented JVM **fixture** test coverage so far,
not live 26.4 Snapshot 3 compatibility confirmation.

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
