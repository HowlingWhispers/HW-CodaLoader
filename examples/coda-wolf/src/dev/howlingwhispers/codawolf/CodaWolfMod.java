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

    private static final class Companion {
        final CompanionSave save;
        final CompanionRules.SleepGate sleep = new CompanionRules.SleepGate();
        Object cachedWolf;
        Object aggressor;
        long defendUntilTick;
        int ownerAttackStamp;
        int wolfAttackStamp;
        boolean disabled;
        Companion(CompanionSave save) { this.save=save; }
    }

    @Override public void onInitialize(CodaContext context) {
        context.registerServerTick("companion", this::tick);
        context.registerCommand("codawolf", "Inspect Coda's defensive wolf companion", (player,args) -> {
            if (args.size()>1 || (!args.isEmpty() && !List.of("status","help","defensive").contains(args.get(0)))) {
                player.reply("Usage: /codawolf [status|defensive|help]"); return;
            }
            if (!args.isEmpty() && args.get(0).equals("help")) {
                player.reply("Coda starts tamed, guards against attackers, retreats at low health, and returns after successful bed sleep. No AI/voice connection yet.");
                return;
            }
            CompanionSave save = CompanionSave.load(player.worldDirectory(),player.playerId());
            String status = !save.created ? "not spawned yet" : save.pendingRespawn ? "waiting for a successful bed sleep"
                    : save.wolfId==null ? "needs recovery; see logs" : "tracked wolf " + save.wolfId;
            player.reply("Coda: " + status + ". Combat: DEFENSIVE (fixed). "
                    + (bridgeUnsupported ? "Minecraft adapter unavailable, check logs." : ""));
        });
        System.out.println(PREFIX + "0.1.0-dev initialized, defensive default, singleplayer only");
    }

    private void tick(CodaServerTickContext tick) {
        if (serverSession == null || !serverSession.equals(tick.sessionId())) {
            serverSession = tick.sessionId(); serverIdentity=null; bridgeUnsupported=false; companions.clear();
        }
        if (bridgeUnsupported || tick.tick()%2!=0) return; // 10Hz, native Minecraft remains authoritative
        try {
            MinecraftWolfBridge game = new MinecraftWolfBridge();
            if (serverIdentity != game.serverIdentity()) {
                serverIdentity=game.serverIdentity(); companions.clear();
            }
            for (Object player : game.players()) {
                UUID owner = game.id(player);
                Companion companion = companions.get(owner);
                if (companion == null) {
                    companion = new Companion(CompanionSave.load(game.worldDirectory(),owner));
                    companions.put(owner,companion);
                }
                if (companion.disabled) continue;
                try { update(game,companion,player,tick.tick()); }
                catch (Exception ex) {
                    companion.disabled=true; // no endless retry/spawn flood or silent duplicate creation
                    System.err.println(PREFIX + "Disabled for player " + owner + " until next session: " + ex);
                }
            }
        } catch (Exception ex) {
            bridgeUnsupported=true;
            System.err.println(PREFIX + "Snapshot 3 singleplayer mapping unavailable; disabled without touching loader: " + ex);
        }
    }

    private void update(MinecraftWolfBridge game, Companion c, Object player, long tick) throws Exception {
        Object level=game.level(player);
        boolean slept=c.sleep.tick(game.sleeping(player),game.dayTime(level));
        if (!c.save.created) { create(game,c,player); return; }

        Object wolf=c.save.wolfId==null ? null : game.wolf(level,c.save.wolfId);
        if (wolf != null) c.cachedWolf=wolf;
        // A missing wolf may simply be in an unloaded chunk or another dimension.
        // Never spawn a second wolf in that case.
        if (c.save.wolfId!=null && c.cachedWolf!=null && game.dead(c.cachedWolf)) {
            c.save.wolfId=null; c.save.pendingRespawn=true; c.cachedWolf=null;
            c.save.persist();
            System.out.println(PREFIX + "Coda fell in combat. Bed sleep required for her return.");
        }
        if (c.save.pendingRespawn && slept) { create(game,c,player); return; }
        if (wolf != null && !c.save.pendingRespawn && !game.dead(wolf)) defend(game,c,player,wolf,tick);
    }

    private void create(MinecraftWolfBridge game, Companion c, Object player) throws Exception {
        Object wolf=game.spawn(player);
        UUID oldId=c.save.wolfId; boolean oldPending=c.save.pendingRespawn, oldCreated=c.save.created;
        c.save.wolfId=game.id(wolf); c.save.created=true; c.save.pendingRespawn=false;
        try { c.save.persist(); }
        catch (Exception ex) {
            c.save.wolfId=oldId; c.save.pendingRespawn=oldPending; c.save.created=oldCreated;
            try { game.discard(wolf); } catch (Exception error) { ex.addSuppressed(error); }
            throw ex;
        }
        c.cachedWolf=wolf;
        c.aggressor=null; c.defendUntilTick=0;
        c.ownerAttackStamp=0; c.wolfAttackStamp=0;
        System.out.println(PREFIX + "Coda joined the world as a tamed, defensive wolf: " + c.save.wolfId);
    }

    private void defend(MinecraftWolfBridge game, Companion c, Object player, Object wolf, long tick) throws Exception {
        int ownerStamp=game.lastAttackStamp(player), wolfStamp=game.lastAttackStamp(wolf);
        if (ownerStamp!=0 && ownerStamp!=c.ownerAttackStamp) {
            c.aggressor=game.lastAttacker(player); c.defendUntilTick=tick+100;
        }
        if (wolfStamp!=0 && wolfStamp!=c.wolfAttackStamp) {
            c.aggressor=game.lastAttacker(wolf); c.defendUntilTick=tick+100;
        }
        c.ownerAttackStamp=ownerStamp; c.wolfAttackStamp=wolfStamp;
        boolean recent=c.aggressor!=null && tick<=c.defendUntilTick && !game.dead(c.aggressor);
        boolean safe=CompanionRules.shouldDefend(recent,!game.dead(wolf),
                game.health(wolf),game.maxHealth(wolf),game.distance(wolf,player));
        if (safe) safe=game.distance(wolf,c.aggressor)<=CompanionRules.GUARD_RANGE_SQUARED;
        Object current=game.target(wolf);
        if (!safe) {
            if (current!=null) game.target(wolf,null); // no unprovoked vanilla target acquisition
        } else if (current!=c.aggressor) {
            game.target(wolf,c.aggressor);
        }
    }
}
