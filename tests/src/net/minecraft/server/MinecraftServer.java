package net.minecraft.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Version-pinned named-method fixtures, not real Minecraft classes. */
public class MinecraftServer {
    private int vanillaTicks;
    private final List<Level> worlds = new ArrayList<>();

    public void tickServer(BooleanSupplier keepTicking) {
        if (!keepTicking.getAsBoolean()) return;
        vanillaTicks++;
    }

    public int getVanillaTicks() {
        return vanillaTicks;
    }

    public void addWorld(Level world) { worlds.add(world); }
    public Iterable<Level> getAllLevels() { return worlds; }

    public record Dimension(String name) {
        public String location() { return name; }
    }

    public static final class Level {
        private final Dimension dimension;
        private final ChunkSource source = new ChunkSource();
        public Level(String dimension) { this.dimension = new Dimension(dimension); }
        public Dimension dimension() { return dimension; }
        public ChunkSource getChunkSource() { return source; }
    }

    public static final class ChunkSource {
        private final Map<String, Chunk> chunks = new HashMap<>();
        private int forcedLoads;

        public Chunk getChunkNow(int x, int z) {
            return chunks.get(x + "," + z);
        }
        public void addLoaded(int x, int z, Chunk chunk) {
            chunks.put(x + "," + z, chunk);
        }
        public int forcedLoads() { return forcedLoads; }
        public Chunk getChunk(int x, int z) {
            forcedLoads++;
            throw new AssertionError("Read-only bridge must never generate/load chunks");
        }
    }

    public static final class Chunk {
        private final Map<BlockPos, Object> entities = new HashMap<>();
        private final Map<BlockPos, net.minecraft.world.level.block.state.BlockState> blocks = new HashMap<>();
        public void put(BlockPos pos, Object entity) { entities.put(pos, entity); }
        public Object getBlockEntity(BlockPos pos) { return entities.get(pos); }
        public void putBlock(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            blocks.put(pos, state);
        }
        public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos,
                    new net.minecraft.world.level.block.state.BlockState(
                            net.minecraft.world.level.block.Blocks.STONE));
        }
    }

    public static final class Chest implements Container {
        private final List<ItemStack> contents;
        public Chest(ItemStack... items) { contents = List.of(items); }
        @Override public int getContainerSize() { return contents.size(); }
        @Override public ItemStack getItem(int slot) { return contents.get(slot); }
    }
}
