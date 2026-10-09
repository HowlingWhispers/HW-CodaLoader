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
        CodaNativeContents.registerBlock("buildcraft_cml", "buildcrafttransport:wood_item", 0.7f);
        CodaNativeContents.registerBlock("buildcraft_cml", "buildcrafttransport:cobblestone_item", 1.4f);
        CodaNativeContents.registerBlock("buildcraft_cml", "buildcraftcore:engine_redstone", 1.5f);
        CodaNativeContents.registerBlockEntityType("buildcraft_cml",
                "buildcrafttransport:pipe_holder", List.of(
                        "buildcrafttransport:wood_item",
                        "buildcrafttransport:cobblestone_item"));
        CodaNativeContents.registerBlockEntityTick("buildcraft_cml",
                "buildcrafttransport:pipe_holder",event -> {});
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
        int checked = 0;
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
        // Test the exact Snapshot 3 pipe-holder lifecycle, not a JVM-only
        // mock and not a fake mod-managed chest/packet database.
        Class<?> entityBlockInterface = Class.forName(
                "net.minecraft.world.level.block.EntityBlock", true, loader);
        Class<?> blockEntityClass = Class.forName(
                "net.minecraft.world.level.block.entity.BlockEntity", true, loader);
        Class<?> posClass = Class.forName("net.minecraft.core.BlockPos", true, loader);
        Class<?> stateClass = Class.forName(
                "net.minecraft.world.level.block.state.BlockState", true, loader);
        Object pos = posClass.getConstructor(int.class,int.class,int.class).newInstance(3,64,5);
        Object entityTypeRegistry = registries.getField("BLOCK_ENTITY_TYPE").get(null);
        Object pipeHolderId = parse.invoke(null,"buildcrafttransport:pipe_holder");
        Object pipeHolder = entityTypeRegistry.getClass().getMethod("getValue",idClass)
                .invoke(entityTypeRegistry,pipeHolderId);
        if (pipeHolder == null || !entityTypeRegistry.getClass()
                .getMethod("getKey",Object.class).invoke(entityTypeRegistry,pipeHolder)
                .toString().equals("buildcrafttransport:pipe_holder"))
            throw new AssertionError("Native BCCE pipe holder BlockEntityType unregistered");
        checked++;

        Object blocksRegistry = registries.getField("BLOCK").get(null);
        for (String pipeId : List.of("buildcrafttransport:wood_item",
                "buildcrafttransport:cobblestone_item")) {
            Object block = blocksRegistry.getClass().getMethod("getValue",idClass)
                    .invoke(blocksRegistry,parse.invoke(null,pipeId));
            if (!entityBlockInterface.isInstance(block))
                throw new AssertionError("BuildCraft pipe is not a native EntityBlock: " + pipeId);
            Object state = block.getClass().getMethod("defaultBlockState").invoke(block);
            Object entity = entityBlockInterface.getMethod("newBlockEntity",posClass,stateClass)
                    .invoke(block,pos,state);
            if (!blockEntityClass.isInstance(entity))
                throw new AssertionError("Minecraft did not construct a real BlockEntity: " + pipeId);
            Object foundType = blockEntityClass.getMethod("getType").invoke(entity);
            if (foundType != pipeHolder)
                throw new AssertionError("Wrong native TilePipeHolder type for " + pipeId);
            Class<?> entityTypeClass = Class.forName(
                    "net.minecraft.world.level.block.entity.BlockEntityType",true,loader);
            if (!(Boolean) entityTypeClass.getMethod("isValid",stateClass).invoke(pipeHolder,state))
                throw new AssertionError("Native pipe-holder type rejects its block state: " + pipeId);
            // Genuine Mojang EntityBlock getter must return the loader-backed
            // native BlockEntityTicker for a matching pipe-holder state.
            Class<?> worldClass = Class.forName("net.minecraft.world.level.Level",true,loader);
            Class<?> beTypeClass = Class.forName(
                    "net.minecraft.world.level.block.entity.BlockEntityType",true,loader);
            Class<?> nativeTickerClass = Class.forName(
                    "net.minecraft.world.level.block.entity.BlockEntityTicker",true,loader);
            Object ticker = entityBlockInterface.getMethod("getTicker",
                    worldClass,stateClass,beTypeClass).invoke(block,null,state,pipeHolder);
            if (!nativeTickerClass.isInstance(ticker))
                throw new AssertionError("Native Minecraft ticker unavailable for "+pipeId);
            checked += 5;
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
        // Verify EXACT live save-path contract before the engine persistence
        // feature reaches a player. Compile-only synthetic fixtures cannot
        // detect Snapshot 3's possible LevelResource/getWorldPath renames.
        // Coda Companion's sleep/respawn clock in Snapshot 3. The former
        // PrimaryLevelData.getDayTime() is no longer present in Mojang's API.
        Class<?> levelClock = Class.forName("net.minecraft.world.level.Level",true,loader);
        java.lang.reflect.Method timeMethod = levelClock.getMethod("getOverworldClockTime");
        if (timeMethod.getReturnType() != long.class)
            throw new AssertionError("Coda Wolf sleep clock must return long");
        checked += 1;

        Class<?> saveRoot = Class.forName("net.minecraft.world.level.storage.LevelResource",true,loader);
        Object root = saveRoot.getField("ROOT").get(null);
        if (root == null) throw new AssertionError("Missing Mojang world-save ROOT resource");
        Class<?> minecraftServer = Class.forName("net.minecraft.server.MinecraftServer",true,loader);
        java.lang.reflect.Method worldPath = minecraftServer.getMethod("getWorldPath",saveRoot);
        if (!java.nio.file.Path.class.isAssignableFrom(worldPath.getReturnType()))
            throw new AssertionError("World-save mapping does not return Path");
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
