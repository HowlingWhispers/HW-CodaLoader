package dev.howlingwhispers.codaloader.api;

/**
 * Experimental SINGLE-PLAYER server-thread block and chest interactions.
 * Phase one supports only a glass marker and ordinary vanilla chest/barrel
 * inventories. Not a general Minecraft block or storage registry.
 */
public interface CodaSingleplayerWorld {
    /** Do not request a chunk load. */
    boolean isLoaded(CodaBlockPos pos) throws Exception;

    /** Currently only minecraft:glass is a supported test marker. */
    boolean isBlock(CodaBlockPos pos, String blockId) throws Exception;

    /**
     * Transfer up to the requested number of items between already loaded
     * vanilla chest/barrel block entities. Full ItemStack components are
     * preserved. This must run on the authoritative integrated-server thread.
     *
     * May return zero when no compatible slot/room exists. Any API mismatch
     * fails before writing, rather than deleting or cloning item data.
     */
    int transfer(CodaBlockPos from, CodaBlockPos to, int maximum) throws Exception;
}
