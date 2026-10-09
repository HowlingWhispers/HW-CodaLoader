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
        // Verified against Mojang 26.4 Snapshot 3 named client:
        // Level.getOverworldClockTime():long replaces the deleted
        // ServerLevel.getDayTime()/PrimaryLevelData.getDayTime() API.
        // Bed-based recovery requires this clock, NOT getGameTime().
        return ((Number) NativeCalls.call(level, "getOverworldClockTime")).longValue();
    }
    /** A read-only Snapshot 3 block sample. Missing/unloaded blocks are skipped;
     * this never asks Minecraft to generate or load chunks. */
    CompanionAwareness.Observation observeBlock(Object wolf, int dx, int dy, int dz,
                                                 long tick) throws Exception {
        Object level = NativeCalls.call(wolf, "level");
        int cx = (int)Math.floor(((Number)NativeCalls.call(wolf,"getX")).doubleValue());
        int cy = (int)Math.floor(((Number)NativeCalls.call(wolf,"getY")).doubleValue());
        int cz = (int)Math.floor(((Number)NativeCalls.call(wolf,"getZ")).doubleValue());
        int x=cx+dx, y=cy+dy, z=cz+dz;
        Object pos=NativeCalls.construct(NativeCalls.type("net.minecraft.core.BlockPos",gameLoader),x,y,z);
        if (!Boolean.TRUE.equals(NativeCalls.call(level,"isLoaded",pos))) return null;
        Object state=NativeCalls.call(level,"getBlockState",pos);
        Object block=NativeCalls.call(state,"getBlock");
        Object registry=NativeCalls.field(
                NativeCalls.type("net.minecraft.core.registries.BuiltInRegistries",gameLoader),
                "BLOCK");
        String id=String.valueOf(NativeCalls.call(registry,"getKey",block));
        if (id.equals("minecraft:air") || id.equals("minecraft:cave_air")
                || id.equals("minecraft:void_air")) return null;
        Object key=NativeCalls.call(level,"dimension");
        String dimension=String.valueOf(NativeCalls.call(key,"location"));
        List<String> tags=new java.util.ArrayList<>();
        // Registry tags are best-effort metadata; an unknown custom tag may
        // not prevent the companion's base ID-based awareness from working.
        try {
            Object holder=NativeCalls.call(block,"builtInRegistryHolder");
            Object values=NativeCalls.call(holder,"tags");
            if (values instanceof java.util.stream.Stream<?> stream) {
                try(stream) {
                    for(Object tag:stream.limit(32).toList())
                        tags.add(String.valueOf(NativeCalls.call(tag,"location")));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException incompatibleTags) {
            // Registered block identity stays available on unknown tag APIs.
        }
        return new CompanionAwareness.Observation(dimension,id,tags,x,y,z,
                dx*dx+dy*dy+dz*dz,tick);
    }

    /** Built-in server system chat. No signed-player-chat spoofing and no network
     * call; Coda's text remains local to this player's singleplayer world. */
    void comment(Object owner, String message) throws Exception {
        Object component=NativeCalls.call(
                NativeCalls.type("net.minecraft.network.chat.Component",gameLoader),
                "literal", "[Coda 🐾] "+message);
        NativeCalls.call(owner,"sendSystemMessage",component);
    }

    boolean sleeping(Object player) throws Exception { return (Boolean) NativeCalls.call(player,"isSleeping"); }
    /** The only source of this tag is Coda's verified saved companion. */
    void markCoda(Object wolf) throws Exception {
        NativeCalls.call(wolf, "addTag", "howl.coda");
    }

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
        markCoda(wolf);
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
