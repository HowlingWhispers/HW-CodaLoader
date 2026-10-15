package dev.howlingwhispers.codaloader.api;

import java.util.concurrent.atomic.AtomicInteger;

/** Pure Java dispatch and exception-isolation tests for the client panel API. */
public final class CodaInventoryPanelsTest {
    public static void main(String[] args) {
        var count = new AtomicInteger();
        Object client = new Object(), screen = new Object();
        CodaInventoryPanels.register("first", "first:widgets", 10, (mc, s) -> {
            if (mc != client || s != screen) throw new AssertionError("Wrong caller");
            count.incrementAndGet();
        });
        CodaInventoryPanels.register("second", "second:widgets", 5, (mc, s) -> {
            throw new IllegalStateException("fixture exception");
        });
        CodaInventoryPanels.register("third", "third:widgets", 0, (mc, s) -> {
            count.incrementAndGet();
        });
        if (CodaInventoryPanels.registeredCount() != 3)
            throw new AssertionError("Expected three panels");
        CodaInventoryPanels.refresh(client, screen);
        CodaInventoryPanels.refresh(client, screen);
        if (count.get() != 4) throw new AssertionError("Panels not isolated");
        try {
            CodaInventoryPanels.register("other", "first:widgets", 0, (mc, s) -> {});
            throw new AssertionError("Expected owner validation");
        } catch (IllegalArgumentException expected) {}
        try {
            CodaInventoryPanels.register("first", "first:widgets", 0, (mc, s) -> {});
            throw new AssertionError("Expected duplicate protection");
        } catch (IllegalArgumentException expected) {}
        System.out.println("CodaInventoryPanelsTest passed");
    }
}
