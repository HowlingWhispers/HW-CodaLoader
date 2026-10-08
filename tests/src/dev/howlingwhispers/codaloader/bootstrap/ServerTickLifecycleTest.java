package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaContext;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Tests actual JVM bytecode interception of a named MinecraftServer fixture. */
public final class ServerTickLifecycleTest {
    private static int checks;

    private static void check(boolean pass, String reason) {
        checks++;
        if (!pass) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        var first = new MinecraftServer();
        var second = new MinecraftServer();
        var frames = new ArrayList<String>();
        var numbers = new ArrayList<Long>();
        var healthyCalls = new AtomicInteger();
        var poisonCalls = new AtomicInteger();
        var context = new CodaContext("0.0.26", "26.4-snapshot-3", Path.of("run"),
                Path.of("run/config/test"), "tick_test", List.of("tick_test"));
        context.registerServerTick("poison", tick -> {
            poisonCalls.incrementAndGet();
            throw new IllegalStateException("intentional fixture failure");
        });
        context.registerServerTick("healthy", tick -> {
            healthyCalls.incrementAndGet();
            frames.add(tick.sessionId());
            numbers.add(tick.tick());
        });

        for (int i = 0; i < 5; i++) first.tickServer(() -> true);
        check(first.getVanillaTicks() == 5, "Vanilla server behavior preserved");
        check(healthyCalls.get() == 5, "Exactly one callback per native tick");
        check(poisonCalls.get() == 3, "Faulty listener disabled after three failures");
        check(frames.stream().distinct().count() == 1, "Same world server retains session identity");
        check(numbers.equals(List.of(1L, 2L, 3L, 4L, 5L)), "Monotonic 1-based tick numbering");

        second.tickServer(() -> false);
        check(second.getVanillaTicks() == 0, "Native early return preserved");
        check(healthyCalls.get() == 6, "All native return points instrumented");
        check(!frames.get(0).equals(frames.get(5)), "New server instance isolated");
        check(numbers.get(5) == 1, "Second server starts at tick 1");

        boolean duplicateRejected = false;
        try { context.registerServerTick("healthy", tick -> {}); }
        catch (IllegalStateException expected) { duplicateRejected = true; }
        check(duplicateRejected, "Duplicate callbacks rejected");
        System.out.println("PASS: " + checks + " H.O.W.L. native server tick lifecycle assertions.");
    }
}
