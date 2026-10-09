package dev.howlingwhispers.codaloader.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Safe Minecraft-side inspection on the authoritative server thread.
 *
 * A view is valid only during its originating server tick. It cannot load
 * chunks and does not expose mutable ItemStacks or world objects.
 */
public interface CodaWorldView {
    /**
     * Server-owned world save root for persistent mod state. Available only
     * while this view is alive on its authoritative server thread. A missing
     * root in a third-party fixture means persistence is unavailable, not
     * permission to use a global profile path.
     */
    default Optional<Path> worldDirectory() throws Exception {
        return Optional.empty();
    }

    /** Real loaded dimensions, using canonical namespaced identifiers. */
    List<String> dimensions() throws Exception;

    /** Never loads a chunk; false includes not-loaded or missing dimension. */
    boolean isChunkLoaded(String dimension, CodaBlockPos pos) throws Exception;

    /**
     * Only inspect an already-loaded position. Empty means no accessible
     * inventory (including an unloaded chunk). Not a transactional adapter.
     */
    Optional<CodaInventoryView> inventory(String dimension, CodaBlockPos pos) throws Exception;

    /** Native block registration check, only in loaded chunks. */
    default boolean isBlock(String dimension, CodaBlockPos pos, String blockId) throws Exception {
        throw new UnsupportedOperationException("Snapshot transport world adapter unavailable");
    }

    /** Powered redstone engine detection, server thread only. */
    default boolean hasNeighborSignal(String dimension, CodaBlockPos pos) throws Exception {
        throw new UnsupportedOperationException("Snapshot redstone adapter unavailable");
    }

    /** Atomic vanilla chest/barrel transfer, never virtual inventories. */
    default int transfer(String dimension, CodaBlockPos source,
                         CodaBlockPos target, int maxItems) throws Exception {
        throw new UnsupportedOperationException("Snapshot native inventory write adapter unavailable");
    }
}
