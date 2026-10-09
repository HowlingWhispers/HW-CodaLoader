package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Client-only texture selector for Coda's persistently marked wolf.
 * The server registers Coda's exact saved UUID with the shared H.O.W.L.
 * appearance API; vanilla tags do not necessarily sync to clients.
 * Non-Coda wolves keep the original WolfRenderState.texture.
 */
public final class CodaWolfClientTextures {
    private static volatile boolean mappingWarning;

    private CodaWolfClientTextures() {}

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
            Object textureId = idType.getMethod("parse",String.class).invoke(null,texture);
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
