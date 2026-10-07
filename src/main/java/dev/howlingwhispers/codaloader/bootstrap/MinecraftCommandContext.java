package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaCommandContext;
import dev.howlingwhispers.codaloader.api.CodaPosition;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Minecraft Java 26.4 Snapshot 3 integrated-server adapter; same-dimension homes in v0.1. */
final class MinecraftCommandContext implements CodaCommandContext {
    private final Object source;
    private final Object player;
    private final ClassLoader loader;

    MinecraftCommandContext(Object source) throws Exception {
        this.source = source;
        this.player = CommandReflection.call(source, "getPlayerOrException");
        this.loader = source.getClass().getClassLoader();
    }

    public UUID playerId() throws Exception { return (UUID) CommandReflection.call(player, "getUUID"); }
    public Path worldDirectory() throws Exception {
        Object server = CommandReflection.call(source, "getServer");
        Object root = Class.forName("net.minecraft.world.level.storage.LevelResource", true, loader).getField("ROOT").get(null);
        return ((Path) CommandReflection.call(server, "getWorldPath", root)).toAbsolutePath().normalize();
    }

    public CodaPosition position() throws Exception {
        Object level = CommandReflection.call(source, "getLevel");
        return new CodaPosition(CommandReflection.call(level, "dimension").toString(),
                ((Number) CommandReflection.call(player, "getX")).doubleValue(),
                ((Number) CommandReflection.call(player, "getY")).doubleValue(),
                ((Number) CommandReflection.call(player, "getZ")).doubleValue(),
                ((Number) CommandReflection.call(player, "getYRot")).floatValue(),
                ((Number) CommandReflection.call(player, "getXRot")).floatValue());
    }

    public void reply(String message) throws Exception { sendReply(source, message, false); }

    static void sendReply(Object source, String message, boolean error) throws Exception {
        Class<?> component = Class.forName("net.minecraft.network.chat.Component", true, source.getClass().getClassLoader());
        Object text = CommandReflection.call(component, "literal", message);
        if (error) CommandReflection.call(source, "sendFailure", text);
        else CommandReflection.call(source, "sendSuccess", (Supplier<Object>) () -> text, false);
    }

    public void teleport(CodaPosition destination) throws Exception {
        CodaPosition current = position();
        if (!current.dimension().equals(destination.dimension()))
            throw new IllegalArgumentException("Return to the home's dimension before using /home.");
        Object level = CommandReflection.call(source, "getLevel");
        Class<?> blockPos = Class.forName("net.minecraft.core.BlockPos", true, loader);
        Object feet = blockPos.getConstructor(int.class, int.class, int.class).newInstance(
                (int) Math.floor(destination.x()), (int) Math.floor(destination.y()), (int) Math.floor(destination.z()));
        if (!(Boolean) CommandReflection.call(level, "isInWorldBounds", feet)
                || !(Boolean) CommandReflection.call(CommandReflection.call(level, "getWorldBorder"), "isWithinBounds", feet))
            throw new IllegalArgumentException("That home is outside the world's bounds. Choose a new home.");

        // Load its chunk on the server thread before checking the landing spot.
        CommandReflection.call(level, "getChunk", ((int) Math.floor(destination.x())) >> 4, ((int) Math.floor(destination.z())) >> 4);
        Object box = CommandReflection.call(player, "getBoundingBox");
        Object destinationBox = CommandReflection.call(box, "move", destination.x() - current.x(),
                destination.y() - current.y(), destination.z() - current.z());
        Object head = CommandReflection.call(feet, "above");
        Object below = blockPos.getConstructor(int.class, int.class, int.class).newInstance(
                (int) Math.floor(destination.x()), (int) Math.floor(destination.y() - 0.01), (int) Math.floor(destination.z()));
        if (!(Boolean) CommandReflection.call(level, "noCollision", player, destinationBox)
                || !(Boolean) CommandReflection.call(CommandReflection.call(level, "getFluidState", feet), "isEmpty")
                || !(Boolean) CommandReflection.call(CommandReflection.call(level, "getFluidState", head), "isEmpty")
                || (Boolean) CommandReflection.call(CommandReflection.call(level, "getBlockState", below), "isAir"))
            throw new IllegalArgumentException("Your home's landing spot is blocked, wet or unsupported. Clear it or set a new home.");

        for (Method method : player.getClass().getMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (!method.getName().equals("teleportTo") || (p.length != 8 && p.length != 6)
                    || !p[0].isInstance(level) || p[1] != double.class || p[2] != double.class || p[3] != double.class) continue;
            Object result;
            if (p.length == 8 && Set.class.isAssignableFrom(p[4]) && p[5] == float.class
                    && p[6] == float.class && p[7] == boolean.class) {
                result = method.invoke(player, level, destination.x(), destination.y(), destination.z(),
                        Set.of(), destination.yaw(), destination.pitch(), false);
            } else if (p.length == 6 && p[4] == float.class && p[5] == float.class) {
                result = method.invoke(player, level, destination.x(), destination.y(), destination.z(), destination.yaw(), destination.pitch());
            } else continue;
            if (Boolean.FALSE.equals(result)) throw new IllegalArgumentException("Minecraft refused that teleport. Your home was kept.");
            return;
        }
        throw new NoSuchMethodException("Snapshot 3 server-player teleport bridge unavailable; no teleport was attempted.");
    }
}
