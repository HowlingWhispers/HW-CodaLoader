package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import dev.howlingwhispers.codaloader.api.CodaInventoryView;
import dev.howlingwhispers.codaloader.api.CodaWorldView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Strict Snapshot 3 named-mapping read-only adapter, with NO chunk loading.
 * Invoked only within a native MinecraftServer tick callback.
 *
 * Refuse an unavailable mapping rather than try aliases which could trigger a
 * chunk load or read world data from another thread.
 */
public final class MinecraftWorldInventoryView implements CodaWorldView, AutoCloseable {
    private final Object server;
    private final Thread serverThread;
    private boolean active = true;

    public MinecraftWorldInventoryView(Object server) {
        this.server = Objects.requireNonNull(server, "server");
        this.serverThread = Thread.currentThread();
    }

    private void requireActive() {
        if (!active || Thread.currentThread() != serverThread)
            throw new IllegalStateException("World inventory views are server-thread tick scoped");
    }

    @Override
    public Optional<java.nio.file.Path> worldDirectory() throws Exception {
        requireActive();
        // Exact Minecraft 26.4 Snapshot 3 save-root API. Never reconstruct
        // paths from level names, game profiles, or an active user's identity.
        Class<?> resource = Class.forName("net.minecraft.world.level.storage.LevelResource",
                true, server.getClass().getClassLoader());
        Object root = resource.getField("ROOT").get(null);
        Object resolved = CommandReflection.call(server, "getWorldPath", root);
        if (!(resolved instanceof java.nio.file.Path path))
            throw new IllegalStateException("Snapshot 3 getWorldPath(ROOT) is not a Path");
        java.nio.file.Path normal = path.toAbsolutePath().normalize();
        if (!java.nio.file.Files.isDirectory(normal) || java.nio.file.Files.isSymbolicLink(normal))
            throw new java.io.IOException("Refusing invalid world save root for H.O.W.L. mod state");
        return Optional.of(normal);
    }

    /** No Minecraft references escape into a mod's public API. */
    @Override
    public List<String> dimensions() throws Exception {
        requireActive();
        List<String> names = new ArrayList<>();
        for (Object level : levels()) names.add(dimensionId(level));
        return List.copyOf(names);
    }

    @Override
    public boolean isChunkLoaded(String dimension, CodaBlockPos pos) throws Exception {
        requireActive();
        Objects.requireNonNull(pos, "pos");
        Object level = findLevel(dimension);
        if (level == null) return false;
        return existingChunk(level, pos) != null;
    }

    @Override
    public Optional<CodaInventoryView> inventory(String dimension, CodaBlockPos pos) throws Exception {
        requireActive();
        Objects.requireNonNull(pos, "pos");
        Object level = findLevel(dimension);
        if (level == null) return Optional.empty();

        // getChunkNow is a non-generating, non-loading lookup; do not replace
        // with getChunk(...) or getBlockEntity(...) on the level itself.
        Object chunk = existingChunk(level, pos);
        if (chunk == null) return Optional.empty();

        ClassLoader loader = level.getClass().getClassLoader();
        Class<?> minecraftPos = Class.forName("net.minecraft.core.BlockPos", true, loader);
        Object blockPos = minecraftPos.getConstructor(int.class, int.class, int.class)
                .newInstance(pos.x(), pos.y(), pos.z());
        Object blockEntity = CommandReflection.call(chunk, "getBlockEntity", blockPos);
        if (blockEntity == null) return Optional.empty();

        Class<?> container = Class.forName("net.minecraft.world.Container", true, loader);
        if (!container.isInstance(blockEntity)) return Optional.empty();
        int size = ((Number) CommandReflection.call(blockEntity, "getContainerSize")).intValue();
        if (size < 0 || size > 256)
            throw new IllegalStateException("Invalid Snapshot 3 inventory size: " + size);
        List<CodaInventoryView.Slot> slots = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            Object item = CommandReflection.call(blockEntity, "getItem", index);
            if (item == null) throw new IllegalStateException("Invalid null Minecraft item stack");
            int count = ((Number) CommandReflection.call(item, "getCount")).intValue();
            int maximum = ((Number) CommandReflection.call(item, "getMaxStackSize")).intValue();
            slots.add(new CodaInventoryView.Slot(index, count, maximum));
        }
        return Optional.of(new CodaInventoryView(pos, slots));
    }

    @Override
    public boolean isBlock(String dimension, CodaBlockPos pos, String blockId) throws Exception {
        requireActive();
        Object level = findLevel(dimension);
        if (level == null) return false;
        return new MinecraftSingleplayerWorld(server, level).isBlock(pos, blockId);
    }

    @Override
    public boolean hasNeighborSignal(String dimension, CodaBlockPos pos) throws Exception {
        requireActive();
        Object level = findLevel(dimension);
        if (level == null || existingChunk(level, pos) == null) return false;
        Class<?> nativePos = Class.forName("net.minecraft.core.BlockPos", true,
                level.getClass().getClassLoader());
        Object at = nativePos.getConstructor(int.class,int.class,int.class)
                .newInstance(pos.x(),pos.y(),pos.z());
        return (Boolean)CommandReflection.call(level, "hasNeighborSignal", at);
    }

    @Override
    public int transfer(String dimension, CodaBlockPos from, CodaBlockPos to,
                        int maximum) throws Exception {
        requireActive();
        Object level = findLevel(dimension);
        if (level == null || existingChunk(level, from) == null
                || existingChunk(level, to) == null) return 0;
        return new MinecraftSingleplayerWorld(server, level).transfer(from, to, maximum);
    }

    private Iterable<?> levels() throws Exception {
        Object levels = CommandReflection.call(server, "getAllLevels");
        if (!(levels instanceof Iterable<?> result))
            throw new IllegalStateException("Snapshot 3 server levels are not iterable");
        return result;
    }

    private Object findLevel(String name) throws Exception {
        if (name == null || !name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Expected a namespaced Minecraft dimension");
        for (Object level : levels()) {
            if (dimensionId(level).equals(name)) return level;
        }
        return null;
    }

    private static String dimensionId(Object level) throws Exception {
        Object dimensionKey = CommandReflection.call(level, "dimension");
        try {
            return CommandReflection.call(dimensionKey, "identifier").toString();
        } catch (IllegalArgumentException fixtureOnly) {
            // Older JVM fixture names used location; the official Snapshot 3
            // ResourceKey was renamed to identifier(). Production chooses it.
            return CommandReflection.call(dimensionKey, "location").toString();
        }
    }

    private static Object existingChunk(Object level, CodaBlockPos pos) throws Exception {
        Object source = CommandReflection.call(level, "getChunkSource");
        return CommandReflection.call(source, "getChunkNow", pos.x() >> 4, pos.z() >> 4);
    }

    @Override
    public void close() {
        requireActive();
        active = false;
    }
}
