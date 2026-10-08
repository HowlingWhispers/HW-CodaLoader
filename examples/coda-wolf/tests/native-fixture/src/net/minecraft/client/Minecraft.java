package net.minecraft.client;
import net.minecraft.client.server.IntegratedServer;
public final class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public final IntegratedServer integrated = new IntegratedServer();
    public static Minecraft getInstance() { return INSTANCE; }
    public IntegratedServer getSingleplayerServer() { return integrated; }
}
