package dev.howlingwhispers.codaloader.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Strict early-load native content declarations. These are accepted BEFORE
 * vanilla's BuiltInRegistries bootstrap and materialized on the exact Snapshot
 * 3 registry lifecycle. No Forge/Fabric API or generic JVM reflection leaks
 * into third-party mods.
 */
public final class CodaNativeContents {
    public enum Kind { BLOCK, ITEM }
    public record Definition(String owner, String id, Kind kind, float hardness) {}
    public record Tab(String owner, String id, String title, String icon, List<String> items) {
        public Tab { items = List.copyOf(items); }
    }
    private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<String, Tab> TABS = new LinkedHashMap<>();
    private static boolean sealed;

    private CodaNativeContents() {}

    private static String requireId(String id) {
        Objects.requireNonNull(id);
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length() > 150)
            throw new IllegalArgumentException("Invalid namespaced Minecraft id: " + id);
        return id;
    }

    public static synchronized void registerBlock(String owner, String id, float hardness) {
        add(new Definition(requireId(owner), requireId(id), Kind.BLOCK, hardness));
        if (!Float.isFinite(hardness) || hardness < 0 || hardness > 100)
            throw new IllegalArgumentException("Block hardness out of range");
    }

    public static synchronized void registerItem(String owner, String id) {
        add(new Definition(requireId(owner), requireId(id), Kind.ITEM, 0));
    }

    private static void add(Definition def) {
        if (sealed) throw new IllegalStateException("Native registry declarations closed after Minecraft bootstrap");
        if (DEFINITIONS.putIfAbsent(def.id(), def) != null)
            throw new IllegalStateException("Duplicate native content id " + def.id());
    }

    public static synchronized void registerTab(String owner, String id, String title,
                                                String icon, List<String> items) {
        if (sealed) throw new IllegalStateException("Native registry declarations closed");
        requireId(owner);
        requireId(id);
        if (title == null || title.isBlank() || title.length() > 64)
            throw new IllegalArgumentException("Creative tab requires title");
        requireId(icon);
        for (String item : items) requireId(item);
        if (TABS.putIfAbsent(id, new Tab(owner, id, title, icon, items)) != null)
            throw new IllegalStateException("Duplicate Creative tab " + id);
    }

    public static synchronized List<Definition> blocks() {
        return DEFINITIONS.values().stream().filter(d -> d.kind() == Kind.BLOCK).toList();
    }
    public static synchronized List<Definition> items() {
        return List.copyOf(DEFINITIONS.values());
    }
    public static synchronized List<Tab> tabs() { return List.copyOf(TABS.values()); }
    public static synchronized void seal() { sealed = true; }
}
