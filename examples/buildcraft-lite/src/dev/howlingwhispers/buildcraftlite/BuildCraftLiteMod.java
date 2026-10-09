package dev.howlingwhispers.buildcraftlite;

import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;
import java.util.List;

/**
 * BuildCraft Lite's deliberately narrow H.O.W.L. foundation.
 *
 * This registers real Minecraft block identities and BlockItems. It does not
 * claim working transport: that requires a native pipe block entity with item
 * persistence, ticking, world inventory access and client cargo rendering.
 */
public final class BuildCraftLiteMod implements CodaMod {
    public static final String WOOD = "hw_buildcraft_lite:wooden_transport_pipe";
    public static final String STONE = "hw_buildcraft_lite:stone_transport_pipe";
    public static final String HOLDER = "hw_buildcraft_lite:transport_pipe_holder";

    @Override
    public void onInitialize(CodaContext context) {
        context.registerBlock(WOOD, 0.25f);
        context.registerBlock(STONE, 0.25f);
        context.registerBlockEntityType(HOLDER, List.of(WOOD, STONE));
        context.registerCreativeTab(
                "hw_buildcraft_lite:transport", "BuildCraft Lite",
                WOOD, List.of(WOOD, STONE));

        // Coda integration starts with honest diagnostics rather than invented
        // knowledge of blocks/transactions the loader cannot yet expose.
        context.registerCommand("bclite", "Coda reports BuildCraft Lite capabilities",
                (player, args) -> {
                    player.reply("[Coda] BuildCraft Lite blocks registered: wooden and stone pipes.");
                    player.reply("[Coda] Transport is not active yet. Missing native travelling-item flow and chest interaction.");
                    player.reply("[Coda] Red/yellow world outlines are not active until the H.O.W.L. diagnostics renderer is available.");
                });
        System.out.println("[BuildCraft Lite] 0.1.0-dev registered wooden and stone pipe blocks; transport not enabled.");
    }
}
