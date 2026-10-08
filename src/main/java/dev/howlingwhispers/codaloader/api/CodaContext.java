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
