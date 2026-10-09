package dev.howlingwhispers.codaloader.api;

import java.util.Objects;

/**
 * One tick of a Minecraft-owned native BlockEntity in a loaded server chunk.
 * This is a lifecycle signal only, not a surrogate inventory or pipe packet.
 */
public record CodaBlockEntityTick(
        String typeId, String dimension, CodaBlockPos position) {
    public CodaBlockEntityTick {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(position, "position");
        if (dimension == null ||
                !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid dimension ID");
    }
}
