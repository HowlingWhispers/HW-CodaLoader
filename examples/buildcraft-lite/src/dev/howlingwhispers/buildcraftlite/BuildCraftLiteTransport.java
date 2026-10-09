package dev.howlingwhispers.buildcraftlite;

import dev.howlingwhispers.codaloader.api.CodaBlockEntityTick;
import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import dev.howlingwhispers.codaloader.api.CodaServerTickContext;
import dev.howlingwhispers.codaloader.api.CodaWorldView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deliberately small, server-authoritative item transport for BuildCraft Lite.
 *
 * Native H.O.W.L. BlockEntity tick signals supply only ACTUALLY ticking
 * pipes. Each server tick consumes these signals, and every eight ticks a
 * bounded route search traverses native wooden/stone blocks in loaded chunks.
 * Only a validated route may invoke the existing component-safe, atomic
 * Minecraft chest transaction bridge. No phantom item inventories, chunk
 * loads, autonomous background threads, or inventory polling loops exist.
 *
 * Items transfer at a route-dependent cadence. This first vertical slice
 * does NOT draw intermediate travelling-item sprites or support MJ engines.
 */
public final class BuildCraftLiteTransport {
    private static final int[][] SIDES = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
        {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };
    private static final int MAX_NODES = 64;
    private static final int MAX_WOODEN = 16;
    private static final int SCAN_INTERVAL = 8;
    private static final int ITEMS_PER_PULSE = 1;

    private record Node(String dimension, CodaBlockPos pos) {}
    private record Hop(CodaBlockPos pos, int distance) {}
    private record Route(CodaBlockPos source, CodaBlockPos target, int pipeCount) {}
    private final Set<Node> ticking = new HashSet<>();
    private final Map<Node, Long> lastDelivery = new HashMap<>();
    private String session = "";
    private volatile String lastStatus = "Waiting for a single-player world and pipe placement.";
    private volatile long movedItems;
    private volatile int validatedRoutes;

    /** Called by the native BlockEntityTicker for both registered pipe blocks. */
    public synchronized void onPipeTick(CodaBlockEntityTick e) {
        if (ticking.size() < 2048) ticking.add(new Node(e.dimension(), e.position()));
    }

    /** Called after each server tick. No world view is retained beyond this call. */
    public synchronized void onServerTick(CodaServerTickContext tick) {
        if (!session.equals(tick.sessionId())) {
            session = tick.sessionId();
            lastDelivery.clear();
            movedItems = 0;
            lastStatus = "Scanning loaded pipes in the current world.";
        }
        Set<Node> seen = Set.copyOf(ticking);
        ticking.clear();
        if (tick.tick() % SCAN_INTERVAL != 0 || tick.world().isEmpty()) return;

        CodaWorldView world = tick.world().orElseThrow();
        int routes = 0;
        int wood = 0;
        int moved = 0;
        String issue = seen.isEmpty()
                ? "No ticking wooden or stone pipes found in loaded chunks."
                : "No complete chest-to-chest pipe route.";
        // Never run a search on unloaded/removed nodes even if a stale ticker
        // event slipped through while the chunk was being unloaded.
        for (Node node : seen) {
            try {
                if (!world.isChunkLoaded(node.dimension(), node.pos())
                        || !world.isBlock(node.dimension(), node.pos(), BuildCraftLiteMod.WOOD))
                    continue;
                if (++wood > MAX_WOODEN) {
                    issue = "Too many wooden pipes in this area; capped at 16.";
                    break;
                }
                Optional<Route> path = findRoute(world, node);
                if (path.isEmpty()) {
                    issue = "A wooden pipe has no reachable receiving chest through stone pipes.";
                    continue;
                }
                Route route = path.orElseThrow();
                if (!enginePowered(world, node)) {
                    issue = "Wooden pipe needs an adjacent powered Redstone Engine.";
                    continue;
                }
                routes++;
                // Allow at least four ticks per pipe and a 12-tick minimum.
                // This is NOT instantaneous long-distance chest teleporting.
                int travelTicks = Math.max(12, route.pipeCount() * 4);
                long previous = lastDelivery.getOrDefault(node, Long.MIN_VALUE / 2);
                if (tick.tick() - previous < travelTicks) continue;
                int transferred = world.transfer(node.dimension(),
                        route.source(), route.target(), ITEMS_PER_PULSE);
                if (transferred > 0) {
                    moved += transferred;
                    lastDelivery.put(node, tick.tick());
                } else {
                    issue = "Pipe route connected; source empty or destination full.";
                }
            } catch (Exception ex) {
                // Malformed native inventory mappings must not disable the
                // entire server listener or duplicate/lose player items.
                issue = "Coda detected an unsafe/unsupported transfer: "
                        + ex.getClass().getSimpleName();
                System.err.println("[BuildCraft Lite] " + issue + ": " + ex.getMessage());
            }
        }
        validatedRoutes = routes;
        movedItems += moved;
        lastDelivery.keySet().retainAll(seen);
        if (routes > 0 && moved > 0) {
            lastStatus = "Working: " + routes + " verified route(s), "
                    + movedItems + " item(s) delivered this session.";
        } else if (routes > 0) {
            lastStatus = "Connected: " + routes + " verified route(s). " + issue;
        } else {
            lastStatus = issue;
        }
    }

