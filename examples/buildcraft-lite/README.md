# BuildCraft Lite for H.O.W.L.

**Experimental human-playtest candidate** for Minecraft Java **26.4 Snapshot 3**.
A separate optional mod. Not the retired BuildCraft prototype, NeoForge, or a
full BuildCraft port. Work only on `main`.

## First functional slice

- Genuine native H.O.W.L. block identities and Creative-tab items:
  `hw_buildcraft_lite:wooden_transport_pipe` and
  `hw_buildcraft_lite:stone_transport_pipe`.
- Actual Minecraft-managed pipe block entities and server-thread native tickers.
- Source chest -> wooden pipe -> one or more stone pipes -> target chest/barrel.
- Automatic wooden extraction **without engines in Lite**; 1 item per pulse
  with a route-length dependent delay, capped at 64 pipe nodes and 16 sources.
- Single-player vanilla single chest/barrel transport using H.O.W.L.'s
  component-preserving native `ItemStack` transaction. Never loads chunks.
- Coda diagnostic command: **`/bclite`**. Detects broken routes, absent source,
  and full target. No fictitious statuses or screenshots.
- Thin cross-arm block and item models. The original BCCE 8.0.23
  `wood_item.png` and `stone_item.png` textures are fetched byte-for-byte
  from pinned upstream commit `23c6af3`, Git blob SHA checked, and packaged
  with the upstream MPL 2.0 notice. H.O.W.L. automatically installs and
  enables an optional mod resource pack when the Lite JAR is found.

## Build

From the `HW-CodaLoader` repository root, with Java 21+ and Internet
access to GitHub:

```bash
bash scripts/build-dist.sh
# compiles optional dist/buildcraft-lite-0.1.0-dev.jar
bash scripts/test-essentials.sh
```

On Windows, use `./scripts/build-dist.ps1`. The CI **Build CodaLoader**
artifact includes both the compatible `CodaLoader.jar` and the optional
`buildcraft-lite-0.1.0-dev.jar`. The mod requires a matching development
loader. It is not bundled as a required player add-on.

## Human playtest (disposable creative world only)

1. Use a **matching H.O.W.L. developer/Nightly runtime** built from this source,
   and place `buildcraft-lite-0.1.0-dev.jar` in the active **game profile's**
   `mods` directory. Remove old/retired BuildCraft variants from this
   disposable profile so their identities cannot be confused.
2. Launch **Minecraft 26.4 Snapshot 3** using H.O.W.L., with Java 25+.
   Look for `BuildCraft Lite` in Creative inventory. H.O.W.L. should prepare
   `HOWL-BuildCraft-Lite.zip` with the two original pipe textures.
3. Place a vanilla **single chest** at `(0,64,0)` and another at `(4,64,0)`.
   Put 10 iron ingots in the first.
4. Place one **Wooden Transport Pipe** at `(1,64,0)` and two **Stone
   Transport Pipes** at `(2,64,0)` and `(3,64,0)`. The second chest must be
   distinct from the source. No command, glass marker or engine is required.
5. Watch the destination chest inventory. It should accumulate ingots.
   Run `/bclite` to have Coda report routes and delivery count.
6. Break the stone pipe at `(2,64,0)`; movement must stop.
   Replace it; movement should resume without duplication. Test a full
   destination and reload a throwaway world.

## Known limitations / do not overclaim

- **Live in-game placement and inventory transfer still require a human test.**
  Headless real-Snapshot registry checks and JVM item-transfer fixture tests
  are green, but they are NOT a substitute for playing Minecraft.
- Cargo currently moves through an authoritative chest-to-chest transaction
  after a route-dependent wait. **Intermediate moving item sprites, original
  BCCE collision geometry, engine activation, multi-way sorting, recipes,
  and all other machines are NOT implemented.**
- Static six-arm pipe models and no-collision temporary blocks are intentional
  Lite placeholders until exact source-native shapes and connections are ported.
- Coda's `/bclite` fault diagnostics work; automatic colored **in-world
  highlighting** still needs the supported client overlay/particle bridge.
- Supports only already-loaded chunks, single-player integrated server,
  ordinary single chests and barrels. **Do not use valuable worlds.**
- User-modified resource packs are never overwritten by H.O.W.L.

## Attribution and licensing

Original textures: [BCCE-team/BuildCraft 8.0.23](https://github.com/BCCE-team/BuildCraft/releases/tag/8.0.23),
by the BCCE team and the original BuildCraft contributors. The original
art bytes are reused under the applicable open-source terms. BCCE's
`LICENSE.txt` is included in the built mod under `META-INF/licenses/`.
H.O.W.L. loader integration and this transport routing implementation are
written independently; no broad relicensing of original BuildCraft code is claimed.
