package dev.howlingwhispers.codaloader.api;

import java.util.List;

/** Registry preflight rules, without loading Minecraft or a fake pipe runtime. */
public final class NativeBlockEntityDeclarationTest {
    private static int checks;
    private static void check(boolean yes, String explanation) {
        checks++;
        if (!yes) throw new AssertionError(explanation);
    }
    private static void rejected(Runnable code, String explanation) {
        try {
            code.run();
            throw new AssertionError("Accepted invalid block entity: " + explanation);
        } catch (IllegalArgumentException correct) {
            checks++;
        }
    }

    public static void main(String[] args) {
        CodaNativeContents.registerBlock("buildcraft_cml", "buildcrafttransport:wood_item", 0.7f);
        CodaNativeContents.registerBlock("buildcraft_cml", "buildcrafttransport:cobblestone_item", 1.4f);
        CodaNativeContents.registerItem("other_mod", "othermod:wrench");
        CodaNativeContents.registerBlock("other_mod", "othermod:foreign_block", 1.0f);
        CodaNativeContents.registerBlockEntityType("buildcraft_cml",
                "buildcrafttransport:pipe_holder",
                List.of("buildcrafttransport:wood_item","buildcrafttransport:cobblestone_item"));
        check(CodaNativeContents.blockEntityTypes().size()==1,"Exactly one real pipe-holder type");
        check(CodaNativeContents.hasBlockEntity("buildcrafttransport:wood_item"),
                "Wooden BuildCraft pipe requires native block entity");
        check(CodaNativeContents.hasBlockEntity("buildcrafttransport:cobblestone_item"),
                "Cobble BuildCraft pipe requires native block entity");
        check(!CodaNativeContents.hasBlockEntity("othermod:foreign_block"),
                "Other blocks retain existing regular registration");
        rejected(() -> CodaNativeContents.registerBlockEntityType("buildcraft_cml",
                        "buildcrafttransport:ghost",List.of("buildcrafttransport:unknown")),
                "Undeclared block");
        rejected(() -> CodaNativeContents.registerBlockEntityType("buildcraft_cml",
                        "buildcrafttransport:foreign",List.of("othermod:foreign_block")),
                "Cross-mod type ownership");
        rejected(() -> CodaNativeContents.registerBlockEntityType("buildcraft_cml",
                        "buildcrafttransport:duplicate",List.of(
                                "buildcrafttransport:wood_item","buildcrafttransport:wood_item")),
                "Duplicate block IDs");
        rejected(() -> CodaNativeContents.registerBlockEntityType("buildcraft_cml",
                        "buildcrafttransport:overlap",List.of("buildcrafttransport:wood_item")),
                "Duplicate type ownership");
        rejected(() -> CodaNativeContents.registerBlockEntityType("buildcraft_cml",
                        "buildcrafttransport:empty",List.of()),
                "Empty block entity type");
        check(CodaNativeContents.blockEntityTypes().get(0).blocks().size()==2,
                "Rejected declarations must not mutate accepted type");
        java.util.concurrent.atomic.AtomicInteger ticks = new java.util.concurrent.atomic.AtomicInteger();
        CodaNativeContents.registerBlockEntityTick("buildcraft_cml",
                "buildcrafttransport:pipe_holder", event -> {
                    if (!event.position().equals(new CodaBlockPos(2,64,0)))
                        throw new AssertionError("Incorrect native block entity position");
                    ticks.incrementAndGet();
                });
        check(CodaNativeContents.hasBlockEntityTick("buildcrafttransport:pipe_holder"),
                "Real pipe-holder declares a native ticker");
        CodaNativeContents.dispatchBlockEntityTick(new CodaBlockEntityTick(
                "buildcrafttransport:pipe_holder", "minecraft:overworld",new CodaBlockPos(2,64,0)));
        check(ticks.get()==1,"Native ticker invokes registered mod exactly once");
        rejected(() -> CodaNativeContents.registerBlockEntityTick("other_mod",
                "buildcrafttransport:pipe_holder",event -> {}),
                "Foreign mod cannot register a BuildCraft pipe ticker");
        rejected(() -> CodaNativeContents.registerBlockEntityTick("buildcraft_cml",
                "buildcrafttransport:pipe_holder",event -> {}),
                "Duplicate owner ticker cannot double-run");
        rejected(() -> CodaNativeContents.registerBlockEntityTick("buildcraft_cml",
                "buildcrafttransport:missing_tile",event -> {}),
                "Unknown tile cannot register a ticker");
        System.out.println("PASS: "+checks+" native block entity registration preflight checks");
    }
}
