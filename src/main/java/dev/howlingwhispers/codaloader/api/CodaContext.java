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

    public void registerItem(String namespacedId) {
        CodaNativeContents.registerItem(modId, namespacedId);
    }

    /**
     * Register a mod's original Minecraft-native implementation at bootstrap.
     * Factories execute before built-in registry freeze, in declaration order.
     * Block items, block entities and other entries are declared explicitly;
     * the loader never substitutes generic classes or creates implicit items.
     */
    public <T> CodaRegistryFactories.Entry<T> registerNativeRegistry(
            String registry, String id, CodaRegistryFactories.Factory<T> factory) {
        return CodaRegistryFactories.register(modId, registry, id, factory);
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
