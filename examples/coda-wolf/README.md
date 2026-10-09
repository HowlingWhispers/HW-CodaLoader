# Coda Wolf Companion (H.O.W.L. experimental mod)

An isolated H.O.W.L. mod, **not a loader change**. Target: Minecraft Java **26.4 Snapshot 3**, H.O.W.L. SDK.
Default combat behavior: **DEFENSIVE**. Vanilla tame wolf, mortal, one per player/world, world-local persistent UUID, protective targeting halted at <=25% HP, and bedtime return after confirmed death. A genuine flee behavior has not yet been ported, so this is not a claim that Coda will physically retreat.

## Status
- Java source compiles against the current public H.O.W.L. API method signatures (JDK 21). Pure rule/state tests pass.
- Native Minecraft 26.4 Snapshot 3 methods are accessed through **strict reflection**. They MUST be tested in-game before a release. On an incompatible named mapping the module disables itself rather than guessing.
- No loader or launcher source is touched. No external AI calls, camera captures, voice, or uploads. The companion remains offline-first.
- **Environmental sensing:** continuously samples up to 8 *already loaded* blocks every 2 server ticks within 3 blocks horizontally and 1 vertically of Coda, using authentic Minecraft registry IDs and block tags. The scans cannot load chunks or alter the environment. She occasionally offers grounded suggestions about mushrooms, ore, crops, bees, amethyst and hazards (45-second global speaking cooldown and 10-minute per-block deduplication). A world-scoped /codawolf awareness on/off preference controls scanning; /codawolf nearby displays recent names and tags.
- Generative active AI is **not connected**. The bounded observation feed is ready for a future opt-in provider, but no world data is sent anywhere or confused with camera vision. Vanilla wolf pathfinding and autonomous AI remain intact. Coda's defensive script clears only targets it explicitly assigned, not vanilla's chosen targets.
- Death detection relies on previously tracked live wolf state. If the game removes a dead wolf before the next callback, the module might not detect it; a future public entity death event is preferable.
- If a known wolf is simply in an unloaded chunk or another dimension, do **not** spawn a duplicate.
- Sleep return requires an observed completed night skip. Sleeping without a night skip does not qualify. First spawn occurs when the player first enters a new world, not during character creation.
- Coda now gets a **Coda-only icy-white/cyan custom fur pack** (generated from the original Snapshot 3 64x32 Snowy Wolf UV, normal and angry states), selected by her verified persisted wolf UUID through H.O.W.L. No other wolves are recolored. This is a first UV-safe look, not a pixel-exact recreation of the illustrative concept atlas. Bespoke blue-eye/face pixels, voice and custom wolf geometry remain future steps.

## Build
Obtain the SDK's **real** `howl-api.jar` matching H.O.W.L. and run:
```sh
bash scripts/test.sh
bash scripts/build.sh /path/to/howl-api.jar
```
Do not bundle H.O.W.L. API into the final mod JAR. Copy the built `coda-wolf-0.1.0-dev.jar` into an isolated development game's `mods/` folder. Remove any duplicate `coda_wolf` mod ID first.

## Commands
`/codawolf`, `/codawolf status`, `/codawolf nearby`, `/codawolf awareness [on|off]`, `/codawolf ai`, `/codawolf defensive`, `/codawolf diagnose`, `/codawolf summon`, `/codawolf help`.
Defensive mode is intentionally the only mode and does not require saved configuration.

## Before declaring playable
1. Smoke-test H.O.W.L. initialization, then create a **throwaway** singleplayer world using 26.4 Snapshot 3.
2. Confirm one tamed wolf named Coda, with wolf UUID stable across leaving/reopening and across chunk unloads.
3. Damage owner, check Coda fights attacker. Check no unprovoked attack; test low HP retreat.
4. Kill Coda, sleep successfully, verify exactly one return and state survives restart.
5. Observe logs for missing named mapping methods or unresolved death hooks; do not use prized worlds before verification.

Proposed next API evolution: public singleplayer entity lifecycle, owner damage, successful-sleep event, and read-only sensory snapshots. Merge only after the loader team's separate work is compared and reviewed.

## Install via CodaLauncher without modifying loader

CodaLauncher scans only the **active Minecraft mods directory**, not GitHub branches or
`loader/run/mods`. The correct folder is exposed via **Mods > OPEN MODS FOLDER**.
Drop the compiled JAR there, then click **REFRESH**. It will appear as
`Coda Wolf Companion | coda_wolf | 0.1.0-dev | Recognized`.
The launcher never auto-downloads arbitrary external mods merely because a branch was pushed.
Stable and Nightly have separate mod folders.

The companion-only GitHub Actions workflow creates a downloadable installer ZIP.
Windows users can run `Install-CodaWolf.ps1` from the extracted package;
it detects the launcher's saved channel and refuses to overwrite a duplicate
`coda_wolf` installation. Alternatively, just copy the JAR using OPEN MODS FOLDER.
**Recognized** means readable metadata, not validated Minecraft behavior.
