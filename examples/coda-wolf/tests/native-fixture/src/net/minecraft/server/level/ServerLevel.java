package net.minecraft.server.level;
import java.util.*;
import net.minecraft.world.entity.animal.wolf.Wolf;
/** Snapshot fixture deliberately has NO ServerLevel.getDayTime method. */
public final class ServerLevel {
    public final LevelData levelData = new LevelData();
    public final Map<UUID,Object> entities = new HashMap<>();
    public LevelData getLevelData() { return levelData; }
    public Object getEntity(UUID id) { return entities.get(id); }
    public boolean addFreshEntity(Wolf wolf) { return entities.putIfAbsent(wolf.getUUID(), wolf) == null; }
    public static final class LevelData {
        public long getDayTime() { return 12345L; }
    }
}
