package dev.howlingwhispers.codaloader.api;

import java.util.List;
import java.util.Objects;

/** Immutable, tick-scoped snapshot of supported vanilla chest/barrel slots. */
public record CodaItemInventoryView(CodaBlockPos pos, List<Slot> slots) {
    public record Slot(int index, String itemId, int count, int maximum) {
        public Slot {
            Objects.requireNonNull(itemId);
            if (index < 0 || count < 0 || maximum < 0 || count > maximum)
                throw new IllegalArgumentException("Invalid item slot");
        }
    }
    public CodaItemInventoryView { Objects.requireNonNull(pos); slots = List.copyOf(slots); }
    public long count(String id) { return slots.stream().filter(s -> s.itemId().equals(id)).mapToLong(Slot::count).sum(); }
}
