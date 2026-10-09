package dev.howlingwhispers.buildcraftlite;

import dev.howlingwhispers.codaloader.api.CodaBlockEntityTick;
import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import dev.howlingwhispers.codaloader.api.CodaInventoryView;
import dev.howlingwhispers.codaloader.api.CodaServerTickContext;
import dev.howlingwhispers.codaloader.api.CodaWorldView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** World-fixture validation only, not a claim about live Minecraft mappings. */
public final class BuildCraftLiteTransportTest {
    private static final String DIM = "minecraft:overworld";
    private static final CodaBlockPos SRC = new CodaBlockPos(0, 64, 0);
    private static final CodaBlockPos WOOD = new CodaBlockPos(1, 64, 0);
    private static final CodaBlockPos STONE1 = new CodaBlockPos(2, 64, 0);
    private static final CodaBlockPos STONE2 = new CodaBlockPos(3, 64, 0);
    private static final CodaBlockPos DST = new CodaBlockPos(4, 64, 0);
    private static int checks;

    private static void check(boolean yes, String message) {
        checks++;
        if (!yes) throw new AssertionError(message);
    }

    private static final class FakeWorld implements CodaWorldView {
        final Map<CodaBlockPos, String> blocks = new HashMap<>();
        final Map<CodaBlockPos, Integer> chests = new HashMap<>();
        boolean destinationFull;
        int moves;

        FakeWorld() {
            blocks.put(WOOD, BuildCraftLiteMod.WOOD);
            blocks.put(STONE1, BuildCraftLiteMod.STONE);
            blocks.put(STONE2, BuildCraftLiteMod.STONE);
            chests.put(SRC, 20);
            chests.put(DST, 0);
        }

        public List<String> dimensions() { return List.of(DIM); }
        public boolean isChunkLoaded(String d, CodaBlockPos pos) { return DIM.equals(d); }
        public boolean isBlock(String d, CodaBlockPos p, String id) {
            return DIM.equals(d) && id.equals(blocks.get(p));
        }
        public Optional<CodaInventoryView> inventory(String d, CodaBlockPos p) {
            if (!DIM.equals(d) || !chests.containsKey(p)) return Optional.empty();
            return Optional.of(new CodaInventoryView(p,
                    List.of(new CodaInventoryView.Slot(0, chests.get(p), 64))));
        }
        public int transfer(String d, CodaBlockPos from, CodaBlockPos to, int max) {
            check(DIM.equals(d), "No cross-dimension transport");
            check(from.equals(SRC) && to.equals(DST), "Correct source and destination");
            check(max == 1, "Bounded one-item transfer");
            if (destinationFull || chests.get(from) == 0) return 0;
            chests.put(from, chests.get(from) - 1);
            chests.put(to, chests.get(to) + 1);
            moves++;
            return 1;
        }
    }

    private static void run(BuildCraftLiteTransport mod, FakeWorld world, int first, int last) {
        for (int n = first; n <= last; n++) {
            for (CodaBlockPos p : List.of(WOOD, STONE1, STONE2)) {
                if (!world.blocks.containsKey(p)) continue;
                mod.onPipeTick(new CodaBlockEntityTick(
                        BuildCraftLiteMod.HOLDER, DIM, p));
            }
            mod.onServerTick(new CodaServerTickContext(
                    "playtest-1", n, Optional.of(world)));
        }
    }

    public static void main(String[] args) throws Exception {
        var world = new FakeWorld();
        var mod = new BuildCraftLiteTransport();
        run(mod, world, 1, 64);
        check(world.moves > 0, "Complete pipe route must deliver items");
        check(world.chests.get(SRC) + world.chests.get(DST) == 20,
                "No duplication or deletion");
        check(mod.diagnosis().contains("verified route"),
                "Coda receives real route diagnostics");

        int count = world.moves;
        world.blocks.remove(STONE1);
        run(mod, world, 65, 128);
        check(world.moves == count, "Broken pipe must never transfer");
        check(mod.diagnosis().contains("no reachable"),
                "Coda identifies broken route");

        world.blocks.put(STONE1, BuildCraftLiteMod.STONE);
        world.destinationFull = true;
        run(mod, world, 129, 192);
        check(world.moves == count, "Full target refuses transfer safely");
        check(mod.diagnosis().contains("destination full"),
                "Coda mentions blocked destination");

        world.destinationFull = false;
        run(mod, world, 193, 256);
        check(world.moves > count, "Reconnect resumes transport");
        check(world.chests.get(SRC) + world.chests.get(DST) == 20,
                "Inventory is conserved after interruption");
        System.out.println("PASS: " + checks
                + " BuildCraft Lite fixture assertions (transport, reconnection, Coda)");
    }
}
