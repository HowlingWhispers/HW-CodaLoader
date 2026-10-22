package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaCommandContext;
import dev.howlingwhispers.codaloader.api.CodaBlockTarget;
import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import dev.howlingwhispers.codaloader.api.CodaPosition;
import dev.howlingwhispers.codaloader.api.CodaSingleplayerWorld;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Set;
import java.util.Optional;
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
        return new CodaPosition(CommandReflection.call(CommandReflection.call(level, "dimension"), "identifier").toString(),
                ((Number) CommandReflection.call(player, "getX")).doubleValue(),
                ((Number) CommandReflection.call(player, "getY")).doubleValue(),
                ((Number) CommandReflection.call(player, "getZ")).doubleValue(),
                ((Number) CommandReflection.call(player, "getYRot")).floatValue(),
                ((Number) CommandReflection.call(player, "getXRot")).floatValue());
    }

    public void reply(String message) throws Exception { sendReply(source, message, false); }

    @Override public Optional<CodaBlockTarget> targetBlock() throws Exception {
        Object server = CommandReflection.call(source, "getServer");
        if (!Boolean.TRUE.equals(CommandReflection.call(server, "isSameThread")))
            throw new IllegalStateException("Block targeting requires the native server thread");
        Object level = CommandReflection.call(source, "getLevel");
        double reach = Math.min(8, ((Number) CommandReflection.call(player, "blockInteractionRange")).doubleValue());
        if (!Double.isFinite(reach) || reach <= 0) return Optional.empty();
        Object eye = CommandReflection.call(player, "getEyePosition");
        Object direction = CommandReflection.call(player, "getViewVector", 1.0f);
        Object end = CommandReflection.call(eye, "add", CommandReflection.call(direction, "scale", reach));
        Class<?> vec = Class.forName("net.minecraft.world.phys.Vec3", true, loader);
        double x = vec.getField("x").getDouble(eye), z = vec.getField("z").getDouble(eye);
        double ex = vec.getField("x").getDouble(end), ez = vec.getField("z").getDouble(end);
        // Vanilla clip reads block states. Preflight its entire tiny corridor,
        // including endpoint epsilon, so that it cannot synchronously load a chunk.
        Object chunks = CommandReflection.call(level, "getChunkSource");
        for (int cx = (int)Math.floor(Math.min(x, ex) - .001) >> 4;
                cx <= (int)Math.floor(Math.max(x, ex) + .001) >> 4; cx++)
            for (int cz = (int)Math.floor(Math.min(z, ez) - .001) >> 4;
                    cz <= (int)Math.floor(Math.max(z, ez) + .001) >> 4; cz++)
                if (CommandReflection.call(chunks, "getChunkNow", cx, cz) == null) return Optional.empty();
        Class<?> clip = Class.forName("net.minecraft.world.level.ClipContext", true, loader);
        Class<?> blocks = Class.forName("net.minecraft.world.level.ClipContext$Block", true, loader);
        Class<?> fluids = Class.forName("net.minecraft.world.level.ClipContext$Fluid", true, loader);
        Object context = clip.getConstructor(vec, vec, blocks, fluids,
                Class.forName("net.minecraft.world.entity.Entity", true, loader)).newInstance(
                        eye, end, blocks.getField("OUTLINE").get(null), fluids.getField("NONE").get(null), player);
        Object hit = CommandReflection.call(level, "clip", context);
        if (!CommandReflection.call(hit, "getType").toString().equals("BLOCK")) return Optional.empty();
        Object p = CommandReflection.call(hit, "getBlockPos");
        return Optional.of(new CodaBlockTarget(
                CommandReflection.call(CommandReflection.call(level, "dimension"), "identifier").toString(),
                new CodaBlockPos(((Number)CommandReflection.call(p, "getX")).intValue(),
                        ((Number)CommandReflection.call(p, "getY")).intValue(),
                        ((Number)CommandReflection.call(p, "getZ")).intValue())));
    }

    @Override public void withWorld(CodaCommandContext.WorldAction action) throws Exception {
        Object server = CommandReflection.call(source, "getServer");
        if (!Boolean.TRUE.equals(CommandReflection.call(server, "isSameThread")))
            throw new IllegalStateException("World commands require the native server thread");
        try (var view = new MinecraftWorldInventoryView(server)) { action.run(view); }
    }

    @Override
    public CodaSingleplayerWorld singleplayerWorld() throws Exception {
        return new MinecraftSingleplayerWorld(source);
    }

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
