package dev.howlingwhispers.codaloader.api;

import java.util.Set;
import java.util.Objects;

/** Exact registry-ID selection. Never reconstructs or strips ItemStack components. */
public record CodaItemFilter(Mode mode, Set<String> items) {
    public enum Mode { WHITELIST, BLACKLIST }
    public CodaItemFilter {
        Objects.requireNonNull(mode);
        items = Set.copyOf(items);
        if (items.size() > 64) throw new IllegalArgumentException("At most 64 filter items");
        for (String id : items) if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Expected namespaced item ID: " + id);
    }
    public boolean allows(String id) { return mode == Mode.WHITELIST == items.contains(id); }
    public static CodaItemFilter all() { return new CodaItemFilter(Mode.BLACKLIST, Set.of()); }
    public static CodaItemFilter only(String id) { return new CodaItemFilter(Mode.WHITELIST, Set.of(id)); }
}
