package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaBlockPlacements;
import dev.howlingwhispers.codaloader.api.CodaBlockPos;

/**
 * Verify the server really placed a native BuildCraft engine or pipe before
 * notifying mods. Never trust client events; never force-load chunks.
 */
public final class CodaBlockPlacementLifecycle {
    private CodaBlockPlacementLifecycle() {}

    public static void afterPlacement(Object result, Object nativeItem, Object context) {
        try {
            if (result == null || nativeItem == null || context == null) return;
            Object level = CommandReflection.call(context, "getLevel");
            if (level == null || (Boolean)CommandReflection.call(level, "isClientSide"))
                return;
            Object pos = CommandReflection.call(context, "getClickedPos");
            if (pos == null) return;
            int x = ((Number)CommandReflection.call(pos, "getX")).intValue();
            int y = ((Number)CommandReflection.call(pos, "getY")).intValue();
            int z = ((Number)CommandReflection.call(pos, "getZ")).intValue();
            Object placedBlock = CommandReflection.call(nativeItem, "getBlock");
            Object actualState = CommandReflection.call(level, "getBlockState", pos);
            if (!(Boolean)CommandReflection.call(actualState, "is", placedBlock))
                return;

            ClassLoader mc = level.getClass().getClassLoader();
            Object registry = Class.forName("net.minecraft.core.registries.BuiltInRegistries",
                    true, mc).getField("BLOCK").get(null);
            String id = CommandReflection.call(registry, "getKey", placedBlock).toString();
            if (!id.equals("buildcraftcore:engine_redstone")
                && !id.equals("buildcrafttransport:wood_item")
                && !id.equals("buildcrafttransport:cobblestone_item")) return;
            Object dimension = CommandReflection.call(level, "dimension");
            String dimensionId = CommandReflection.call(dimension, "identifier").toString();
            CodaBlockPlacements.dispatch(new CodaBlockPlacements.Placement(
                    dimensionId, new CodaBlockPos(x, y, z), id));
        } catch (Throwable error) {
            System.err.println("[H.O.W.L.] BuildCraft native placement event unavailable: " + error);
        }
    }
}
