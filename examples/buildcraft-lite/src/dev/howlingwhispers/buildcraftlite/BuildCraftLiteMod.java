package dev.howlingwhispers.buildcraftlite;

import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;
import java.util.List;

/**
 * Small, independently packaged BuildCraft-derived transport feature.
 * Coda is integrated via status and fault diagnostics on each native route.
 */
public final class BuildCraftLiteMod implements CodaMod {
    public static final String WOOD = "hw_buildcraft_lite:wooden_transport_pipe";
    public static final String STONE = "hw_buildcraft_lite:stone_transport_pipe";
    public static final String ENGINE = "hw_buildcraft_lite:redstone_engine";
    public static final String WRENCH = "hw_buildcraft_lite:wrench";
    public static final String HOLDER = "hw_buildcraft_lite:transport_pipe_holder";
    private final BuildCraftLiteTransport transport = new BuildCraftLiteTransport();

    @Override
    public void onInitialize(CodaContext context) {
        context.registerBlock(WOOD, 0.25f);
        context.registerBlock(STONE, 0.25f);
        context.registerBlock(ENGINE, 1.0f);
        context.registerItem(WRENCH);
        context.registerBlockEntityType(HOLDER, List.of(WOOD, STONE));
        context.registerCreativeTab(
                "hw_buildcraft_lite:transport", "BuildCraft Lite",
                WOOD, List.of(WOOD, STONE, ENGINE, WRENCH));

        // Native block entity ticks are supplied only by Minecraft's own
        // ticking loaded blocks. Never synthesize fake transport coordinates.
        context.registerBlockEntityTick(HOLDER, transport::onPipeTick);
        context.registerServerTick("buildcraft_lite_transport", transport::onServerTick);
        context.registerCommand("bclite", "Ask Coda to check BuildCraft Lite transport",
                (player, args) -> player.reply(transport.diagnosis()));
        System.out.println("[BuildCraft Lite] Powered Redstone Engine extraction and wooden/stone chest transport enabled.");
    }
}
