package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaNativeContents;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Real Mojang 26.4 Snapshot 3 classes, not fixtures.
 * Tests registry bootstrap headlessly without launching Minecraft's GL UI.
 */
public final class NativeMinecraftSmoke {
    public static void main(String[] args) throws Exception {
        NativeFactoryMinecraftSmoke.declare();
        CodaNativeContents.registerBlock("buildcraft_cml", "buildcrafttransport:wood_item", 0.7f);
        CodaNativeContents.registerBlock("buildcraft_cml", "buildcrafttransport:cobblestone_item", 1.4f);
        CodaNativeContents.registerBlock("buildcraft_cml", "buildcraftcore:engine_redstone", 1.5f);
        CodaNativeContents.registerItem("buildcraft_cml", "buildcraftcore:wrench");
        CodaNativeContents.registerTab("buildcraft_cml", "buildcraftcore:buildcraft",
                "BuildCraft", "buildcrafttransport:wood_item",
                List.of("buildcrafttransport:wood_item", "buildcrafttransport:cobblestone_item",
                        "buildcraftcore:engine_redstone", "buildcraftcore:wrench"));

        ClassLoader loader = NativeMinecraftSmoke.class.getClassLoader();
        // Mojang's real launcher sets the current version before bootstrapping.
        // The headless test must do the same or DataFixers fails with
        // "Game version not set" before native registry hooks execute.
        Class<?> constants = Class.forName("net.minecraft.SharedConstants", true, loader);
        constants.getMethod("tryDetectVersion").invoke(null);
        Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap", true, loader);
        System.out.println("Bootstrapping EXACT Mojang Snapshot 3 registries...");
        bootstrap.getMethod("bootStrap").invoke(null);

        Class<?> registries = Class.forName(
                "net.minecraft.core.registries.BuiltInRegistries", true, loader);
        Class<?> idClass = Class.forName("net.minecraft.resources.Identifier", true, loader);
        Method parse = idClass.getMethod("parse", String.class);
        int checked = NativeFactoryMinecraftSmoke.verify();
        for (String name : List.of("buildcrafttransport:wood_item",
                "buildcrafttransport:cobblestone_item", "buildcraftcore:engine_redstone")) {
            Object blocks = registries.getField("BLOCK").get(null);
            Object id = parse.invoke(null, name);
            Object value = blocks.getClass().getMethod("getValue", idClass).invoke(blocks, id);
            if (value == null || blocks.getClass().getMethod("getKey", Object.class).invoke(blocks, value)
                    .toString().equals(name) == false) {
                throw new AssertionError("Native Block missing: " + name);
            }
            Class<?> blockClass = Class.forName("net.minecraft.world.level.block.Block", true, loader);
            Class<?> stateClass = Class.forName("net.minecraft.world.level.block.state.BlockState", true, loader);
            Object definition = blockClass.getMethod("getStateDefinition").invoke(value);
            Object states = definition.getClass().getMethod("getPossibleStates").invoke(definition);
            Object stateIds = blockClass.getField("BLOCK_STATE_REGISTRY").get(null);
            for (Object state : (Iterable<?>) states) {
                // The packet codec uses this exact IdMapper; exercise both
                // directions rather than only checking BLOCK registration.
                int stateId = (Integer) stateIds.getClass().getMethod("getId", Object.class)
                        .invoke(stateIds, state);
                if (stateId < 0 || stateIds.getClass().getMethod("byId", int.class)
                        .invoke(stateIds, stateId) != state)
                    throw new AssertionError("Block state has no packet ID: " + name);
                checked++;
                // Chunk rendering dereferences this initialized shape array.
                Class<?> direction = Class.forName("net.minecraft.core.Direction", true, loader);
                for (Object face : direction.getEnumConstants()) {
                    if (stateClass.getMethod("getFaceOcclusionShape", direction)
                            .invoke(state, face) == null)
                        throw new AssertionError("Block face shape cache missing: " + name);
                    checked++;
                }
            }
            // Real BlockItem must also be findable by Minecraft's
            // Item.byBlock registry, not just ITEM key lookups.
            Object nativeItem = Class.forName("net.minecraft.world.item.Item",true,loader)
                    .getMethod("byBlock",Class.forName("net.minecraft.world.level.block.Block",true,loader))
                    .invoke(null,value);
            Object itemRegistry = registries.getField("ITEM").get(null);
            String itemId = itemRegistry.getClass().getMethod("getKey",Object.class)
                    .invoke(itemRegistry,nativeItem).toString();
            if (!itemId.equals(name))
                throw new AssertionError("Native BuildCraft BlockItem mapping absent: "+name);
            checked++;
        }
        for (String name : List.of("buildcrafttransport:wood_item",
                "buildcrafttransport:cobblestone_item", "buildcraftcore:engine_redstone",
                "buildcraftcore:wrench")) {
            Object items = registries.getField("ITEM").get(null);
            Object id = parse.invoke(null, name);
            Object value = items.getClass().getMethod("getValue", idClass).invoke(items, id);
            if (value == null || !items.getClass().getMethod("getKey", Object.class)
                    .invoke(items, value).toString().equals(name))
                throw new AssertionError("Native item missing: " + name);
            checked++;
        }
        Object creative = registries.getField("CREATIVE_MODE_TAB").get(null);
        Object name = parse.invoke(null, "buildcraftcore:buildcraft");
        Object tab = creative.getClass().getMethod("getValue", idClass).invoke(creative, name);
        if (tab == null) throw new AssertionError("BuildCraft Creative tab not registered");
        checked++;
        // Link the transformed real screen without initializing graphics.
        // Combined with CreativeInventoryTest's execution fixture, this
        // checks the exact renderer's bytecode and JVM verification.
        Class.forName("net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen",
                false, loader).getDeclaredMethods();
        checked++;
        // Engine-powered routing needs these exact native methods. Verify
        // before publishing nightlies instead of discovering wrong signatures
        // after players load their worlds.
        Class<?> posType = Class.forName("net.minecraft.core.BlockPos",true,loader);
        Class.forName("net.minecraft.world.level.Level",true,loader)
            .getMethod("hasNeighborSignal",posType);
        checked++;
        Class<?> nativeStack = Class.forName("net.minecraft.world.item.ItemStack",true,loader);
        nativeStack.getMethod("getCount");
        nativeStack.getMethod("copyWithCount",int.class);
        nativeStack.getMethod("isSameItemSameComponents",nativeStack,nativeStack);
        checked += 3;
        Class<?> chest = Class.forName("net.minecraft.world.level.block.entity.ChestBlockEntity",true,loader);
        chest.getMethod("getContainerSize");
        chest.getMethod("getItem",int.class);
        chest.getMethod("setItem",int.class,nativeStack);
        checked += 3;
        System.out.println("PASS: " + checked
                + " actual Mojang Snapshot 3 native BuildCraft registry and transfer interface checks");
    }
}
