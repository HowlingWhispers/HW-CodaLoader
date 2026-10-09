package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaRegistryFactories;
import java.util.concurrent.atomic.AtomicInteger;

/** No Minecraft classes may be loaded while a mod declares its catalogs. */
public final class NativeFactoryDeclarationsTest {
    private static int checks;
    private static void check(boolean valid, String label) {
        checks++;
        if (!valid) throw new AssertionError(label);
    }
    private static void rejects(Runnable action, Class<? extends Exception> expected, String label) {
        try { action.run(); throw new AssertionError(label); }
        catch (Exception failure) { check(expected.isInstance(failure), label); }
    }
    public static void main(String[] args) throws Exception {
        AtomicInteger constructions = new AtomicInteger();
        Object actual = new Object();
        var block = CodaRegistryFactories.register("test", "minecraft:block", "test:machine", context -> {
            constructions.incrementAndGet();
            check(context.registry().equals("minecraft:block") && context.id().equals("test:machine"),
                    "Original factory receives its native identifiers");
            check(context.key() == actual, "Native key is passed unchanged");
            return actual;
        });
        var item = CodaRegistryFactories.register("test", "minecraft:item", "test:machine", context -> block.get());
        check(constructions.get() == 0 && !block.isBound(), "Declaration never calls constructors");
        rejects(block::get, IllegalStateException.class, "Early get must fail instead of constructing placeholders");
        rejects(() -> CodaRegistryFactories.register("test", "minecraft:block", "test:machine", c -> actual),
                IllegalArgumentException.class, "Duplicate native ID refused");
        rejects(() -> CodaRegistryFactories.register("test", "minecraft:block", "test:Wrong", c -> actual),
                IllegalArgumentException.class, "Invalid native identifier refused");
        var entries = CodaRegistryFactories.seal();
        check(entries.size() == 2 && entries.get(0) == block && entries.get(1) == item,
                "Dependency declaration order preserved across different registries");
        rejects(() -> CodaRegistryFactories.register("test", "minecraft:item", "test:late", c -> actual),
                IllegalStateException.class, "Registrations after the bootstrap boundary refused");
        block.bind(block.create(actual));
        item.bind(item.create(actual));
        check(block.get() == actual && item.get() == actual && constructions.get() == 1,
                "Original implementation constructed once and exposed by deferred references");
        rejects(() -> block.bind(actual), IllegalStateException.class, "A bound native entry cannot be replaced");
        System.out.println("PASS: " + checks + " deferred native factory API checks");
    }
}
