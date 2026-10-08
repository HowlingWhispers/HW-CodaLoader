package dev.howlingwhispers.codawolf;

import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;
import dev.howlingwhispers.codaloader.api.CodaServerTickContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coda's ordinary, mortal, tame wolf body. Offline-first, one per owner per world.
 * This module does not connect to any AI provider or transmit gameplay data.
 */
public final class CodaWolfMod implements CodaMod {
    private static final String PREFIX = "[Coda Wolf] ";
    private final Map<UUID, Companion> companions = new HashMap<>();
    private String serverSession;
    private Object serverIdentity;
    private boolean bridgeUnsupported;
    private long ticksSeen;
    private String lastFailure = "none";

    private static final class Companion {
        final CompanionSave save;
        final CompanionRules.SleepGate sleep = new CompanionRules.SleepGate();
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
            if (args.size() > 1 || (!args.isEmpty() &&
                    !List.of("status", "help", "defensive", "diagnose", "summon").contains(args.get(0)))) {
                player.reply("Usage: /codawolf [status|diagnose|summon|defensive|help]");
                return;
            }
            String command = args.isEmpty() ? "status" : args.get(0);
            if (command.equals("help")) {
                player.reply("Coda: DEFENSIVE. Commands: /codawolf status, /codawolf diagnose, /codawolf summon. " +
                        "The summon command is safe and never duplicates a registered wolf.");
                return;
            }
            CompanionSave save = CompanionSave.load(player.worldDirectory(), player.playerId());
            if (command.equals("summon")) {
                summonFromCommand(player, save);
                return;
            }
            String status = describeSave(save);
            if (!command.equals("diagnose")) {
                player.reply("Coda: " + status + ". Combat: DEFENSIVE. " +
                        (bridgeUnsupported ? "Native bridge disabled; use /codawolf diagnose." : ""));
                return;
            }
            player.reply("Coda diagnostics: server ticks=" + ticksSeen +
                    ", adapter=" + (bridgeUnsupported ? "DISABLED" : "not disabled") +
                    ", save=" + status + ", last error=" + lastFailure);
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
        System.out.println(PREFIX + "0.1.0-dev initialized. If she does not spawn: /codawolf diagnose, then /codawolf summon.");
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
            companions.clear();
        }
        if (bridgeUnsupported || tick.tick() % 2 != 0) return;
        try {
            MinecraftWolfBridge game = new MinecraftWolfBridge();
            if (serverIdentity != game.serverIdentity()) {
                serverIdentity = game.serverIdentity();
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
        boolean slept = c.sleep.tick(game.sleeping(player), game.dayTime(level));
        if (!c.save.created) { create(game, c, player); return; }

        Object wolf = c.save.wolfId == null ? null : game.wolf(level, c.save.wolfId);
        if (wolf != null) c.cachedWolf = wolf;
        // Missing wolf may be in an unloaded chunk or another dimension; never duplicate it.
        if (c.save.wolfId != null && c.cachedWolf != null && game.dead(c.cachedWolf)) {
            c.save.wolfId = null;
            c.save.pendingRespawn = true;
            c.cachedWolf = null;
            c.save.persist();
            System.out.println(PREFIX + "Coda fell in combat. Bed sleep required for her return.");
        }
        if (c.save.pendingRespawn && slept) { create(game, c, player); return; }
        if (wolf != null && !c.save.pendingRespawn && !game.dead(wolf))
            defend(game, c, player, wolf, tick);
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
        c.aggressor = null;
        c.defendUntilTick = 0;
        c.ownerAttackStamp = 0;
        c.wolfAttackStamp = 0;
        System.out.println(PREFIX + "Coda joined the world as a tamed, defensive wolf: " + c.save.wolfId);
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
            if (current != null) game.target(wolf, null);
        } else if (current != c.aggressor) {
            game.target(wolf, c.aggressor);
        }
    }
}
