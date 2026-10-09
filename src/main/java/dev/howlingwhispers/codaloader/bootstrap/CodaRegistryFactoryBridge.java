package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaRegistryFactories;
import java.lang.reflect.Method;
import java.util.Map;

/** Registers the mod's own native classes, without constructing generic replacement blocks. */
final class CodaRegistryFactoryBridge {
    private static boolean complete;

    private CodaRegistryFactoryBridge() {}

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, true, CodaRegistryFactoryBridge.class.getClassLoader());
    }

    static synchronized void registerAll() {
        if (complete) return;
        var entries = CodaRegistryFactories.seal();
        if (entries.isEmpty()) { complete = true; return; }
        try {
            Class<?> identifier = type("net.minecraft.resources.Identifier");
            Class<?> resourceKey = type("net.minecraft.resources.ResourceKey");
            Class<?> registryType = type("net.minecraft.core.Registry");
            Object root = type("net.minecraft.core.registries.BuiltInRegistries").getField("REGISTRY").get(null);
            Method parse = identifier.getMethod("parse", String.class);
            Method createKey = resourceKey.getMethod("create", resourceKey, identifier);
            Method register = registryType.getMethod("register", registryType, resourceKey, Object.class);
            for (CodaRegistryFactories.Entry<?> entry : entries) {
                Object registryId = parse.invoke(null, entry.registry());
                // Only built-in, writable registries. World-specific dynamic
                // registries require a separate data-loading integration.
                if (!(Boolean) registryType.getMethod("containsKey", identifier).invoke(root, registryId))
                    throw new IllegalStateException("Unsupported built-in registry: " + entry.registry());
                Object registry = registryType.getMethod("getValue", identifier).invoke(root, registryId);
                Object id = parse.invoke(null, entry.id());
                if ((Boolean) registryType.getMethod("containsKey", identifier).invoke(registry, id))
                    throw new IllegalStateException("Refusing occupied native id: " + entry.id());
                Object registryKey = registryType.getMethod("key").invoke(registry);
                Object key = createKey.invoke(null, registryKey, id);
                registerEntry(entry, registry, key, register);
            }
            complete = true;
        } catch (Throwable failure) {
            throw new IllegalStateException("H.O.W.L. native factory registration failed before registry freeze", failure);
        }
    }

    private static <T> void registerEntry(CodaRegistryFactories.Entry<T> entry, Object registry,
                                          Object key, Method register) throws Exception {
        T value = entry.create(key);
        register.invoke(null, registry, key, value);
        if (entry.registry().equals("minecraft:block")) initializeBlock(value);
        if (entry.registry().equals("minecraft:item")) associateBlockItem(value);
        entry.bind(value);
        System.out.println("[H.O.W.L.] Native factory registered " + entry.registry() + "/" + entry.id()
                + " using " + value.getClass().getName());
    }

    private static void initializeBlock(Object block) throws Exception {
        Class<?> blockType = type("net.minecraft.world.level.block.Block");
        Object definition = blockType.getMethod("getStateDefinition").invoke(block);
        Object states = definition.getClass().getMethod("getPossibleStates").invoke(definition);
        Object ids = blockType.getField("BLOCK_STATE_REGISTRY").get(null);
        Method add = ids.getClass().getMethod("add", Object.class);
        for (Object state : (Iterable<?>) states) {
            state.getClass().getMethod("initCache").invoke(state);
            add.invoke(ids, state);
        }
    }

    private static void associateBlockItem(Object item) throws Exception {
        Class<?> blockItem = type("net.minecraft.world.item.BlockItem");
        if (!blockItem.isInstance(item)) return;
        Object block = blockItem.getMethod("getBlock").invoke(item);
        @SuppressWarnings("unchecked")
        Map<Object, Object> byBlock = (Map<Object, Object>) type("net.minecraft.world.item.Item")
                .getField("BY_BLOCK").get(null);
        if (byBlock.putIfAbsent(block, item) != null)
            throw new IllegalStateException("Native block-item mapping already occupied: " + block);
    }
}
