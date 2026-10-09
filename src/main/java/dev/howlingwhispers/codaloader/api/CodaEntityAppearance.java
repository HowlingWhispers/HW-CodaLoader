package dev.howlingwhispers.codaloader.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One integrated-singleplayer JVM's explicit companion appearance registry.
 *
 * Entity scoreboard tags are NOT guaranteed to synchronize to the client, so
 * render overrides rely on the exact server-verified entity UUID instead.
 * Mods register a skin after resolving their saved companion identity.
 *
 * Registry entries are ephemeral; Coda Companion refreshes them after save
 * restoration and removes them on world changes and confirmed death. Actual
 * PNGs still must exist in a separately enabled resourcepack.
 */
public final class CodaEntityAppearance {
    public record WolfSkin(String tameTexture, String angryTexture) {
        public WolfSkin {
            requireId(tameTexture);
            requireId(angryTexture);
        }
    }

    private static final ConcurrentHashMap<UUID,WolfSkin> WOLVES = new ConcurrentHashMap<>();

    private CodaEntityAppearance() {}

    public static void setWolfSkin(UUID entityId, String tameTexture, String angryTexture) {
        Objects.requireNonNull(entityId, "entityId");
        WOLVES.put(entityId,new WolfSkin(tameTexture,angryTexture));
    }

    public static Optional<WolfSkin> wolfSkin(UUID entityId) {
        return Optional.ofNullable(entityId == null ? null : WOLVES.get(entityId));
    }

    public static void clearWolfSkin(UUID entityId) {
        if (entityId != null) WOLVES.remove(entityId);
    }

    private static void requireId(String value) {
        if (value == null || value.length() > 192
                || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid H.O.W.L. entity skin asset identifier");
    }
}
