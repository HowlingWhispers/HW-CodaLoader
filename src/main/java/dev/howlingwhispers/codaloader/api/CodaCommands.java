package dev.howlingwhispers.codaloader.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Small loader-owned registry consumed by the integrated-server command bridge. */
public final class CodaCommands {
    public record Registration(String owner, String name, String description, CodaCommand command) {}
    private static final Map<String, Registration> COMMANDS = new LinkedHashMap<>();
    private CodaCommands() {}

    public static synchronized void register(String owner, String name, String description, CodaCommand command) {
        if (owner == null || owner.isBlank() || name == null || !name.matches("[a-z][a-z0-9_-]{0,31}"))
            throw new IllegalArgumentException("Invalid command owner or name");
        Objects.requireNonNull(command, "command");
        if (COMMANDS.containsKey(name)) throw new IllegalStateException("Command already registered: /" + name);
        COMMANDS.put(name, new Registration(owner, name, Objects.requireNonNull(description), command));
    }

    public static synchronized List<Registration> registrations() {
        return List.copyOf(COMMANDS.values());
    }
}
