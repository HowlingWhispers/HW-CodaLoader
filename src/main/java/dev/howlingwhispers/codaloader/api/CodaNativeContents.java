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

    /**
     * Native Minecraft block-entity type, not a parallel virtual inventory.
     * Multiple BuildCraft pipe blocks share the same original pipe-holder type.
     */
    public record BlockEntityDefinition(String owner, String id, List<String> blocks) {
        public BlockEntityDefinition {
            blocks = List.copyOf(blocks);
        }
    }
    private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<String, Tab> TABS = new LinkedHashMap<>();
    private static final Map<String, BlockEntityDefinition> BLOCK_ENTITIES = new LinkedHashMap<>();
    private static boolean sealed;

    private CodaNativeContents() {}

    private static String requireId(String id) {
        Objects.requireNonNull(id);
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length() > 150)
            throw new IllegalArgumentException("Invalid namespaced Minecraft id: " + id);
        return id;
    }

    private static String requireOwner(String owner) {
        if (owner == null || !owner.matches("[a-z0-9_.-]{1,64}"))
            throw new IllegalArgumentException("Invalid H.O.W.L. mod id " + owner);
        return owner;
    }

    public static synchronized void registerBlock(String owner, String id, float hardness) {
        if (!Float.isFinite(hardness) || hardness < 0 || hardness > 100)
            throw new IllegalArgumentException("Block hardness out of range");
        add(new Definition(requireOwner(owner), requireId(id), Kind.BLOCK, hardness));
    }

    public static synchronized void registerItem(String owner, String id) {
        add(new Definition(requireOwner(owner), requireId(id), Kind.ITEM, 0));
    }

    private static void add(Definition def) {
        if (sealed) throw new IllegalStateException("Native registry declarations closed after Minecraft bootstrap");
        if (DEFINITIONS.putIfAbsent(def.id(), def) != null)
            throw new IllegalStateException("Duplicate native content id " + def.id());
    }

    public static synchronized void registerTab(String owner, String id, String title,
                                                String icon, List<String> items) {
        if (sealed) throw new IllegalStateException("Native registry declarations closed");
        requireOwner(owner);
        requireId(id);
        if (title == null || title.isBlank() || title.length() > 64)
            throw new IllegalArgumentException("Creative tab requires title");
        requireId(icon);
        for (String item : items) requireId(item);
        if (TABS.putIfAbsent(id, new Tab(owner, id, title, icon, items)) != null)
            throw new IllegalStateException("Duplicate Creative tab " + id);
    }

    public static synchronized void registerBlockEntityType(String owner, String id, List<String> blocks) {
        requireOwner(owner);
        requireId(id);
        Objects.requireNonNull(blocks, "blocks");
        if (sealed) throw new IllegalStateException("Block entity declarations closed after bootstrap");
        if (blocks.isEmpty() || blocks.size() > 64 || blocks.stream().distinct().count() != blocks.size())
            throw new IllegalArgumentException("Block entity requires unique owned block IDs");
        for (String block : blocks) {
            requireId(block);
            Definition def = DEFINITIONS.get(block);
            if (def == null || def.kind() != Kind.BLOCK || !def.owner().equals(owner))
                throw new IllegalArgumentException("Block entity requires an owned registered block: " + block);
            if (BLOCK_ENTITIES.values().stream().anyMatch(other -> other.blocks().contains(block)))
                throw new IllegalArgumentException("Block already has a native block entity type: " + block);
        }
        if (BLOCK_ENTITIES.putIfAbsent(id, new BlockEntityDefinition(owner, id, blocks)) != null)
            throw new IllegalArgumentException("Duplicate block entity type " + id);
    }

    public static synchronized boolean hasBlockEntity(String blockId) {
        return BLOCK_ENTITIES.values().stream().anyMatch(type -> type.blocks().contains(blockId));
    }

    public static synchronized List<BlockEntityDefinition> blockEntityTypes() {
        return List.copyOf(BLOCK_ENTITIES.values());
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
