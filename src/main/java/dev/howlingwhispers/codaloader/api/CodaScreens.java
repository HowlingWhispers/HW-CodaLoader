package dev.howlingwhispers.codaloader.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Registry for CodaLoader-owned game screens.
 *
 * This is the loader-side API only. A Minecraft-specific screen adapter must
 * construct/render screens and invoke resolve() on the client GUI thread.
 * Nothing here changes Minecraft's currently displayed screen.
 */
public final class CodaScreens {
    public static final String TITLE = "minecraft:title";
    public static final String PAUSE = "minecraft:pause";
    public static final String SETTINGS = "minecraft:settings";
    public static final String WORLD_SELECT = "minecraft:world_select";
    private static final Set<String> RESERVED = Set.of(TITLE, PAUSE, SETTINGS, WORLD_SELECT);

    @FunctionalInterface
    public interface ScreenFactory {
        /** Return the native screen object, or null to defer to the next provider. */
        Object create(Object minecraftClient, Object previousScreen) throws Exception;
    }

    public record Provider(String screenId, String modId, int priority, ScreenFactory factory) {
        public Provider {
            Objects.requireNonNull(screenId, "screenId");
            Objects.requireNonNull(modId, "modId");
            Objects.requireNonNull(factory, "factory");
            if (screenId.isBlank() || modId.isBlank()) throw new IllegalArgumentException("Blank screen or mod ID");
            if (!screenId.contains(":") || screenId.startsWith(":") || screenId.endsWith(":"))
                throw new IllegalArgumentException("Screen ID must be namespaced (for example hw:clipboard)");
        }
    }

    private final List<Provider> providers = new ArrayList<>();

    /**
     * Register once at mod initialization. Higher priority wins. Equal priorities
     * are ordered by mod ID, making resolution independent of load order.
     */
    public synchronized void register(Provider provider) {
        Objects.requireNonNull(provider, "provider");
        if (providers.stream().anyMatch(p -> p.screenId().equals(provider.screenId())
                && p.modId().equals(provider.modId())))
            throw new IllegalArgumentException("Duplicate screen provider: " + provider.modId() + "/" + provider.screenId());
        providers.add(provider);
        providers.sort(Comparator.comparing(Provider::screenId)
                .thenComparing(Comparator.comparingInt(Provider::priority).reversed())
                .thenComparing(Provider::modId));
    }

    /** Immutable snapshot, safe for diagnostics and mod discovery. */
    public synchronized List<Provider> providers(String screenId) {
        return List.copyOf(providers.stream().filter(p -> p.screenId().equals(screenId)).toList());
    }

    /**
     * Called exclusively on the Minecraft GUI thread by a version-specific adapter.
     * Failures are isolated per provider; null indicates vanilla fallback.
     */
    public Object resolve(String screenId, Object client, Object previousScreen) {
        for (Provider provider : providers(screenId)) {
            try {
                Object screen = provider.factory().create(client, previousScreen);
                if (screen != null) return screen;
            } catch (Exception failure) {
                System.err.println("[CodaLoader] Screen provider " + provider.modId()
                        + " failed for " + screenId + ": " + failure);
            }
        }
        return null;
    }

    public static boolean isVanillaScreen(String screenId) {
        return RESERVED.contains(screenId);
    }
}
