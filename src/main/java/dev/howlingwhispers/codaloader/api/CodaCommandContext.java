package dev.howlingwhispers.codaloader.api;

import java.nio.file.Path;
import java.util.UUID;

/** Player-only command context. World data belongs in worldDirectory, never a global profile. */
public interface CodaCommandContext {
    UUID playerId() throws Exception;
    Path worldDirectory() throws Exception;
    CodaPosition position() throws Exception;
    void reply(String message) throws Exception;
    /** Must refuse unsafe positions and unavailable dimensions without moving the player. */
    void teleport(CodaPosition destination) throws Exception;
}
