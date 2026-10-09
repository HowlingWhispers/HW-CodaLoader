package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaRegistryFactories;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
            Method parse = identifier.getMethod("parse", String.class);
            Method createKey = resourceKey.getMethod("create", resourceKey, identifier);
            Method register = registryType.getMethod("register", registryType, resourceKey, Object.class);
            for (CodaRegistryFactories.Entry<?> entry : entries) {
                // Only built-in, writable registries. World-specific dynamic
                // registries require a separate data-loading integration.
                Object registry = builtInRegistry(entry.registry(), registryType, resourceKey);
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

    private static Object builtInRegistry(String id, Class<?> registryType, Class<?> keyType) throws Exception {
        // Snapshot 3's root registry holders are not bound until freeze starts.
        // Resolve the declared registry instances without reading those holders
        // or freezing the root early. Match the real registry key, not field names.
        for (var field : type("net.minecraft.core.registries.BuiltInRegistries").getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !registryType.isAssignableFrom(field.getType())) continue;
            Object registry = field.get(null);
            Object key = registryType.getMethod("key").invoke(registry);
            if (keyType.getMethod("identifier").invoke(key).toString().equals(id)) return registry;
        }
        throw new IllegalStateException("Unsupported built-in registry: " + id);
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