    /**
     * Wooden pipe must touch a source chest. A chain of one or more stone pipes
     * must then lead to a DIFFERENT chest/barrel. A failed search never mutates
     * inventory. Routes are reconstructed from native block IDs every pulse.
     */
    private Optional<Route> findRoute(CodaWorldView world, Node wooden) throws Exception {
        String dimension = wooden.dimension();
        CodaBlockPos origin = wooden.pos();
        List<CodaBlockPos> sources = new ArrayList<>();
        for (CodaBlockPos adjacent : neighbors(origin)) {
            if (world.inventory(dimension, adjacent).isPresent()) sources.add(adjacent);
        }
        if (sources.isEmpty()) return Optional.empty();

        ArrayDeque<Hop> queue = new ArrayDeque<>();
        Set<CodaBlockPos> visited = new HashSet<>();
        visited.add(origin);
        // A receiving chest connected directly to the wooden source is not
        // a transport route; at least one stone segment must be traversed.
        for (CodaBlockPos next : neighbors(origin)) {
            if (stone(world, dimension, next) && visited.add(next)) queue.add(new Hop(next, 1));
        }
        while (!queue.isEmpty() && visited.size() <= MAX_NODES) {
            Hop hop = queue.removeFirst();
            for (CodaBlockPos nearby : neighbors(hop.pos())) {
                if (stone(world, dimension, nearby)) {
                    if (visited.size() < MAX_NODES && visited.add(nearby))
                        queue.addLast(new Hop(nearby, hop.distance() + 1));
                } else if (!sources.contains(nearby)
                        && world.inventory(dimension, nearby).isPresent()) {
                    return Optional.of(new Route(sources.get(0), nearby, hop.distance() + 1));
                }
            }
        }
        return Optional.empty();
    }

    /** Engine must actually exist beside the wooden pipe and receive redstone. */
    private static boolean enginePowered(CodaWorldView world, Node wooden) throws Exception {
        for (CodaBlockPos side : neighbors(wooden.pos())) {
            if (world.isChunkLoaded(wooden.dimension(), side)
                    && world.isBlock(wooden.dimension(), side, BuildCraftLiteMod.ENGINE)
                    && world.hasNeighborSignal(wooden.dimension(), side)) return true;
        }
        return false;
    }

    private static boolean stone(CodaWorldView world, String dim, CodaBlockPos pos)
            throws Exception {
        return world.isChunkLoaded(dim, pos)
                && world.isBlock(dim, pos, BuildCraftLiteMod.STONE);
    }

    private static List<CodaBlockPos> neighbors(CodaBlockPos p) {
        List<CodaBlockPos> result = new ArrayList<>(6);
        for (int[] d : SIDES)
            result.add(new CodaBlockPos(p.x() + d[0], p.y() + d[1], p.z() + d[2]));
        return result;
    }

    /** Player-facing Coda diagnostics, no fabricated outlines or flow stats. */
    public String diagnosis() {
        return "[Coda] " + lastStatus + " Active routes: " + validatedRoutes
                + ". Items delivered: " + movedItems + ".";
    }
}
