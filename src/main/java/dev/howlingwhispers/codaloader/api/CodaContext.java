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

    public void registerCommand(String name, String description, CodaCommand command) {
        CodaCommands.register(modId, name, description, command);
    }
}
