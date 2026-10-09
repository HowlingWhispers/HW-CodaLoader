# BuildCraft Lite: first H.O.W.L. slice

Target: **Minecraft Java 26.4 Snapshot 3**, **H.O.W.L.**, on GitHub `main`.

This is a new minimal mod, **not** a resurrection of the deleted custom BuildCraft prototype and **not** a full upstream port.

## Implemented in this first source slice

- Native registrations for wooden and stone pipe blocks and their BlockItems.
- Shared block-entity type declaration through H.O.W.L.
- A BuildCraft Lite Creative tab and `/bclite` Coda capability/status command.
- Honest diagnostic messages; Coda does not claim to see a blockage she cannot inspect.

## Not yet implemented

- Original BuildCraft pipe geometry, textures, connection states or recipes.
- Working wooden extraction, travelling item state, stone transport, chest insertion.
- Persistent native pipe holder and original transport item rendering.
- Red/yellow/green Coda outline overlays.

**Do not announce this as a playable transport mod** and do not publish a Nightly based on block registration alone.

## Next implementation milestone

Adapt the original BuildCraft BCCE transport pipe and travelling-item code under its license, preserving copyright notices, into native H.O.W.L. block entities. Implement actual server-thread chest transactions (the current `CodaSingleplayerWorld` supports only a temporary glass marker and a development-only transaction), save/reload state, then test one wooden-to-stone-to-chest route. Add the Coda diagnostics overlay only when real connection state is available.

Use `sdk/build-mod.sh` or `sdk/build-mod.ps1` as the foundation for packaging with the public API JAR supplied by the H.O.W.L. SDK. Do not bundle that API JAR into the mod.
