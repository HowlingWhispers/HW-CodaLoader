package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaScreens;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Minecraft 26.4 screen handoff. Invoked only by native screen initialization hooks
 * on the game GUI thread. Failure leaves the current vanilla screen intact.
 */
public final class CodaScreenBridge {
    private static final Set<Object> ATTEMPTED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> SWITCHING = ThreadLocal.withInitial(() -> false);
    private static boolean warningPrinted;

    private CodaScreenBridge() {}

    public static void afterNativeInitialize(Object nativeScreen, String screenId) {
        if (SWITCHING.get() || CodaScreens.global().providers(screenId).isEmpty()) return;
        try {
            ClassLoader loader = nativeScreen.getClass().getClassLoader();
            Class<?> minecraftType = Class.forName("net.minecraft.client.Minecraft", false, loader);
            Object client = minecraftType.getMethod("getInstance").invoke(null);
            if (client == null) return;

            Class<?> screenType = Class.forName("net.minecraft.client.gui.screens.Screen", false, loader);
            Field active = null;
            for (Class<?> c = minecraftType; c != null && active == null; c = c.getSuperclass()) {
                try { active = c.getDeclaredField("screen"); }
                catch (NoSuchFieldException ignored) {}
            }
            if (active == null) return; // Unknown snapshot: fail closed.
            active.setAccessible(true);
            if (active.get(client) != nativeScreen) return;
            synchronized (ATTEMPTED) {
                if (!ATTEMPTED.add(nativeScreen)) return;
            }

            Object replacement = CodaScreens.global().resolve(screenId, client, nativeScreen);
            if (replacement == null || replacement == nativeScreen) return;
            if (!screenType.isInstance(replacement)) {
                report("Provider returned an object that is not a Minecraft Screen: "
                        + replacement.getClass().getName(), null);
                return;
            }
            Method setScreen = minecraftType.getMethod("setScreen", screenType);
            SWITCHING.set(true);
            try {
                if (active.get(client) == nativeScreen) {
                    setScreen.invoke(client, replacement);
                    System.out.println("[CodaLoader] Activated custom screen: " + screenId);
                }
            } finally {
                SWITCHING.remove();
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            report("Cannot activate custom screen " + screenId, ex);
        }
    }

    private static void report(String message, Throwable error) {
        if (!warningPrinted) {
            warningPrinted = true;
            System.err.println("[CodaLoader] " + message + (error == null ? "" : ": " + error));
        }
    }
}
