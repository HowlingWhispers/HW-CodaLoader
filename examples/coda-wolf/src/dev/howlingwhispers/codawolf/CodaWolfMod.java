package dev.howlingwhispers.codawolf;

import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;
import dev.howlingwhispers.codaloader.api.CodaServerTickContext;
import dev.howlingwhispers.codaloader.api.CodaEntityAppearance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;

/**
 * Coda's ordinary, mortal, tame wolf body. Offline-first, one per owner per world.
 * This module does not connect to any AI provider or transmit gameplay data.
 */
public final class CodaWolfMod implements CodaMod {
    private static final String PREFIX = "[Coda Wolf] ";
    private static final String TAME = "codawolf:entity/coda_tame";
    private static final String ANGRY = "codawolf:entity/coda_angry";
    private static final int[][] SCAN_OFFSETS = scanOffsets();
    /** Near-first fixed 7x3x7 search. Eight loaded blocks every two ticks,
     * spread over time: no full-chunk scan, lighting engine or screenshots. */
    private static int[][] scanOffsets() {
        List<int[]> offsets = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++)
                    offsets.add(new int[]{dx,dy,dz});
        offsets.sort(Comparator.comparingInt(v -> v[0]*v[0]+v[1]*v[1]+v[2]*v[2]));
        return offsets.toArray(int[][]::new);
    }

    private static void skin(java.util.UUID wolfId) {
        CodaEntityAppearance.setWolfSkin(wolfId, TAME, ANGRY);
    }
    private void clearRegisteredSkins() {
        for (Companion c : companions.values())
            CodaEntityAppearance.clearWolfSkin(c.save.wolfId);
    }
    private final Map<UUID, Companion> companions = new HashMap<>();
    private String serverSession;
    private Object serverIdentity;
    private boolean bridgeUnsupported;
    private long ticksSeen;
    private String lastFailure = "none";

    private static final class Companion {
        final CompanionSave save;
        final CompanionRules.SleepGate sleep = new CompanionRules.SleepGate();
        final CompanionAwareness awareness = new CompanionAwareness();
        boolean awarenessUnavailable;
        Object assignedTarget;
        Object cachedWolf;
        Object aggressor;
        long defendUntilTick;
        int ownerAttackStamp;
        int wolfAttackStamp;
        boolean disabled;
        Companion(CompanionSave save) { this.save = save; }
    }

    @Override public void onInitialize(CodaContext context) {
        context.registerServerTick("companion", this::tick);
        context.registerCommand("codawolf", "Inspect or recover Coda's defensive wolf companion", (player, args) -> {
            if (args.size() > 2 || (!args.isEmpty() &&
                    !List.of("status", "help", "defensive", "diagnose", "summon",
                             "awareness", "nearby", "ai").contains(args.get(0)))
                    || (args.size() == 2 && !args.get(0).equals("awareness"))) {
                player.reply("Usage: /codawolf [status|summon|diagnose|nearby|awareness on/off|ai|help]");
                return;
            }
            String command = args.isEmpty() ? "status" : args.get(0);
            if (command.equals("help")) {
                player.reply("Coda: defensive and offline-aware. Commands: /codawolf status, " +
                        "/codawolf nearby, /codawolf awareness on/off, /codawolf diagnose, " +
                        "/codawolf summon, /codawolf ai. No provider data is sent.");
                return;
            }
            CompanionSave save = CompanionSave.load(player.worldDirectory(), player.playerId());
            if (command.equals("ai")) {
                player.reply("Offline reactions are working. A generative AI provider is " +
                        "not yet connected to Coda Companion. No observations leave this PC.");
                return;
            }
            if (command.equals("awareness")) {
                if (args.size() == 2) {
                    if (!List.of("on", "off").contains(args.get(1))) {
                        player.reply("Usage: /codawolf awareness [on|off]"); return;
                    }
                    boolean enabled = args.get(1).equals("on");
                    save.awarenessEnabled = enabled;
                    save.persist();
                    Companion cached = companions.get(player.playerId());
                    if (cached != null) cached.save.awarenessEnabled = enabled;
                    player.reply("Coda awareness " + (enabled ? "ON" : "OFF")
                            + ". Offline only; no camera or external service.");
                } else {
                    player.reply("Coda awareness: " + (save.awarenessEnabled ? "ON" : "OFF")
                            + ". Tip cooldown 45s. Use /codawolf nearby for her observations.");
                }
                return;
            }
            if (command.equals("nearby")) {
                Companion cached = companions.get(player.playerId());
                if (cached == null || cached.awareness.recent().isEmpty()) {
                    player.reply("Nothing identified nearby yet. Stay close to Coda for a few " +
                            "seconds, or check /codawolf awareness.");
                } else {
                    player.reply("Coda's recent registry observations (local only):");
                    for (var observation : cached.awareness.recent().stream().limit(8).toList()) {
                        String tags = observation.tags().isEmpty() ? "" :
                                " #" + String.join(" #", observation.tags().stream().limit(2).toList());
                        player.reply(" * " + observation.blockId() + tags +
                                " (~" + (int)Math.sqrt(observation.distanceSquared()) + " blocks)");
                    }
                }
                return;
            }
            if (command.equals("summon")) {
                summonFromCommand(player, save);
                return;
            }
            String status = describeSave(save);
            if (!command.equals("diagnose")) {
                player.reply("Coda: " + status + ". Combat: DEFENSIVE. Awareness: " +
                        (save.awarenessEnabled ? "ON" : "OFF") + ". Active AI: NOT CONNECTED. " +
                        (bridgeUnsupported ? "Native bridge disabled; use /codawolf diagnose." : ""));
                return;
            }
            player.reply("Coda diagnostics: server ticks=" + ticksSeen +
                    ", adapter=" + (bridgeUnsupported ? "DISABLED" : "not disabled") +
                    ", save=" + status + ", last error=" + lastFailure +
                    ", observed blocks=" + (companions.containsKey(player.playerId())
                            ? companions.get(player.playerId()).awareness.observationsSeen() : 0));
            try {
                MinecraftWolfBridge game = new MinecraftWolfBridge();
                Object owner = game.player(player.playerId());
                if (owner == null) {
                    player.reply("Native player lookup failed: your UUID is not in the integrated server player list.");
                    return;
                }
                if (save.wolfId == null) {
                    player.reply("Native world/player bridge is reachable. No living wolf UUID saved.");
                    return;
                }
                Object wolf = game.wolf(game.level(owner), save.wolfId);
                player.reply(wolf == null
                        ? "Native bridge works, but Coda's UUID is not loaded in this dimension. No duplicate spawned."
                        : "Native bridge works. Wolf is loaded; alive=" + !game.dead(wolf) +
                                ", health=" + game.health(wolf));
            } catch (Exception ex) {
                lastFailure = detail(ex);
                player.reply("Native Minecraft bridge FAILED: " + lastFailure);
                System.err.println(PREFIX + "Diagnostic exception: " + ex);
            }
        });
        System.out.println(PREFIX + "0.1.2-dev initialized. If she does not spawn: /codawolf diagnose, then /codawolf summon.");
    }

    private String describeSave(CompanionSave save) {
        return !save.created ? "not spawned yet" : save.pendingRespawn
                ? "waiting for successful bed sleep" : save.wolfId == null
                ? "inconsistent save: no wolf UUID" : "tracked wolf " + save.wolfId;
    }

    private static String detail(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        String description = cause.getClass().getSimpleName() +
                (message == null || message.isBlank() ? "" : ": " + message);
        return description.length() > 350 ? description.substring(0, 350) : description;
    }

    /**
     * The native Brigadier command is dispatched on the integrated server thread,
     * independently of the optional H.O.W.L. tick hook. This is both a safe manual
     * recovery path and a diagnostic probe for missing native mappings.
     */
    private void summonFromCommand(dev.howlingwhispers.codaloader.api.CodaCommandContext command,
                                   CompanionSave save) throws Exception {
        if (save.pendingRespawn) {
            command.reply("Coda fell in combat. Sleep in a bed to bring her back. No duplicate spawned.");
            return;
        }
        MinecraftWolfBridge game;
        try {
            game = new MinecraftWolfBridge();
        } catch (Exception ex) {
            lastFailure = detail(ex);
            command.reply("Cannot access Minecraft singleplayer server: " + lastFailure);
            return;
        }
        Object owner = game.player(command.playerId());
        if (owner == null) {
            command.reply("Cannot find your player in the current singleplayer server.");
            return;
        }
        if (save.wolfId != null) {
            Object existing = game.wolf(game.level(owner), save.wolfId);
            if (existing != null && !game.dead(existing)) {
                game.markCoda(existing);
                skin(save.wolfId);
                command.reply("Coda already exists. Her saved wolf UUID is " + save.wolfId + ".");
                return;
            }
            // A missing entity does not imply death: she could be in an unloaded
            // chunk or another dimension. Never produce a duplicate.
            command.reply("Coda is registered but not loaded here. Return to her last area; " +
                    "no duplicate will be spawned. /codawolf diagnose for details.");
            return;
        }
        if (save.created) {
            command.reply("Coda's save says she existed but has no active wolf. " +
                    "No duplicate will be spawned; check /codawolf diagnose.");
            return;
        }
        try {
            Companion companion = companions.computeIfAbsent(command.playerId(), unused -> new Companion(save));
            create(game, companion, owner);
            command.reply("Coda has joined you! Defensive mode active.");
        } catch (Exception ex) {
            lastFailure = detail(ex);
            command.reply("Coda could not spawn: " + lastFailure + ". See launcher logs.");
            System.err.println(PREFIX + "Manual summon failed: " + ex);
        }
    }

    private void tick(CodaServerTickContext tick) {
        ticksSeen++;
        if (serverSession == null || !serverSession.equals(tick.sessionId())) {
            serverSession = tick.sessionId();
            serverIdentity = null;
            bridgeUnsupported = false;
            clearRegisteredSkins();
            companions.clear();
        }
        if (bridgeUnsupported || tick.tick() % 2 != 0) return;
        try {
            MinecraftWolfBridge game = new MinecraftWolfBridge();
            if (serverIdentity != game.serverIdentity()) {
                serverIdentity = game.serverIdentity();
                clearRegisteredSkins();
                companions.clear();
            }
            for (Object player : game.players()) {
                UUID owner = game.id(player);
                Companion c = companions.get(owner);
                if (c == null) {
                    c = new Companion(CompanionSave.load(game.worldDirectory(), owner));
                    companions.put(owner, c);
                }
                if (c.disabled) continue;
                try { update(game, c, player, tick.tick()); }
                catch (Exception ex) {
                    c.disabled = true;
                    lastFailure = detail(ex);
                    System.err.println(PREFIX + "Disabled for player " + owner + " until next session: " + ex);
                }
            }
        } catch (Exception ex) {
            bridgeUnsupported = true;
            lastFailure = detail(ex);
            System.err.println(PREFIX + "Snapshot 3 singleplayer mapping unavailable; " +
                    "automatic spawning disabled without touching loader: " + ex);
        }
    }

    private void update(MinecraftWolfBridge game, Companion c, Object player, long tick) throws Exception {
        Object level = game.level(player);
        // First companion creation cannot depend on optional sleep/time APIs.
        // Snapshot 3's ServerLevel.getDayTime() is absent. Only consult the
        // day clock while a *dead* companion is awaiting bed-based recovery.
        if (!c.save.created) { create(game, c, player); return; }

        Object wolf = c.save.wolfId == null ? null : game.wolf(level, c.save.wolfId);
        if (wolf != null) {
            c.cachedWolf = wolf;
            // UUID is retrieved from CompanionSave, not the wolf's display name.
            // The client and integrated server share this JVM appearance table.
            skin(c.save.wolfId);
            game.markCoda(wolf); // NBT marker is cosmetic, not the renderer's source of truth.
        }
        // Missing wolf may be in an unloaded chunk or another dimension; never duplicate it.
        if (c.save.wolfId != null && c.cachedWolf != null && game.dead(c.cachedWolf)) {
            CodaEntityAppearance.clearWolfSkin(c.save.wolfId);
            c.save.wolfId = null;
            c.save.pendingRespawn = true;
            c.cachedWolf = null;
            c.save.persist();
            System.out.println(PREFIX + "Coda fell in combat. Bed sleep required for her return.");
        }
        if (c.save.pendingRespawn) {
            boolean slept = c.sleep.tick(game.sleeping(player), game.dayTime(level));
            if (slept) create(game, c, player);
            return;
        }
        if (wolf != null && !game.dead(wolf)) {
            defend(game, c, player, wolf, tick);
            if (tick % CompanionAwareness.SCAN_INTERVAL_TICKS == 0)
                sense(game, c, player, wolf, tick);
        }
    }

    private void create(MinecraftWolfBridge game, Companion c, Object player) throws Exception {
        Object wolf = game.spawn(player);
        UUID oldId = c.save.wolfId;
        boolean oldPending = c.save.pendingRespawn, oldCreated = c.save.created;
        c.save.wolfId = game.id(wolf);
        c.save.created = true;
        c.save.pendingRespawn = false;
        try { c.save.persist(); }
        catch (Exception ex) {
            c.save.wolfId = oldId;
            c.save.pendingRespawn = oldPending;
            c.save.created = oldCreated;
            try { game.discard(wolf); } catch (Exception failure) { ex.addSuppressed(failure); }
            throw ex;
        }
        c.cachedWolf = wolf;
        skin(c.save.wolfId);
        c.aggressor = null;
        c.assignedTarget = null;
        c.defendUntilTick = 0;
        c.ownerAttackStamp = 0;
        c.wolfAttackStamp = 0;
        System.out.println(PREFIX + "Coda joined the world as a tamed, defensive wolf: " + c.save.wolfId);
    }

    /** Never let missing sensory mappings disable Coda's movement, combat,
     * health, respawn, world saves or vanilla Wolf entity behavior. */
    private void sense(MinecraftWolfBridge game, Companion c, Object owner,
                       Object wolf, long tick) {
        if (!c.save.awarenessEnabled || c.awarenessUnavailable) return;
        try {
            if (game.distance(owner, wolf) > CompanionRules.GUARD_RANGE_SQUARED)
                return; // Never send suggestions from an abandoned distant pet.
            for (int sample = 0; sample < CompanionAwareness.BLOCKS_PER_SCAN; sample++) {
                int[] offset=SCAN_OFFSETS[c.awareness.nextScanIndex(SCAN_OFFSETS.length)];
                var observed=game.observeBlock(wolf, offset[0], offset[1], offset[2], tick);
                if (observed == null) continue;
                var idea=c.awareness.observe(observed);
                if (idea.isPresent()) game.comment(owner, idea.get().text());
            }
        } catch (Exception invalidMapping) {
            c.awarenessUnavailable = true;
            System.err.println(PREFIX + "Environment sensing paused (vanilla wolf AI "
                    + "still running): " + detail(invalidMapping));
        }
    }

    private void defend(MinecraftWolfBridge game, Companion c, Object player, Object wolf, long tick) throws Exception {
        int ownerStamp = game.lastAttackStamp(player), wolfStamp = game.lastAttackStamp(wolf);
        if (ownerStamp != 0 && ownerStamp != c.ownerAttackStamp) {
            c.aggressor = game.lastAttacker(player);
            c.defendUntilTick = tick + 100;
        }
        if (wolfStamp != 0 && wolfStamp != c.wolfAttackStamp) {
            c.aggressor = game.lastAttacker(wolf);
            c.defendUntilTick = tick + 100;
        }
        c.ownerAttackStamp = ownerStamp;
        c.wolfAttackStamp = wolfStamp;
        boolean recent = c.aggressor != null && tick <= c.defendUntilTick && !game.dead(c.aggressor);
        boolean safe = CompanionRules.shouldDefend(recent, !game.dead(wolf),
                game.health(wolf), game.maxHealth(wolf), game.distance(wolf, player));
        if (safe) safe = game.distance(wolf, c.aggressor) <= CompanionRules.GUARD_RANGE_SQUARED;
        Object current = game.target(wolf);
        if (!safe) {
            // Do not cancel vanilla AI's own hunting/defense targets.
            if (CompanionRules.releaseAssignedTarget(false,
                    current != null && current == c.assignedTarget))
                game.target(wolf, null);
            c.assignedTarget = null;
        } else if (current != c.aggressor) {
            game.target(wolf, c.aggressor);
            c.assignedTarget = c.aggressor;
        }
    }
}
