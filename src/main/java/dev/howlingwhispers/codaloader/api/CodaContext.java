package dev.howlingwhispers.codaloader.api;

import java.nio.file.Path;
import java.util.List;

/** Read-only information supplied to a mod when it initializes. */
public record CodaContext(
        String loaderVersion,
        String minecraftVersion,
        Path gameDirectory,
        Path configDirectory,
        String modId,
        List<String> loadedModIds) {

    public CodaContext {
        loadedModIds = List.copyOf(loadedModIds);
    }

    /**
     * Declare native blocks before Snapshot 3's vanilla registries freeze.
     * H.O.W.L. owns the game-specific registration and Creative tab bridge.
     * An actual BlockItem is automatically registered for each block.
     */
    public void registerBlock(String namespacedId, float hardness) {
        CodaNativeContents.registerBlock(modId, namespacedId, hardness);
    }

    /**
     * Declare a genuine Snapshot 3 EntityBlock/BlockEntityType association
     * before native registries freeze. H.O.W.L. supplies the loader boundary;
     * BuildCraft still owns the eventual TilePipeHolder behavior.
     */
    public void registerBlockEntityType(String typeId, List<String> blocks) {
        CodaNativeContents.registerBlockEntityType(modId, typeId, blocks);
    }

    /**
     * Called by Minecraft's native server-side BlockEntityTicker for the
     * specified registered type. No client ticks, fake packets or loaded
     * chunk scans. Original machine logic remains the mod's responsibility.
     */
    public void registerBlockEntityTick(String typeId,
            java.util.function.Consumer<CodaBlockEntityTick> callback) {
        CodaNativeContents.registerBlockEntityTick(modId, typeId, callback);
    }

    /**
     * Supply an existing native Minecraft Block implementation, preserving
     * its real block states, geometry, interaction hooks, and EntityBlock.
     * The loader validates its Minecraft type when registries materialize.
     *
     * Register the block ID first; use this only once the original source
     * compiles against the actual Minecraft version.
     */
    public void registerNativeBlockFactory(String blockId,
            java.util.function.Supplier<?> originalMinecraftBlockFactory) {
        CodaNativeContents.registerNativeBlockFactory(
                modId, blockId, originalMinecraftBlockFactory);
    }

    /**
     * Supply an existing mod BlockEntity constructor with native BlockPos
     * and BlockState objects. No synthetic H.O.W.L. pipe state is generated.
     */
    public void registerNativeBlockEntityFactory(String typeId,
            java.util.function.BiFunction<Object,Object,Object> originalBlockEntityFactory) {
        CodaNativeContents.registerNativeBlockEntityFactory(
                modId, typeId, originalBlockEntityFactory);
    }

    public void registerItem(String namespacedId) {
        CodaNativeContents.registerItem(modId, namespacedId);
    }

    public void registerCreativeTab(String id, String title, String icon, List<String> entries) {
        CodaNativeContents.registerTab(modId, id, title, icon, entries);
    }

    /** Receive placement events from the authoritative Minecraft server. */
    public void registerBlockPlacement(java.util.function.Consumer<CodaBlockPlacements.Placement> listener) {
        CodaBlockPlacements.register(modId, listener);
    }

    /** Register a Minecraft-native screen factory for a namespaced screen ID. */
    public void registerScreen(String screenId, int priority, CodaScreens.ScreenFactory factory) {
        CodaScreens.global().register(new CodaScreens.Provider(screenId, modId, priority, factory));
    }

    public void registerCommand(String name, String description, CodaCommand command) {
        CodaCommands.register(modId, name, description, command);
    }

    /**
     * Server-thread callback. Registered during mod initialization and driven by
     * the strict Snapshot 3 tickServer hook. Not a client/render-thread callback.
     */
    public void registerServerTick(String listenerId, CodaServerTick callback) {
        CodaServerTicks.register(modId, listenerId, callback);
    }
}
