# Coda Wolf spawn diagnostics hotfix

Minecraft Java 26.4 Snapshot 3 is not yet live-spawn verified. The first
prototype relied entirely on a strict server tick transformer whose native
mapping compatibility was not confirmed.

In a **throwaway** singleplayer test world:

1. Run `/codawolf diagnose`.
   - `server ticks=0` after the world is running means the loader's native
     server-tick callback never reached this mod; auto spawn cannot work yet.
   - `adapter=DISABLED` or `Native Minecraft bridge FAILED` displays the
     underlying Java exception, usually a renamed/missing native method.
2. Run `/codawolf summon` if her save reports `not spawned yet`.
   The command is served on Minecraft's integrated server thread, **without
   requiring the tick hook**. On success she appears as a tamed vanilla wolf,
   named Coda, at your current position.
3. If she is already tracked, the command refuses a second spawn. A missing
   entity may simply be in another dimension or unloaded chunk. Never delete
   save state or spawn replacements based only on a missing entity.
4. If she was killed, sleep in a bed. The manual command does not bypass
   the sleep-respawn rule.

Look for `[Coda Wolf]` and `[H.O.W.L.]` messages in **Copy All Logs**.
Any new missing-mapping diagnosis can be applied to the mod in isolation.
Avoid editing the loader until its active work can be integrated safely.

The mod is still offline-only without AI eyes/voice, and full automatic
spawning cannot be guaranteed before the server-tick hook is verified.
