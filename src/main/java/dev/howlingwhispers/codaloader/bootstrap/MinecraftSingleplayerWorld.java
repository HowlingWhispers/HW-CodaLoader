package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import dev.howlingwhispers.codaloader.api.CodaSingleplayerWorld;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Experimental Snapshot 3 SINGLE-PLAYER inventory bridge.
 *
 * Only pre-loaded, ordinary chests and barrels are supported, never double
 * chest union containers or inventory wrappers. Only vanilla marker: glass; plus registered H.O.W.L. native blocks.
 * No client/network use, and no chunk loading or world generation.
 */
final class MinecraftSingleplayerWorld implements CodaSingleplayerWorld {
    private final Object level;
    private final Thread ownerThread;
    private final ClassLoader loader;

    MinecraftSingleplayerWorld(Object source) throws Exception {
        this(CommandReflection.call(Objects.requireNonNull(source, "source"), "getServer"),
                CommandReflection.call(source, "getLevel"));
    }

    /** Authoritative tick bridge: does not rely on a player command source. */
    MinecraftSingleplayerWorld(Object server, Object level) throws Exception {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(level, "level");
        Class<?> integrated = Class.forName(
                "net.minecraft.client.server.IntegratedServer", false,
                server.getClass().getClassLoader());
        if (!integrated.isInstance(server))
            throw new IllegalStateException("BuildCraft transfers require integrated single-player");
        this.level = level;
        this.loader = level.getClass().getClassLoader();
        this.ownerThread = Thread.currentThread();
    }

    private void checkThread() {
        if (Thread.currentThread() != ownerThread)
            throw new IllegalStateException("Inventory transfer must remain on integrated server thread");
    }

    private Object nativePos(CodaBlockPos pos) throws Exception {
        return Class.forName("net.minecraft.core.BlockPos", true, loader)
                .getConstructor(int.class, int.class, int.class)
                .newInstance(pos.x(), pos.y(), pos.z());
    }

    private Object chunk(CodaBlockPos pos) throws Exception {
        Object chunks = CommandReflection.call(level, "getChunkSource");
        return CommandReflection.call(chunks, "getChunkNow", pos.x() >> 4, pos.z() >> 4);
    }

    @Override
    public boolean isLoaded(CodaBlockPos pos) throws Exception {
        checkThread();
        return chunk(Objects.requireNonNull(pos, "pos")) != null;
    }

    @Override
    public boolean isBlock(CodaBlockPos pos, String blockId) throws Exception {
        checkThread();
        // Exact Snapshot 3 native block IDs. An unrecognized mod ID cannot
        // be assumed to be vanilla glass or a valid BuildCraft pipe.
        boolean registeredModBlock = dev.howlingwhispers.codaloader.api.CodaNativeContents
                .blocks().stream().anyMatch(def -> def.id().equals(blockId));
        if (!registeredModBlock && !java.util.Set.of("minecraft:glass",
                "buildcrafttransport:wood_item", "buildcrafttransport:cobblestone_item",
                "buildcraftcore:engine_redstone").contains(blockId))
            throw new IllegalArgumentException("Unregistered H.O.W.L. block lookup: " + blockId);
        Object loaded = chunk(Objects.requireNonNull(pos, "pos"));
        if (loaded == null) return false;
        Object state = CommandReflection.call(loaded, "getBlockState", nativePos(pos));
        // Preserve the legacy vanilla-glass fixture until native snapshot
        // registry test classes are available. New BuildCraft paths use the
        // real registered native block and never fall back to glass.
        if (blockId.equals("minecraft:glass")) {
            Class<?> blocks = Class.forName("net.minecraft.world.level.block.Blocks", true, loader);
            return (Boolean)CommandReflection.call(state, "is", blocks.getField("GLASS").get(null));
        }
        Object registry = Class.forName("net.minecraft.core.registries.BuiltInRegistries", true, loader)
                .getField("BLOCK").get(null);
        Class<?> idClass = Class.forName("net.minecraft.resources.Identifier", true, loader);
        Object id = idClass.getMethod("parse", String.class).invoke(null, blockId);
        Object candidate = registry.getClass().getMethod("getValue", idClass).invoke(registry, id);
        if (candidate == null) return false;
        // Defaulted block registries return minecraft:air for unknown keys.
        Object candidateId = registry.getClass().getMethod("getKey", Object.class).invoke(registry, candidate);
        if (!blockId.equals(candidateId.toString())) return false;
        return (Boolean) CommandReflection.call(state, "is", candidate);
    }

