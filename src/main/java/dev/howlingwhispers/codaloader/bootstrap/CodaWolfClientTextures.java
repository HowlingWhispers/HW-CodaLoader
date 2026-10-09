package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.reflect.Field;

/**
 * Client-only texture selector for Coda's persistently marked wolf.
 * The server registers Coda's exact saved UUID with the shared H.O.W.L.
 * appearance API; vanilla tags do not necessarily sync to clients.
 * Non-Coda wolves keep the original WolfRenderState.texture.
 */
public final class CodaWolfClientTextures {
    private static volatile boolean mappingWarning;

    private CodaWolfClientTextures() {}

    /**
     * Direct entity renderer texture IDs must include textures/ and .png.
     * No atlas lookup occurs for the WolfRenderState.texture field.
     * Preserve fully-qualified resource locations for future mods, and
     * translate existing H.O.W.L. companion sprite IDs for compatibility.
     */
    static String wolfTextureLocation(String requested) {
        if (requested == null) throw new IllegalArgumentException("Missing wolf texture ID");
        int colon = requested.indexOf(':');
        if (colon < 1 || colon != requested.lastIndexOf(':'))
            throw new IllegalArgumentException("Wolf texture must have a namespace");
        String namespace = requested.substring(0, colon);
        String path = requested.substring(colon + 1);
        if (path.startsWith("textures/") && path.endsWith(".png"))
            return requested;
        if (path.startsWith("entity/") && !path.contains("..")
                && !path.endsWith(".png"))
            return namespace + ":textures/" + path + ".png";
        throw new IllegalArgumentException("Unsupported direct wolf texture path: " + requested);
    }

    /** Called after the exact Snapshot 3 WolfRenderer.extractRenderState. */
    public static void select(Object wolf, Object wolfRenderState) {
        if (wolf == null || wolfRenderState == null) return;
        try {
            Object uuid = wolf.getClass().getMethod("getUUID").invoke(wolf);
            if (!(uuid instanceof java.util.UUID id)) return;
            var registration = dev.howlingwhispers.codaloader.api.CodaEntityAppearance.wolfSkin(id);
            if (registration.isEmpty()) return;
            boolean angry = (Boolean)wolf.getClass().getMethod("isAngry").invoke(wolf);
            String texture = angry ? registration.get().angryTexture()
                                   : registration.get().tameTexture();
            ClassLoader loader = wolf.getClass().getClassLoader();
            Class<?> idType = Class.forName("net.minecraft.resources.Identifier",true,loader);
            // WolfRenderState.texture is a DIRECT PNG Identifier, not an
            // atlas sprite reference. e.g. namespace:textures/entity/wolf.png.
            // Older Companion versions registered namespace:entity/wolf;
            // normalize those logical paths instead of requesting a missing
            // resource and displaying Minecraft's magenta checkerboard.
            Object textureId = idType.getMethod("parse",String.class)
                    .invoke(null, wolfTextureLocation(texture));
            Field field = wolfRenderState.getClass().getField("texture");
            if (!idType.isAssignableFrom(field.getType()))
                throw new IllegalStateException("Snapshot 3 wolf render-state texture type changed");
            field.set(wolfRenderState,textureId);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!mappingWarning) {
                mappingWarning = true;
                System.err.println("[H.O.W.L.] Exclusive Coda wolf renderer mapping unavailable: " + ex);
            }
            // Refuse to replace textures on an unknown mapping; vanilla art survives.
        }
    }
}
