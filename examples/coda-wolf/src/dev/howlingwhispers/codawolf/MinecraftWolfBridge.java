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
    Object player(UUID ownerId) throws Exception {
        for (Object candidate : players()) {
            if (ownerId.equals(id(candidate))) return candidate;
        }
        return null;
    }
    Path worldDirectory() throws Exception {
        Object root = NativeCalls.field(NativeCalls.type("net.minecraft.world.level.storage.LevelResource",gameLoader),"ROOT");
        return ((Path) NativeCalls.call(server,"getWorldPath",root)).toAbsolutePath().normalize();
    }
    Object level(Object player) throws Exception { return NativeCalls.call(player,"level"); }
    long dayTime(Object level) throws Exception {
        // Snapshot 3's ServerLevel does not expose getDayTime() directly.
        // The level-data object is the authoritative daylight clock used
        // for night skipping; game time is NOT interchangeable here.
        try {
            return ((Number) NativeCalls.call(level, "getDayTime")).longValue();
        } catch (NoSuchMethodException notOnLevel) {
            Object levelData = NativeCalls.call(level, "getLevelData");
            return ((Number) NativeCalls.call(levelData, "getDayTime")).longValue();
        }
    }
    boolean sleeping(Object player) throws Exception { return (Boolean) NativeCalls.call(player,"isSleeping"); }
    Object wolf(Object level, UUID uuid) throws Exception {
        // getEntity is intentionally a loaded-entity lookup. Missing does NOT mean dead.
        return NativeCalls.call(level,"getEntity",uuid);
    }
    boolean dead(Object wolf) throws Exception {
        // isAlive() is enough to distinguish a loaded living entity. Do not
        // require a second snapshot-specific native method merely to spawn.
        return !(Boolean) NativeCalls.call(wolf,"isAlive");
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

    /**
     * Resolve the existing vanilla wolf entity by its registry id, not a
     * hard-coded static EntityType.WOLF field. Snapshot 3 no longer exposes
     * that field. No registry writes, entity registration or loader edits.
     */
    private static Object resolveWolfType(ClassLoader loader) throws Exception {
        Class<?> entityType = NativeCalls.type("net.minecraft.world.entity.EntityType", loader);
        try {
            Object constant = NativeCalls.field(entityType, "WOLF");
            if (entityType.isInstance(constant)) return constant;
            throw new IllegalStateException("EntityType.WOLF is not an EntityType");
        } catch (NoSuchFieldException absentOnSnapshot3) {
            Class<?> builtIns = NativeCalls.type(
                    "net.minecraft.core.registries.BuiltInRegistries", loader);
            Object registry = NativeCalls.field(builtIns, "ENTITY_TYPE");
            Class<?> identifier = NativeCalls.type("net.minecraft.resources.Identifier", loader);
            Object id = NativeCalls.call(identifier, "parse", "minecraft:wolf");
            Object value;
            try {
                value = NativeCalls.call(registry, "getValue", id);
            } catch (NoSuchMethodException noGetValue) {
                // Explicit second supported Registry lookup signature.
                value = NativeCalls.call(registry, "get", id);
            }
            if (value instanceof java.util.Optional<?> optional) value = optional.orElse(null);
            if (value == null || !entityType.isInstance(value))
                throw new IllegalStateException("Vanilla minecraft:wolf has no EntityType in Snapshot 3");
            return value;
        }
    }

    /** Spawn an ordinary vanilla Wolf and let vanilla pathfinding/following run. */
    Object spawn(Object player) throws Exception {
        Object level = level(player);
        Class<?> wolfClass;
        try { wolfClass = NativeCalls.type("net.minecraft.world.entity.animal.wolf.Wolf",gameLoader); }
        catch (ClassNotFoundException ex) {
            wolfClass = NativeCalls.type("net.minecraft.world.entity.animal.Wolf",gameLoader);
        }
        Object wolfType = resolveWolfType(gameLoader);
        Object wolf = NativeCalls.construct(wolfClass,wolfType,level);
        // Spawn in the same loaded, collision-free position the player occupies.
        // Offsetting into an unknown neighbouring block may embed the wolf in stone.
        NativeCalls.call(wolf,"setPos",
                ((Number) NativeCalls.call(player,"getX")).doubleValue(),
                ((Number) NativeCalls.call(player,"getY")).doubleValue(),
                ((Number) NativeCalls.call(player,"getZ")).doubleValue());
        // Snapshot 3's TamableAnimal no longer exposes setOwnerUUID(UUID).
        // Vanilla's tame(Player) establishes BOTH the owner reference and the
        // tamed flag with the game's own entity-reference system.
        NativeCalls.call(wolf,"tame",player);
        if (!Boolean.TRUE.equals(NativeCalls.call(wolf,"isTame"))
                || !Boolean.TRUE.equals(NativeCalls.call(wolf,"isOwnedBy",player)))
            throw new IllegalStateException("Minecraft did not bind Coda's wolf to the player");
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
