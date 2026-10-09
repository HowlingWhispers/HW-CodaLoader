package dev.howlingwhispers.codawolf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Coda's offline, read-only discovery and reaction policy.
 *
 * Minecraft registry IDs and tags are observations, NOT visuals and NOT
 * automatic proof that something is accessible, safe or harvestable.
 * Deterministic reactions require no language model, account or network.
 * The immutable observation feed is also suitable as future consent-gated
 * active-AI context, without exposing world saves or arbitrary player data.
 */
public final class CompanionAwareness {
    public static final int SCAN_INTERVAL_TICKS = 2;
    public static final int BLOCKS_PER_SCAN = 8;
    public static final int ANNOUNCE_COOLDOWN_TICKS = 20 * 45;
    public static final int DUPLICATE_COOLDOWN_TICKS = 20 * 60 * 10;
    private static final int MAX_MEMORY = 256;
    private static final int MAX_RECENT = 12;

    public record Observation(String dimension, String blockId, List<String> tags,
                              int x, int y, int z, int distanceSquared, long tick) {
        public Observation {
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(blockId);
            tags = List.copyOf(Objects.requireNonNull(tags));
            if (dimension.isBlank() || blockId.isBlank() || !blockId.contains(":")
                    || distanceSquared < 0 || tick < 0 || tags.size() > 32)
                throw new IllegalArgumentException("Invalid Coda world observation");
        }
        String fingerprint() {
            return dimension + "|" + blockId + "|" + x + "," + y + "," + z;
        }
    }

    public record Reaction(String category, String text, Observation observation) {}
    private final Map<String,Long> noticed = new LinkedHashMap<>();
    private final List<Observation> recent = new ArrayList<>();
    private long lastSpoken = -ANNOUNCE_COOLDOWN_TICKS;
    private long observationsSeen;
    private int scanIndex;

    public long observationsSeen() { return observationsSeen; }
    public int nextScanIndex(int positions) {
        if (positions < 1) throw new IllegalArgumentException("No scan offsets");
        int index = Math.floorMod(scanIndex, positions);
        scanIndex = (index + 1) % positions;
        return index;
    }

    /** Ordered newest first; caller may format to a player or feed a model later. */
    public List<Observation> recent() { return List.copyOf(recent); }

    public Optional<Reaction> observe(Observation observation) {
        Objects.requireNonNull(observation);
        observationsSeen++;
        String key = observation.fingerprint();
        Long previous = noticed.get(key);
        if (previous != null && observation.tick() - previous < DUPLICATE_COOLDOWN_TICKS)
            return Optional.empty();
        noticed.put(key, observation.tick());
        if (noticed.size() > MAX_MEMORY) {
            var iterator = noticed.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        recent.removeIf(x -> x.fingerprint().equals(key));
        recent.add(0, observation);
        if (recent.size() > MAX_RECENT) recent.remove(recent.size()-1);
        var candidate = respondTo(observation);
        if (candidate.isEmpty() || observation.tick() - lastSpoken < ANNOUNCE_COOLDOWN_TICKS)
            return Optional.empty();
        lastSpoken = observation.tick();
        return candidate;
    }

    /** Only grounded named blocks/tags get canned advice; unknown IDs stay in context. */
    public static Optional<Reaction> respondTo(Observation o) {
        String id=o.blockId();
        String category, words;
        if (id.equals("minecraft:lava")) {
            category="hazard";
            words="Careful, boss! Lava is registered nearby. Watch your step.";
        } else if (id.equals("minecraft:brown_mushroom") || id.equals("minecraft:red_mushroom")) {
            category="mushroom";
            String kind=id.endsWith("brown_mushroom") ? "brown" : "red";
            words="Found a " + kind + " mushroom nearby! You could gather it for mushroom stew.";
        } else if (id.equals("minecraft:crimson_fungus") || id.equals("minecraft:warped_fungus")) {
            category="fungus";
            words="There's some " + (id.contains("crimson") ? "crimson" : "warped")
                    + " fungus nearby. Handy if we're exploring the Nether.";
        } else if (id.equals("minecraft:bee_nest") || id.equals("minecraft:beehive")) {
            category="bees";
            words="Bee home detected! A properly placed campfire helps keep them calm when collecting honey.";
        } else if (id.equals("minecraft:amethyst_cluster") || id.equals("minecraft:budding_amethyst")) {
            category="amethyst";
            words="Amethyst nearby! Remember, budding amethyst is best left where it grows.";
        } else if (id.equals("minecraft:ancient_debris") || id.endsWith("_ore")) {
            category="ore";
            String material=id.substring(id.indexOf(':')+1).replace('_',' ');
            words="Found " + material + " nearby. We could mark this spot for mining.";
        } else if (id.equals("minecraft:wheat") || id.equals("minecraft:carrots")
                || id.equals("minecraft:potatoes")) {
            category="crops";
            words="There are crops close by. Maybe we should check if they're ready for harvesting.";
        } else if (id.endsWith(":engine_redstone") || id.endsWith(":engine_combustion")
                || id.endsWith(":engine_stirling")) {
            category="machine";
            words="BuildCraft engine nearby! I can identify it, but heat diagnostics aren't connected yet.";
        } else if (o.tags().contains("minecraft:flowers") || o.tags().contains("minecraft:small_flowers")) {
            category="flower";
            words="I found some flowers! Could make a nice dye or brighten up our home.";
        } else {
            return Optional.empty();
        }
        return Optional.of(new Reaction(category,words,o));
    }
}
