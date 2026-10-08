package dev.howlingwhispers.codawolf;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Optional, strict singleplayer adapter for named Minecraft classes.
 * No private H.O.W.L. internals, transformers, packets, or loader modifications.
 * A mapping mismatch aborts this module's functionality without changing the game.
 */
final class MinecraftWolfBridge {
    private final ClassLoader gameLoader;
    private final Object server;
    MinecraftWolfBridge() throws Exception {
        gameLoader = MinecraftWolfBridge.class.getClassLoader();
        Class<?> clientClass = NativeCalls.type("net.minecraft.client.Minecraft", gameLoader);
        Object client = NativeCalls.call(clientClass, "getInstance");
        server = NativeCalls.call(client, "getSingleplayerServer");
        if (server == null || !server.getClass().getName().contains("IntegratedServer"))
            throw new IllegalStateException("Coda Wolf only runs in integrated singleplayer");
    }
    Object serverIdentity() { return server; }
    @SuppressWarnings("unchecked")
    List<Object> players() throws Exception {
        return (List<Object>) NativeCalls.call(NativeCalls.call(server,"getPlayerList"),"getPlayers");
    }
    UUID id(Object entity) throws Exception { return (UUID) NativeCalls.call(entity,"getUUID"); }
    Path worldDirectory() throws Exception {
        Object root = NativeCalls.field(NativeCalls.type("net.minecraft.world.level.storage.LevelResource",gameLoader),"ROOT");
        return ((Path) NativeCalls.call(server,"getWorldPath",root)).toAbsolutePath().normalize();
    }
    Object level(Object player) throws Exception { return NativeCalls.call(player,"level"); }
    long dayTime(Object level) throws Exception { return ((Number) NativeCalls.call(level,"getDayTime")).longValue(); }
    boolean sleeping(Object player) throws Exception { return (Boolean) NativeCalls.call(player,"isSleeping"); }
    Object wolf(Object level, UUID uuid) throws Exception {
        // getEntity is intentionally a loaded-entity lookup. Missing does NOT mean dead.
        return NativeCalls.call(level,"getEntity",uuid);
    }
    boolean dead(Object wolf) throws Exception {
        return !(Boolean) NativeCalls.call(wolf,"isAlive")
                || (Boolean) NativeCalls.call(wolf,"isDeadOrDying");
    }
    float health(Object entity) throws Exception {
        return ((Number) NativeCalls.call(entity,"getHealth")).floatValue();
    }
    float maxHealth(Object entity) throws Exception {
        return ((Number) NativeCalls.call(entity,"getMaxHealth")).floatValue();
    }
    double distance(Object a, Object b) throws Exception {
        return ((Number) NativeCalls.call(a,"distanceToSqr",b)).doubleValue();
    }
    Object target(Object wolf) throws Exception { return NativeCalls.call(wolf,"getTarget"); }
    void target(Object wolf, Object attacker) throws Exception { NativeCalls.call(wolf,"setTarget",attacker); }
    Object lastAttacker(Object entity) throws Exception { return NativeCalls.call(entity,"getLastHurtByMob"); }
    int lastAttackStamp(Object entity) throws Exception {
        return ((Number) NativeCalls.call(entity,"getLastHurtByMobTimestamp")).intValue();
    }

    /** Spawn an ordinary vanilla Wolf and let vanilla pathfinding/following run. */
    Object spawn(Object player) throws Exception {
        Object level = level(player);
        Class<?> wolfClass;
        try { wolfClass = NativeCalls.type("net.minecraft.world.entity.animal.wolf.Wolf",gameLoader); }
        catch (ClassNotFoundException ex) {
            wolfClass = NativeCalls.type("net.minecraft.world.entity.animal.Wolf",gameLoader);
        }
        Class<?> typeClass = NativeCalls.type("net.minecraft.world.entity.EntityType",gameLoader);
        Object wolfType = NativeCalls.field(typeClass,"WOLF");
        Object wolf = NativeCalls.construct(wolfClass,wolfType,level);
        NativeCalls.call(wolf,"setPos",
                ((Number) NativeCalls.call(player,"getX")).doubleValue()+1.5,
                ((Number) NativeCalls.call(player,"getY")).doubleValue(),
                ((Number) NativeCalls.call(player,"getZ")).doubleValue()+1.5);
        NativeCalls.call(wolf,"setOwnerUUID",id(player));
        try { NativeCalls.call(wolf,"setTame",true,true); }
        catch (NoSuchMethodException ex) { NativeCalls.call(wolf,"setTame",true); }
        NativeCalls.call(wolf,"setOrderedToSit",false);
        Object text = NativeCalls.call(NativeCalls.type("net.minecraft.network.chat.Component",gameLoader),"literal","Coda");
        NativeCalls.call(wolf,"setCustomName",text);
        NativeCalls.call(wolf,"setCustomNameVisible",true);
        NativeCalls.call(wolf,"setPersistenceRequired");
        // Cosmetic collar. Custom ice-white fur needs a dedicated resource pack later.
        try {
            Object cyan = NativeCalls.field(NativeCalls.type("net.minecraft.world.item.DyeColor",gameLoader),"CYAN");
            NativeCalls.call(wolf,"setCollarColor",cyan);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException ignored) { }
        NativeCalls.call(wolf,"setHealth",maxHealth(wolf));
        if (!Boolean.TRUE.equals(NativeCalls.call(level,"addFreshEntity",wolf)))
            throw new IllegalStateException("Minecraft refused to spawn the Coda wolf");
        return wolf;
    }
    void discard(Object wolf) throws Exception { NativeCalls.call(wolf,"discard"); }
}