    private Object chest(CodaBlockPos pos) throws Exception {
        Object loaded = chunk(pos);
        if (loaded == null) return null;
        Object entity = CommandReflection.call(loaded, "getBlockEntity", nativePos(pos));
        if (entity == null) return null;
        String type = entity.getClass().getName();
        if (!type.equals("net.minecraft.world.level.block.entity.ChestBlockEntity")
                && !type.equals("net.minecraft.world.level.block.entity.BarrelBlockEntity"))
            return null;
        return entity;
    }

    private static int intCall(Object obj, String method, Object... arguments) throws Exception {
        return ((Number) CommandReflection.call(obj, method, arguments)).intValue();
    }

    /** Preflight the exact setter before committing anything. */
    private static void requireSetter(Object container, Class<?> stackType) throws IOException {
        for (Method method : container.getClass().getMethods()) {
            if (method.getName().equals("setItem") && method.getParameterCount() == 2
                    && method.getParameterTypes()[0] == int.class
                    && method.getParameterTypes()[1].isAssignableFrom(stackType)) return;
        }
        throw new IOException("Snapshot 3 chest item setter changed: transfer aborted safely");
    }

    @Override
    public int transfer(CodaBlockPos from, CodaBlockPos to, int maximum) throws Exception {
        checkThread();
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) throw new IllegalArgumentException("Two different chests are required");
        if (maximum < 1 || maximum > 64)
            throw new IllegalArgumentException("Transfer must move between 1 and 64 items");

        Object source = chest(from);
        Object destination = chest(to);
        if (source == null || destination == null)
            throw new IllegalArgumentException("Place ordinary single chests/barrels at both loaded endpoints");

        int srcSize = intCall(source, "getContainerSize");
        int dstSize = intCall(destination, "getContainerSize");
        if (srcSize < 1 || srcSize > 54 || dstSize < 1 || dstSize > 54)
            throw new IOException("Invalid vanilla chest/barrel size; refusing transfer");

        Class<?> stackType = Class.forName("net.minecraft.world.item.ItemStack", true, loader);
        Object empty = stackType.getField("EMPTY").get(null);
        requireSetter(source, stackType);
        requireSetter(destination, stackType);

        // Search for ONE stack transfer per pulse; full ItemStack data components
        // are copied, never translated to a registry name or dropped.
        for (int i = 0; i < srcSize; i++) {
            Object sourceStack = CommandReflection.call(source, "getItem", i);
            if ((Boolean) CommandReflection.call(sourceStack, "isEmpty")) continue;
            int available = intCall(sourceStack, "getCount");
            int stackLimit = intCall(sourceStack, "getMaxStackSize");
            if (available < 1 || stackLimit < 1) continue;
            for (int j = 0; j < dstSize; j++) {
                Object destStack = CommandReflection.call(destination, "getItem", j);
                boolean slotEmpty = (Boolean) CommandReflection.call(destStack, "isEmpty");
                if (!slotEmpty && !(Boolean) CommandReflection.call(
                        stackType, "isSameItemSameComponents", sourceStack, destStack)) continue;
                int occupied = slotEmpty ? 0 : intCall(destStack, "getCount");
                int slotMax = slotEmpty ? stackLimit : Math.min(stackLimit,
                        intCall(destStack, "getMaxStackSize"));
                int count = Math.min(maximum, Math.min(available, slotMax - occupied));
                if (count <= 0) continue;

                // Resolve every copy before touching world state.
                Object newSource = available == count ? empty
                        : CommandReflection.call(sourceStack, "copyWithCount", available - count);
                Object newDestination = CommandReflection.call(sourceStack, "copyWithCount",
                        occupied + count);
                Object oldSource = CommandReflection.call(sourceStack, "copy");
                Object oldDestination = CommandReflection.call(destStack, "copy");

                // No stale slot change is possible within one server-thread
                // command. Still rollback both inventories on a setter failure.
                try {
                    CommandReflection.call(destination, "setItem", j, newDestination);
                    CommandReflection.call(source, "setItem", i, newSource);
                    CommandReflection.call(destination, "setChanged");
                    CommandReflection.call(source, "setChanged");
                    return count;
                } catch (Exception error) {
                    Exception rollbackFailure = null;
                    try {
                        CommandReflection.call(source, "setItem", i, oldSource);
                        CommandReflection.call(destination, "setItem", j, oldDestination);
                        CommandReflection.call(source, "setChanged");
                        CommandReflection.call(destination, "setChanged");
                    } catch (Exception rollback) { rollbackFailure = rollback; }
                    IOException failure = new IOException("Transfer failed; original chest contents restored when possible", error);
                    if (rollbackFailure != null) failure.addSuppressed(rollbackFailure);
                    throw failure;
                }
            }
        }
        return 0;
    }
}
