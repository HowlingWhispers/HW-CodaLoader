package dev.howlingwhispers.codaloader.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Server session identity, monotonic tick count and optional ephemeral
 * read-only Minecraft world inspection. Views expire when this tick ends.
 */
public record CodaServerTickContext(String sessionId, long tick, Optional<CodaWorldView> world) {
    public CodaServerTickContext {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(world, "world");
        if (sessionId.isBlank() || tick < 1)
            throw new IllegalArgumentException("Invalid server tick context");
    }

    /** Existing fixture and external SDK users retain the two-argument form. */
    public CodaServerTickContext(String sessionId, long tick) {
        this(sessionId, tick, Optional.empty());
    }
}
