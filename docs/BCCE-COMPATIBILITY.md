# BCCE on H.O.W.L.: 26.4 Snapshot 3

The existing playable BuildCraft prototype stays unchanged. It is not a port
of BCCE's full gameplay. The development branch `feat/howl-native-mod-api`
adds the first loader API and source adapter needed to reuse BCCE's original
implementations. Loader version **0.0.30 is unreleased** on this branch.

The inspected upstream release is **8.0.23+1.21.11+neoforge**, SHA-256
`d468b51c12beb979e00c389964f68a85a885762ddfe195a2ab5d1040a4a79107`.
Its manifest requires Minecraft `[1.21.11,1.21.12)` and NeoForge `[21.11.45,)`.
It contains nine modules: lib, core, factory, transport, energy, builders,
silicon, robotics and compat. The unchanged release JAR cannot currently run
on H.O.W.L. and 26.4 Snapshot 3. Changing its manifest cannot resolve that.

## Implemented

H.O.W.L. accepts deferred factories for original Minecraft-native classes,
without replacing them with generic blocks. Its bootstrap bridge supplies
native registry keys, block-state packet IDs, shape caches and block-item
associations. The BCCE platform `RegistryBinding` uses this API to bind the
original ordered catalogs and their lazy entry references. It does not change
BCCE's pipe or engine algorithms. The API is supplied by H.O.W.L.; players
will not need another API JAR.

The Snapshot inspection workflow verifies factories against Mojang's actual
26.4 Snapshot 3 classes. Its BCCE test fetches checksum-verified catalog
sources from upstream commit
`b1b166d29da797abf6df3e0618a3bd62f14bc41e`, selects the modern Minecraft
branch and adapts `ResourceLocation` to Snapshot 3's `Identifier`. It checks
deferred execution, dependency order, object identity, binding and refused
late entries. These tests are not a full in-game BCCE playtest.

## Still required for the full mod

BCCE's module lifecycle and service implementations must be connected to
H.O.W.L., and its 1.21.11 Minecraft calls adapted to Snapshot 3. Its original
machines also require capability access, persistent block entities, packet
handlers, client renderers/model data, recipes and world generation. Optional
JEI/Jade integration needs its own compatibility decision. None of those are
provided by this registry adapter alone.

Use the upstream shared gameplay and modern sources, with H.O.W.L. platform
implementations replacing NeoForge integration points. Retain upstream MPL-2.0
licensing and notices. The adapter lives outside the loader's production JAR;
test downloads retain the original source files and upstream license.
Do not publish this foundation as a complete BuildCraft port or replace the
working launcher download with an incomplete mod.
