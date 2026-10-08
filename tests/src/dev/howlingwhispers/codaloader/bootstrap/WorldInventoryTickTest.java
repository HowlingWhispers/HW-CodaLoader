package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaWorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Actual JVM agent instrumented server callback, fixture Minecraft class only.
 * Checks single-player isolation, read-only inventories, and strict no-chunk-load.
 */
public final class WorldInventoryTickTest {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    private static void fails(Runnable test, String reason) {
        try { test.run(); }
        catch (IllegalStateException expected) { checks++; return; }
        throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        MinecraftServer minecraft = new MinecraftServer();
        var overworld = new MinecraftServer.Level("minecraft:overworld");
        var nether = new MinecraftServer.Level("minecraft:the_nether");
        minecraft.addWorld(overworld);
        minecraft.addWorld(nether);

        var chunk = new MinecraftServer.Chunk();
        chunk.put(new BlockPos(1, 64, 2),
                new MinecraftServer.Chest(new ItemStack(12, 64),
                        new ItemStack(0, 64), new ItemStack(3, 16)));
        chunk.put(new BlockPos(2, 64, 2), new Object());
        overworld.getChunkSource().addLoaded(0, 0, chunk);

        List<CodaWorldView> leaked = new ArrayList<>();
        List<String> captured = new ArrayList<>();
        var threads = new AtomicReference<Throwable>();
        var context = new CodaContext("0.0.26", "26.4-snapshot-3", Path.of("run"),
                Path.of("run/config/world_test"), "world_test", List.of("world_test"));
        context.registerServerTick("inspect_loaded_chest", tick -> {
            CodaWorldView view = tick.world().orElseThrow();
            leaked.add(view);
            check(view.dimensions().equals(List.of("minecraft:overworld", "minecraft:the_nether")),
                    "Expected two canonical dimensions");
            CodaBlockPos chest = new CodaBlockPos(1, 64, 2);
            check(view.isChunkLoaded("minecraft:overworld", chest), "Chest chunk loaded");
            check(!view.isChunkLoaded("minecraft:the_nether", chest), "Dimensions do not alias");
            check(!view.isChunkLoaded("minecraft:overworld", new CodaBlockPos(17, 64, 2)),
                    "Missing chunk reports unloaded without generating it");
            check(view.inventory("minecraft:overworld", chest).orElseThrow().slots().stream()
                    .map(slot -> slot.count()).toList().equals(List.of(12, 0, 3)),
                    "Read-only native chest occupancy comes from block entity");
            check(view.inventory("minecraft:overworld", new CodaBlockPos(2, 64, 2)).isEmpty(),
                    "Non-container block entities are not inventories");
            check(view.inventory("minecraft:overworld", new CodaBlockPos(17, 64, 2)).isEmpty(),
                    "No inspection or load for missing chunk");
            check(view.inventory("minecraft:the_nether", chest).isEmpty(),
                    "Other world cannot see Overworld chest");
            check(overworld.getChunkSource().forcedLoads() == 0, "Never invokes a chunk-loading API");

            Thread wrong = new Thread(() -> {
                try { view.dimensions(); }
                catch (Throwable ex) { threads.set(ex); }
            }, "wrong-render-thread");
            wrong.start();
            wrong.join();
            check(threads.get() instanceof IllegalStateException,
                    "World view refuses off-thread access");
            captured.add(tick.sessionId());
        });
        minecraft.tickServer(() -> true);
        check(captured.size() == 1, "Real instrumentation dispatched single-player world view");
        check(!captured.get(0).isBlank(), "Single player session has an ID");
        check(minecraft.getVanillaTicks() == 1, "Normal Minecraft tick preserved");
        CodaWorldView expired = leaked.get(0);
        fails(() -> {
            try { expired.dimensions(); }
            catch (IllegalStateException ex) { throw ex; }
            catch (Exception ex) { throw new AssertionError(ex); }
        }, "World view must be invalid outside callback scope");
        check(overworld.getChunkSource().forcedLoads() == 0, "No chunk generation at any point");
        System.out.println("PASS: " + checks + " single-player read-only inventory bridge assertions");
    }
}
