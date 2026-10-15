package dev.howlingwhispers.codaloader.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared H.O.W.L. recipe-book category declarations.
 * UI bridges consume snapshots, not live mod-owned collections.
 * Registration is only allowed during mod initialization.
 */
public final class CodaRecipeBookCategories {
    public record Category(String modId, String id, String title,
                           String icon, List<String> itemIds) {
        public Category {
            Objects.requireNonNull(modId);
            Objects.requireNonNull(id);
            Objects.requireNonNull(title);
            Objects.requireNonNull(icon);
            itemIds = List.copyOf(itemIds);
            // A mod ID is a namespace only; category IDs and items are namespaced resources.
            if (!modId.matches("[a-z0-9_.-]+") || !valid(id) || !valid(icon) || title.isBlank()
                    || itemIds.isEmpty() || itemIds.stream().anyMatch(s -> !valid(s))) {
                throw new IllegalArgumentException("Invalid recipe-book category");
            }
        }
    }

    private static final Map<String, Category> CATEGORIES = new LinkedHashMap<>();
    private static boolean frozen;

    private CodaRecipeBookCategories() {}

    private static boolean valid(String id) {
        return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    private static String namespace(String modId, String value) {
        if (value == null) throw new IllegalArgumentException("Null identifier");
        return value.contains(":") ? value : modId + ":" + value;
    }

    public static synchronized void register(String modId, String id, String title,
                                              String icon, List<String> itemIds) {
        if (frozen) throw new IllegalStateException("Recipe-book categories already frozen");
        if (modId == null || !modId.matches("[a-z0-9_.-]+"))
            throw new IllegalArgumentException("Invalid mod ID");
        String key = namespace(modId, id);
        String iconId = namespace(modId, icon);
        if (!key.startsWith(modId + ":"))
            throw new IllegalArgumentException("Recipe category must use its mod namespace");
        Category category = new Category(modId, key, title, iconId, itemIds);
        if (CATEGORIES.putIfAbsent(key, category) != null)
            throw new IllegalArgumentException("Duplicate recipe category: " + key);
    }

    /** Deterministic ordering avoids jitter across launches and UI rebuilds. */
    public static synchronized List<Category> snapshot() {
        return CATEGORIES.values().stream()
                .sorted(Comparator.comparing(Category::modId).thenComparing(Category::id))
                .toList();
    }

    public static synchronized void freeze() { frozen = true; }

    /** Test-only reset; never call after Minecraft starts loading screens. */
    public static synchronized void resetForTests() {
        CATEGORIES.clear();
        frozen = false;
    }
}
