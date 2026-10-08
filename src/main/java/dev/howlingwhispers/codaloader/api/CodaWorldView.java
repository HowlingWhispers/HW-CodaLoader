package dev.howlingwhispers.codaloader.api;

import java.util.List;
import java.util.Optional;

/**
 * Safe Minecraft-side inspection on the authoritative server thread.
 *
 * A view is valid only during its originating server tick. It cannot load
 * chunks and does not expose mutable ItemStacks or world objects.
 */
public interface CodaWorldView {
    /** Real loaded dimensions, using canonical namespaced identifiers. */
    List<String> dimensions() throws Exception;

    /** Never loads a chunk; false includes not-loaded or missing dimension. */
    boolean isChunkLoaded(String dimension, CodaBlockPos pos) throws Exception;

    /**
     * Only inspect an already-loaded position. Empty means no accessible
     * inventory (including an unloaded chunk). Not a transactional adapter.
     */
    Optional<CodaInventoryView> inventory(String dimension, CodaBlockPos pos) throws Exception;
}
