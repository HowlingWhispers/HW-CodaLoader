package dev.howlingwhispers.codaloader.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** GUI-thread registry for client-side player inventory panels. */
public final class CodaInventoryPanels {
    @FunctionalInterface
    public interface Panel {
        void refresh(Object minecraftClient, Object inventoryScreen) throws Exception;
    }

    private record Entry(String id, String owner, int priority, Panel panel) {}
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Set<String> WARNED = new HashSet<>();
    private CodaInventoryPanels() {}

    public static synchronized void register(String modId, String panelId, int priority, Panel panel) {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(panelId, "panelId");
        Objects.requireNonNull(panel, "panel");
        if (modId.isBlank() || !panelId.startsWith(modId + ":")
                || panelId.length() <= modId.length() + 1)
            throw new IllegalArgumentException("Panel ID must be namespaced to owner mod");
        if (ENTRIES.stream().anyMatch(e -> e.id().equals(panelId)))
            throw new IllegalArgumentException("Duplicate inventory panel " + panelId);
        ENTRIES.add(new Entry(panelId, modId, priority, panel));
        ENTRIES.sort(Comparator.comparingInt(Entry::priority).reversed()
                .thenComparing(Entry::owner).thenComparing(Entry::id));
    }

    /** Called only on the Minecraft client GUI thread. */
    public static void refresh(Object client, Object screen) {
        if (client == null || screen == null) return;
        List<Entry> providers;
        synchronized (CodaInventoryPanels.class) { providers = List.copyOf(ENTRIES); }
        for (Entry provider : providers) {
            try {
                provider.panel().refresh(client, screen);
            } catch (Exception | LinkageError failure) {
                synchronized (CodaInventoryPanels.class) {
                    if (WARNED.add(provider.id()))
                        System.err.println("[H.O.W.L.] Inventory panel " + provider.id()
                                + " refresh failed: " + failure);
                }
            }
        }
    }

    public static synchronized int registeredCount() { return ENTRIES.size(); }
}
