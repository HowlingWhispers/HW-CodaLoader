package dev.howlingwhispers.codaloader.api;

import java.nio.file.Path;
import java.util.UUID;

/** Player-only command context. World data belongs in worldDirectory, never a global profile. */
public interface CodaCommandContext {
    UUID playerId() throws Exception;
    Path worldDirectory() throws Exception;
    CodaPosition position() throws Exception;
    void reply(String message) throws Exception;
    /**
     * Experimental: only available to integrated single-player server commands.
     * The returned object may only be used on the invoking server thread.
     */
    default CodaSingleplayerWorld singleplayerWorld() throws Exception {
        throw new UnsupportedOperationException("This H.O.W.L. command context has no single-player world access");
    }
    /** Must refuse unsafe positions and unavailable dimensions without moving the player. */
    void teleport(CodaPosition destination) throws Exception;
}
