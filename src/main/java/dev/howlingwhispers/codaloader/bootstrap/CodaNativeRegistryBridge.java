package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaNativeContents;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Narrow, Minecraft 26.4 Snapshot 3 registry bridge.
 *
 * All registration runs at a vanilla bootstrap boundary, BEFORE registry
 * freeze. Defs are supplied by mods during javaagent premain. No registry
 * thawing, reflective override of a frozen registry, or overwriting existing
 * vanilla ids is permitted. Each phase fails closed if Mojang changes its
 * signatures. Minecraft classes are loaded only on the Minecraft JVM.
 */
public final class CodaNativeRegistryBridge {
    private static final Map<String, Object> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, Object> ITEMS = new LinkedHashMap<>();
    private static boolean blocksDone, itemsDone, creativeDone;

    private CodaNativeRegistryBridge() {}

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, true, CodaNativeRegistryBridge.class.getClassLoader());
    }
    private static Object invoke(Object object, String method, Class<?>[] types, Object... args)
            throws ReflectiveOperationException {
        return object.getClass().getMethod(method, types).invoke(object, args);
    }
    private static Object identifier(String name) throws ReflectiveOperationException {
        return type("net.minecraft.resources.Identifier").getMethod("parse", String.class)
                .invoke(null, name);
    }
    private static Object key(Object registry, String name) throws ReflectiveOperationException {
        Class<?> resourceKey = type("net.minecraft.resources.ResourceKey");
        Object registryKey = registry.getClass().getMethod("key").invoke(registry);
        return resourceKey.getMethod("create", resourceKey,
                type("net.minecraft.resources.Identifier"))
                .invoke(null, registryKey, identifier(name));
    }
    private static Object registry(String name) throws ReflectiveOperationException {
        return type("net.minecraft.core.registries.BuiltInRegistries").getField(name).get(null);
    }
    private static Object register(Object registry, String name, Object value)
            throws ReflectiveOperationException {
        Class<?> cls = type("net.minecraft.core.Registry");
        return cls.getMethod("register", cls, type("net.minecraft.resources.ResourceKey"),
                Object.class).invoke(null, registry, key(registry, name), value);
    }
    private static void requireFree(Object registry, String name)
            throws ReflectiveOperationException {
        if ((Boolean)registry.getClass().getMethod("containsKey",
                type("net.minecraft.resources.Identifier")).invoke(registry, identifier(name)))
            throw new IllegalStateException("Refusing occupied Minecraft id " + name);
    }

    /**
     * Mojang Snapshot 3 initializes Items DURING Blocks.<clinit>, while
     * calculating LightBlock shape caches. Hooking Items.<clinit> separately
     * therefore sees incomplete Blocks and crashes. Register both at the
     * RETURN of Blocks.<clinit>, while both vanilla registries are writable.
     */
    public static synchronized void registerBlocksAndItems() {
        registerBlocks();
        registerItems();
    }

    /** Register blocks after vanilla Block/Item static constructors complete. */
    private static synchronized void registerBlocks() {
        if (blocksDone) return;
        try {
            Object registry = registry("BLOCK");
            Class<?> properties = type("net.minecraft.world.level.block.state.BlockBehaviour$Properties");
            Class<?> blockClass = type("net.minecraft.world.level.block.Block");
            for (CodaNativeContents.Definition def : CodaNativeContents.blocks()) {
                requireFree(registry, def.id());
                Object p = properties.getMethod("of").invoke(null);
                p = properties.getMethod("setId", type("net.minecraft.resources.ResourceKey"))
                        .invoke(p, key(registry, def.id()));
                p = properties.getMethod("strength", float.class).invoke(p, def.hardness());
                // Correct for the original narrow pipe bodies, not a solid
                // suffocating cube. Actual BuildCraft models use release art.
                if (def.id().contains("item")) {
                    p = properties.getMethod("noOcclusion").invoke(p);
                }
                Object block = blockClass.getConstructor(properties).newInstance(p);
                register(registry, def.id(), block);
                BLOCKS.put(def.id(), block);
                System.out.println("[H.O.W.L.] Native block registered: " + def.id());
            }
            blocksDone = true;
        } catch (Throwable error) {
            System.err.println("[H.O.W.L.] FATAL: native block registry failed before freeze: " + error);
            error.printStackTrace(System.err);
            // Do not mark success or pretend items are available in Creative.
            throw new IllegalStateException("H.O.W.L. native block registry failed", error);
        }
    }

    /** Register Item and BlockItem entries after native blocks exist. */
    private static synchronized void registerItems() {
        if (itemsDone) return;
        try {
            Object registry = registry("ITEM");
            Class<?> properties = type("net.minecraft.world.item.Item$Properties");
            Class<?> block = type("net.minecraft.world.level.block.Block");
            Class<?> blockItem = type("net.minecraft.world.item.BlockItem");
            Class<?> item = type("net.minecraft.world.item.Item");
            for (CodaNativeContents.Definition def : CodaNativeContents.items()) {
                requireFree(registry, def.id());
                Object p = properties.getConstructor().newInstance();
                p = properties.getMethod("setId", type("net.minecraft.resources.ResourceKey"))
                        .invoke(p, key(registry, def.id()));
                Object value;
                if (def.kind() == CodaNativeContents.Kind.BLOCK) {
                    Object nativeBlock = BLOCKS.get(def.id());
                    if (nativeBlock == null)
                        throw new IllegalStateException("Block missing before item phase: " + def.id());
                    value = blockItem.getConstructor(block, properties).newInstance(nativeBlock, p);
                } else {
                    value = item.getConstructor(properties).newInstance(p);
                }
                register(registry, def.id(), value);
                ITEMS.put(def.id(), value);
                System.out.println("[H.O.W.L.] Native item registered: " + def.id());
            }
            itemsDone = true;
        } catch (Throwable error) {
            System.err.println("[H.O.W.L.] FATAL: native item registry failed before freeze: " + error);
            error.printStackTrace(System.err);
            throw new IllegalStateException("H.O.W.L. native item registry failed", error);
        }
    }

    /** Hook: before CreativeModeTabs.bootstrap(Registry) returns. */
    public static synchronized void registerCreativeTabs(Object registry) {
        if (creativeDone) return;
        try {
            Class<?> rowClass = type("net.minecraft.world.item.CreativeModeTab$Row");
            Class<?> creativeTab = type("net.minecraft.world.item.CreativeModeTab");
            Class<?> builderClass = type("net.minecraft.world.item.CreativeModeTab$Builder");
            Class<?> generator = type("net.minecraft.world.item.CreativeModeTab$DisplayItemsGenerator");
            Class<?> component = type("net.minecraft.network.chat.Component");
            Class<?> itemStack = type("net.minecraft.world.item.ItemStack");

            // Find a truly free bottom-row position so BuildCraft never hides
            // vanilla tabs. Fail loudly if Mojang exhausts tab space.
            int column = 0;
            Object all = registry.getClass().getMethod("stream").invoke(registry);
            try (java.util.stream.Stream<?> stream = (java.util.stream.Stream<?>)all) {
                for (Object tab : stream.toList()) {
                    Object row = creativeTab.getMethod("row").invoke(tab);
                    if (row.toString().equals("BOTTOM")) {
                        int index = (Integer)creativeTab.getMethod("column").invoke(tab);
                        column = Math.max(column, index + 1);
                    }
                }
            }
            Object bottom = rowClass.getField("BOTTOM").get(null);
            for (CodaNativeContents.Tab spec : CodaNativeContents.tabs()) {
                if (column >= 10) throw new IllegalStateException(
                        "No available Creative tab slots for " + spec.id());
                requireFree(registry, spec.id());
                Object builder = creativeTab.getMethod("builder", rowClass, int.class)
                        .invoke(null, bottom, column++);
                Object title = component.getMethod("literal", String.class)
                        .invoke(null, spec.title());
                builder = builderClass.getMethod("title", component).invoke(builder, title);

                Object iconItem = ITEMS.get(spec.icon());
                if (iconItem == null) throw new IllegalStateException("No tab icon: " + spec.icon());
                Supplier<Object> icon = () -> {
                    try { return iconItem.getClass().getMethod("getDefaultInstance").invoke(iconItem); }
                    catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
                };
                builder = builderClass.getMethod("icon", Supplier.class).invoke(builder, icon);

                Object display = Proxy.newProxyInstance(
                        generator.getClassLoader(), new Class<?>[]{generator},
                        (proxy, method, args) -> {
                            if (method.getName().equals("toString")) return "H.O.W.L. Creative " + spec.id();
                            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                            if (method.getName().equals("equals")) return proxy == args[0];
                            if (method.getName().equals("accept") && args.length == 2) {
                                Object output = args[1];
                                // Interface is public. Minecraft's internal
                                // output implementation can be package-private.
                                Method accept = type("net.minecraft.world.item.CreativeModeTab$Output")
                                        .getMethod("accept", itemStack);
                                for (String id : spec.items()) {
                                    Object entry = ITEMS.get(id);
                                    if (entry == null) throw new IllegalStateException("Missing tab item " + id);
                                    Object stack = entry.getClass().getMethod("getDefaultInstance").invoke(entry);
                                    accept.invoke(output, stack);
                                }
                            }
                            return null;
                        });
                builder = builderClass.getMethod("displayItems", generator).invoke(builder, display);
                Object tab = builderClass.getMethod("build").invoke(builder);
                register(registry, spec.id(), tab);
                System.out.println("[H.O.W.L.] Creative tab registered: " + spec.id());
            }
            CodaNativeContents.seal();
            creativeDone = true;
        } catch (Throwable error) {
            System.err.println("[H.O.W.L.] FATAL: Creative tab native hook failed: " + error);
            error.printStackTrace(System.err);
            throw new IllegalStateException("H.O.W.L. Creative native hook failed", error);
        }
    }
}
