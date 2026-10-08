package net.minecraft.client.server;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
public final class IntegratedServer {
    public final ServerLevel level = new ServerLevel();
    public final FakePlayer player = new FakePlayer(level);
    private final PlayerList players = new PlayerList(player);
    public PlayerList getPlayerList() { return players; }
    public Path getWorldPath(LevelResource resource) { return Path.of(System.getProperty("java.io.tmpdir")); }
    public static final class PlayerList {
        final List<Object> players;
        PlayerList(FakePlayer player) { players=List.of(player); }
        public List<Object> getPlayers() { return players; }
    }
    public static final class FakePlayer {
        final ServerLevel world;
        final UUID uuid = UUID.randomUUID();
        FakePlayer(ServerLevel world) { this.world=world; }
        public UUID getUUID() { return uuid; }
        public ServerLevel level() { return world; }
        public double getX() { return 10.0; }
        public double getY() { return 64.0; }
        public double getZ() { return 10.0; }
    }
}
