# Coda Wolf Companion (H.O.W.L. experimental mod)

An isolated H.O.W.L. mod, **not a loader change**. Target: Minecraft Java **26.4 Snapshot 3**, H.O.W.L. SDK.
Default combat behavior: **DEFENSIVE**. Vanilla tame wolf, mortal, one per player/world, world-local persistent UUID, protective targeting, retreat at <=25% HP, and bedtime return after confirmed death.

## Status
- Java source compiles against the current public H.O.W.L. API method signatures (JDK 21). Pure rule/state tests pass.
- Native Minecraft 26.4 Snapshot 3 methods are accessed through **strict reflection**. They MUST be tested in-game before a release. On an incompatible named mapping the module disables itself rather than guessing.
- No loader or launcher source is touched. No external AI calls, camera captures, voice, or uploads. The first iteration is deliberately offline-first.
- Death detection relies on previously tracked live wolf state. If the game removes a dead wolf before the next callback, the module might not detect it; a future public entity death event is preferable.
- If a known wolf is simply in an unloaded chunk or another dimension, do **not** spawn a duplicate.
- Sleep return requires an observed completed night skip. Sleeping without a night skip does not qualify. First spawn occurs when the player first enters a new world, not during character creation.
- White custom fur and Coda's voice/eyes are future steps; the current mod uses a vanilla wolf and optional cyan collar.

## Build
Obtain the SDK's **real** `howl-api.jar` matching H.O.W.L. and run:
```sh
bash scripts/test.sh
bash scripts/build.sh /path/to/howl-api.jar
```
Do not bundle H.O.W.L. API into the final mod JAR. Copy the built `coda-wolf-0.1.0-dev.jar` into an isolated development game's `mods/` folder. Remove any duplicate `coda_wolf` mod ID first.

## Commands
`/codawolf`, `/codawolf status`, `/codawolf defensive`, `/codawolf help`.
Defensive mode is intentionally the only mode and does not require saved configuration.

## Before declaring playable
1. Smoke-test H.O.W.L. initialization, then create a **throwaway** singleplayer world using 26.4 Snapshot 3.
2. Confirm one tamed wolf named Coda, with wolf UUID stable across leaving/reopening and across chunk unloads.
3. Damage owner, check Coda fights attacker. Check no unprovoked attack; test low HP retreat.
4. Kill Coda, sleep successfully, verify exactly one return and state survives restart.
5. Observe logs for missing named mapping methods or unresolved death hooks; do not use prized worlds before verification.

Proposed next API evolution: public singleplayer entity lifecycle, owner damage, successful-sleep event, and read-only sensory snapshots. Merge only after the loader team's separate work is compared and reviewed.
