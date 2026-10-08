package net.minecraft.server;

import java.util.function.BooleanSupplier;

/** Small, version-pinned server-side fixture for the bytecode hook. */
public class MinecraftServer {
    private int vanillaTicks;

    public void tickServer(BooleanSupplier keepTicking) {
        if (!keepTicking.getAsBoolean()) return;
        vanillaTicks++;
    }

    public int getVanillaTicks() {
        return vanillaTicks;
    }
}
