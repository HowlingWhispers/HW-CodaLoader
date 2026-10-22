package dev.howlingwhispers.codaloader.api;

import java.util.*;

/** Loaded-only, authoritative primitives for settlement mods. Valid only in its originating callback. */
public interface CodaLivingWorld {
    record Block(String id, Map<String,String> properties, boolean solid, boolean fluid) {
        public Block { properties = Map.copyOf(properties); }
        public boolean air() { return Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air").contains(id); }
    }
    record Actor(UUID id, double x, double y, double z, float health, boolean sleeping) {
        public double distanceSquared(CodaBlockPos p) {
            return Math.pow(x-p.x()-.5,2)+Math.pow(y-p.y(),2)+Math.pow(z-p.z()-.5,2);
        }
    }
    long clock() throws Exception;
    Optional<Block> block(CodaBlockPos pos) throws Exception;
    /** Empty includes unloaded entities. Never interpret absence as death or permission to respawn. */
    Optional<Actor> actor(UUID id) throws Exception;
    /** UUID is allocated and persisted by the mod before spawning. Duplicate UUIDs are refused. */
    boolean spawn(UUID id, String name, CodaBlockPos feet) throws Exception;
    boolean walk(UUID id, CodaBlockPos target, double speed) throws Exception;
    boolean sleep(UUID id, CodaBlockPos bed) throws Exception;
    void wake(UUID id) throws Exception;
    void damage(UUID id, float amount) throws Exception;
    void discard(UUID id) throws Exception;
    /** Only supported chest/barrel inventories. Exactly one real item is consumed, or nothing changes. */
    boolean eat(UUID id, CodaBlockPos stockpile, Set<String> food) throws Exception;
    /** Component-preserving transfers between the native actor inventory and a nearby chest/barrel. */
    int deliver(UUID id, CodaBlockPos stockpile) throws Exception;
    int carried(UUID id) throws Exception;
    int collect(UUID id, CodaBlockPos stockpile, CodaItemFilter filter, int maximum) throws Exception;
    boolean equip(UUID id, CodaBlockPos stockpile, Set<String> tools) throws Exception;
    /** Break one expected non-container block in reach, using an actual supplied tool and vanilla loot. */
    boolean harvest(UUID id, CodaBlockPos target, String expectedId, CodaBlockPos supplies,
                    Set<String> tools) throws Exception;
    /** Consume a real seed/sapling from actor inventory or nearby supplies; native survival checks apply. */
    boolean plant(UUID id, CodaBlockPos target, String itemId, CodaBlockPos supplies) throws Exception;
    void announce(String message) throws Exception;
}
