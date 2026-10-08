package dev.howlingwhispers.codaloader.api;

import java.util.List;
import java.util.Objects;

/**
 * Read-only slot OCCUPANCY for one loaded container.
 *
 * The first bridge intentionally avoids converting Minecraft ItemStacks to
 * lossy item IDs or stripping item components. Never use these counts to
 * implement extraction; actual movement requires a separate atomic adapter.
 */
public record CodaInventoryView(CodaBlockPos pos, List<Slot> slots) {
    public CodaInventoryView {
        Objects.requireNonNull(pos, "pos");
        slots = List.copyOf(slots);
        if (slots.size() > 256)
            throw new IllegalArgumentException("Too many inventory slots");
    }

    public record Slot(int index, int count, int maxStackSize) {
        public Slot {
            if (index < 0 || count < 0 || maxStackSize < 0 || count > maxStackSize)
                throw new IllegalArgumentException("Invalid inventory slot");
        }
    }
}
