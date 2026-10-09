package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Client-only texture selector for Coda's persistently marked wolf.
 * The server owns the "howl.coda" vanilla entity tag and updates it only
 * after verifying CompanionSave.wolfId. No client/world telemetry or mod
 * dependency. Non-Coda wolves keep vanilla WolfRenderState.texture.
 */
public final class CodaWolfClientTextures {
    public static final String CODA_TAG = "howl.coda";
    private static volatile boolean mappingWarning;

    private CodaWolfClientTextures() {}

    /** Called after the exact Snapshot 3 WolfRenderer.extractRenderState. */
    public static void select(Object wolf, Object wolfRenderState) {
        if (wolf == null || wolfRenderState == null) return;
        try {
            Method tagsMethod = wolf.getClass().getMethod("getTags");
            Object tags = tagsMethod.invoke(wolf);
            if (!(tags instanceof Set<?> set) || !set.contains(CODA_TAG)) return;
            boolean angry = (Boolean)wolf.getClass().getMethod("isAngry").invoke(wolf);
            String texture = angry
                    ? "codawolf:entity/coda_angry"
                    : "codawolf:entity/coda_tame";
            ClassLoader loader = wolf.getClass().getClassLoader();
            Class<?> idType = Class.forName("net.minecraft.resources.Identifier",true,loader);
            Object id = idType.getMethod("parse",String.class).invoke(null,texture);
            Field field = wolfRenderState.getClass().getField("texture");
            if (!idType.isAssignableFrom(field.getType()))
                throw new IllegalStateException("Snapshot 3 wolf render-state texture type changed");
            field.set(wolfRenderState,id);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!mappingWarning) {
                mappingWarning = true;
                System.err.println("[H.O.W.L.] Exclusive Coda wolf renderer mapping unavailable: " + ex);
            }
            // Refuse to replace textures on an unknown mapping; vanilla art survives.
        }
    }
}
