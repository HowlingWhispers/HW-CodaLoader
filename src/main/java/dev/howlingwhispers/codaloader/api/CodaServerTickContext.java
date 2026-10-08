package dev.howlingwhispers.codaloader.api;

import java.util.Objects;

/**
 * Opaque world-session identity and monotonic tick count.
 * A new MinecraftServer instance receives a new session ID.
 * No live Minecraft objects are exposed through this provisional API.
 */
public record CodaServerTickContext(String sessionId, long tick) {
    public CodaServerTickContext {
        Objects.requireNonNull(sessionId, "sessionId");
        if (sessionId.isBlank() || tick < 1) throw new IllegalArgumentException("Invalid server tick context");
    }
}
