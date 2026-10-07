package dev.howlingwhispers.codaloader.bootstrap;

import com.mojang.brigadier.CommandDispatcher;
import dev.howlingwhispers.codaloader.api.*;
import dev.howlingwhispers.essentials.HwEssentialsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/** Real Brigadier parsing with fixture Minecraft objects: adapter checks, not a live-game claim. */
public final class CommandBridgeTest {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        Path world = Files.createTempDirectory("cml-command-bridge-");
        Path config = world.resolve("config"); Files.createDirectories(config);
        new HwEssentialsMod().onInitialize(new CodaContext("0.0.19", "26.4-snapshot-3", world, config, "hw_essentials", List.of("hw_essentials")));
        Server server = new Server(world);
        Source source = new Source(server);
        ServerCommandHooks.register(server);
        check(server.commands.dispatcher.getRoot().getChildren().size() == 5, "register all roots");
        check(server.commands.syncs == 1, "connected player receives refreshed command tree");
        ServerCommandHooks.register(server);
        check(server.commands.dispatcher.getRoot().getChildren().size() == 5, "repeat registration preserves roots");
        check(server.commands.dispatcher.execute("sethome cabin", source) == 1, "named home command parses");
        source.player.x = 100; source.player.yaw = 180;
        check(server.commands.dispatcher.execute("home cabin", source) == 1, "home command succeeds");
        check(source.player.x == 10.5 && source.player.yaw == 90 && source.player.teleports == 1, "server teleport uses saved position and facing");
        source.level.collision = true;
        check(server.commands.dispatcher.execute("home cabin", source) == 0, "blocked destination refuses");
        check(source.player.teleports == 1, "collision never moves player");
        source.level.collision = false; source.level.fluid = true;
        check(server.commands.dispatcher.execute("home cabin", source) == 0, "fluid destination refuses");
        source.level.fluid = false; source.level.supported = false;
        check(server.commands.dispatcher.execute("home cabin", source) == 0, "unsupported destination refuses");
        source.level.supported = true; source.level.bounds = false;
        check(server.commands.dispatcher.execute("home cabin", source) == 0, "world bounds refuse");
        source.level.bounds = true;
        check(server.commands.dispatcher.execute("sethome", source) == 1, "no-argument command parses");
        check(server.commands.dispatcher.execute("sethome one two", source) == 0, "extra arguments rejected");
        check(server.commands.dispatcher.execute("homes", source) == 1, "list command");
        check(source.messages.getLast().contains("cabin"), "listing replies to source player");
        check(server.commands.dispatcher.execute("delhome cabin", source) == 1, "delete command");
        check(server.commands.dispatcher.execute("home cabin", source) == 0, "missing home rejects");
        MinecraftCommandContext adapter = new MinecraftCommandContext(source);
        check(adapter.worldDirectory().equals(world), "actual world directory adapter");
        check(adapter.playerId().equals(source.player.id), "UUID adapter");
        check(source.level.chunks > 0, "destination chunks loaded before checks");
        System.out.println("Command bridge tests passed: " + checks + " checks with real Brigadier; live Minecraft remains unverified.");
    }
    public static final class Commands {
        public final CommandDispatcher<Source> dispatcher = new CommandDispatcher<>();
        int syncs;
        public CommandDispatcher<Source> getDispatcher() { return dispatcher; }
        public void sendCommands(Player player) { syncs++; }
    }
    public static final class PlayerList {
        final List<Player> players = new ArrayList<>();
        public List<Player> getPlayers() { return players; }
    }
    public static final class Server {
        final Path world; final Commands commands = new Commands();
        final PlayerList players = new PlayerList();
        public Server(Path world) { this.world = world; }
        public Commands getCommands() { return commands; }
        public PlayerList getPlayerList() { return players; }
        public Path getWorldPath(LevelResource root) { return world; }
    }
    public static final class Source {
        final Server server; final Player player = new Player(); final Level level = new Level();
        final List<String> messages = new ArrayList<>();
        public Source(Server server) { this.server = server; server.players.players.add(player); }
        public Player getPlayerOrException() { return player; }
        public Server getServer() { return server; }
        public Level getLevel() { return level; }
        public void sendSuccess(Supplier<Component> text, boolean broadcast) {
            if (broadcast) throw new AssertionError("Reply must not broadcast private homes");
            messages.add(text.get().text());
        }
        public void sendFailure(Component text) { messages.add(text.text()); }
    }
    public static final class Player {
        final UUID id = UUID.randomUUID(); double x = 10.5, y = 70, z = -42.25; float yaw = 90, pitch = 15; int teleports;
        public UUID getUUID() { return id; }
        public double getX() { return x; } public double getY() { return y; } public double getZ() { return z; }
        public float getYRot() { return yaw; } public float getXRot() { return pitch; }
        public Box getBoundingBox() { return new Box(); }
        public boolean teleportTo(Level level, double x, double y, double z, Set<?> relative, float yaw, float pitch, boolean resetCamera) {
            if (!relative.isEmpty()) throw new AssertionError("Homes must be absolute");
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; teleports++; return true;
        }
    }
    public static final class Box { public Box move(double x, double y, double z) { return this; } }
    public static final class Fluid { final boolean present; public Fluid(boolean present) { this.present = present; } public boolean isEmpty() { return !present; } }
    public static final class Block { final boolean supported; public Block(boolean supported) { this.supported = supported; } public boolean isAir() { return !supported; } }
    public static final class Border { final boolean bounds; public Border(boolean bounds) { this.bounds = bounds; } public boolean isWithinBounds(BlockPos pos) { return bounds; } }
    public static final class Level {
        boolean collision, fluid, supported = true, bounds = true; int chunks;
        public String dimension() { return "overworld"; }
        public boolean isInWorldBounds(BlockPos pos) { return bounds; }
        public Border getWorldBorder() { return new Border(bounds); }
        public Object getChunk(int x, int z) { chunks++; return new Object(); }
        public boolean noCollision(Player player, Box box) { return !collision; }
        public Fluid getFluidState(BlockPos pos) { return new Fluid(fluid); }
        public Block getBlockState(BlockPos pos) { return new Block(supported); }
    }
}
