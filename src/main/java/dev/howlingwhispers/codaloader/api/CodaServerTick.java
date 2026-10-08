package dev.howlingwhispers.codaloader.api;

/** A callback on Minecraft's authoritative server tick thread. */
@FunctionalInterface
public interface CodaServerTick {
    /**
     * Called once when MinecraftServer.tickServer(BooleanSupplier) returns.
     * Keep work short: blocking here will stall the world.
     */
    void onTick(CodaServerTickContext context) throws Exception;
}
