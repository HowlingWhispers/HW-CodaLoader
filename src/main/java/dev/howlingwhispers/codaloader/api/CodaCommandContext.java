package dev.howlingwhispers.codaloader.api;

import java.nio.file.Path;
import java.util.UUID;
import java.util.Optional;

/** Player-only command context. World data belongs in worldDirectory, never a global profile. */
public interface CodaCommandContext {
    @FunctionalInterface
    interface WorldAction { void run(CodaWorldView world) throws Exception; }
    /** Scoped world access for player commands, on the authoritative server thread. */
    default void withWorld(WorldAction action) throws Exception {
        throw new UnsupportedOperationException("This command context has no scoped world access");
    }
    UUID playerId() throws Exception;
    Path worldDirectory() throws Exception;
    CodaPosition position() throws Exception;
    /**
     * First outlined block under the player's crosshair, within vanilla interaction
     * reach (capped at eight blocks). Server thread only; never loads chunks.
     * Fluids are ignored. Empty means a miss or an unloaded ray corridor.
     */
    default Optional<CodaBlockTarget> targetBlock() throws Exception {
        throw new UnsupportedOperationException("This command context has no block targeting");
    }
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
