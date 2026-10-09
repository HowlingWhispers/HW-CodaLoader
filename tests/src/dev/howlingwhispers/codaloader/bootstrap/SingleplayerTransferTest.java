package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.concurrent.atomic.AtomicReference;

/** Java 21 Minecraft fixtures. An actual game run remains required. */
public final class SingleplayerTransferTest {
    private static int checks;
    private static void check(boolean yes, String what) {
        checks++;
        if (!yes) throw new AssertionError(what);
    }
    private static void rejected(Throwing action, String what) throws Exception {
        try { action.run(); } catch (IllegalStateException | IllegalArgumentException ex) {
            checks++;
            return;
        }
        throw new AssertionError(what);
    }
    @FunctionalInterface interface Throwing { void run() throws Exception; }

    public static final class Source {
        final IntegratedServer server;
        final MinecraftServer.Level level;
        Source(IntegratedServer server, MinecraftServer.Level level) {
            this.server = server; this.level = level;
        }
        public IntegratedServer getServer() { return server; }
        public MinecraftServer.Level getLevel() { return level; }
    }
    public static final class WrongServerSource {
        final MinecraftServer server = new MinecraftServer();
        final MinecraftServer.Level level = new MinecraftServer.Level("minecraft:overworld");
        public MinecraftServer getServer() { return server; }
        public MinecraftServer.Level getLevel() { return level; }
    }

    public static void main(String[] args) throws Exception {
        IntegratedServer server = new IntegratedServer();
        var level = new MinecraftServer.Level("minecraft:overworld");
        var chunk = new MinecraftServer.Chunk();
        server.addWorld(level);
        level.getChunkSource().addLoaded(0, 0, chunk);

        var from = new CodaBlockPos(0, 64, 0);
        var to = new CodaBlockPos(4, 64, 0);
        var source = new ChestBlockEntity(new ItemStack("minecraft:diamond", 18, 64,
                "custom_name:Keepsake"), new ItemStack(0,64));
        var output = new BarrelBlockEntity(new ItemStack(0,64),
                new ItemStack("minecraft:diamond", 60,64, "custom_name:Keepsake"),
                new ItemStack(0,64));
        chunk.put(new BlockPos(0, 64, 0), source);
        chunk.put(new BlockPos(4, 64, 0), output);
        for (int i=1; i<=3; i++) chunk.putBlock(new BlockPos(i, 64, 0), new BlockState(Blocks.GLASS));

        var world = new MinecraftSingleplayerWorld(new Source(server, level));
        check(world.isLoaded(from) && world.isLoaded(to), "Both chests loaded");
        for (int i=1; i<=3; i++)
            check(world.isBlock(new CodaBlockPos(i,64,0), "minecraft:glass"), "Glass marker exists");
        check(!world.isLoaded(new CodaBlockPos(16,64,0)), "Unloaded chunk not loaded");
        check(!world.isBlock(new CodaBlockPos(16,64,0), "minecraft:glass"), "Unloaded glass not read");

        check(world.transfer(from, to, 16) == 16, "First transfer of 16 complete item stacks");
        check(source.getItem(0).getCount() == 2, "Source stock decreased exactly");
        check(output.getItem(0).getCount() == 16, "Destination empty slot occupied");
        check(output.getItem(0).components().equals("custom_name:Keepsake"),
                "Custom item components survived transfer");
        check(output.getItem(1).getCount() == 60, "Previously stored stack preserved");

        check(world.transfer(from, to, 16) == 2, "Partial source stack moved");
        check(source.getItem(0).isEmpty(), "Source slot is empty");
        check(output.getItem(0).getCount() == 18, "Partial items merged with matching stack");
        check(world.transfer(from, to, 16) == 0, "Empty source returns zero");

        source.setItem(0, new ItemStack("minecraft:diamond", 12,64,"enchantment:sharpness"));
        check(world.transfer(from, to, 16) == 12, "Different component stack moved");
        check(output.getItem(0).getCount() == 18
                && output.getItem(1).getCount() == 60,
                "Items with different components do not merge");
        check(output.getItem(0).components().equals("custom_name:Keepsake")
                && output.getItem(2).components().equals("enchantment:sharpness"),
                "Different item components remain separate and intact");

        // Setter throws after destination acceptance: recover the original.
        source.setItem(0, new ItemStack("minecraft:iron_ingot", 7,64,""));
        var failedOutput = new ChestBlockEntity(new ItemStack(0,64));
        chunk.put(new BlockPos(5,64,0), failedOutput);
        failedOutput.failNextWrite();
        try {
            world.transfer(from, new CodaBlockPos(5,64,0), 7);
            throw new AssertionError("Write rejection must propagate");
        } catch (java.io.IOException expected) { checks++; }
        check(source.getItem(0).getCount() == 7 && failedOutput.getItem(0).isEmpty(),
                "Rejected destination leaves inventory unchanged");

        rejected(() -> world.transfer(from, from, 8), "Self transfers refused");
        rejected(() -> world.transfer(from, to, 65), "Overlarge transfers refused");
        rejected(() -> world.isBlock(from, "minecraft:stone"), "Non-whitelisted marker refused");
        rejected(() -> new MinecraftSingleplayerWorld(new WrongServerSource()),
                "Dedicated/nonintegrated server cannot access test bridge");

        var crossThread = new AtomicReference<Throwable>();
        Thread thread = new Thread(() -> {
            try { world.transfer(from,to,1); } catch (Exception ex) { crossThread.set(ex); }
        });
        thread.start(); thread.join();
        check(crossThread.get() instanceof IllegalStateException, "Transfer refused from other thread");
        check(level.getChunkSource().forcedLoads() == 0, "Never requests new or generated chunks");
        System.out.println("PASS: " + checks + " integrated single-player chest transfer fixtures");
    }
}
