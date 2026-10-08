package dev.howlingwhispers.codaloader.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Server-side placement events emitted by the native BlockItem adapter. */
public final class CodaBlockPlacements {
    public record Placement(String dimension, CodaBlockPos position, String blockId) {}
    private record Subscriber(String owner, Consumer<Placement> listener) {}
    private static final List<Subscriber> LISTENERS = new ArrayList<>();

    private CodaBlockPlacements() {}

    public static synchronized void register(String owner, Consumer<Placement> listener) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(listener);
        if (!owner.matches("[a-z0-9_.-]{1,64}"))
            throw new IllegalArgumentException("Invalid mod owner");
        LISTENERS.add(new Subscriber(owner, listener));
    }

    /** Called after a successful native server BlockItem placement. */
    public static void dispatch(Placement placed) {
        List<Subscriber> listeners;
        synchronized (CodaBlockPlacements.class) { listeners = List.copyOf(LISTENERS); }
        for (Subscriber listener : listeners) {
            try { listener.listener().accept(placed); }
            catch (Throwable error) {
                System.err.println("[H.O.W.L.] Placement callback failed for "
                        + listener.owner() + ": " + error);
            }
        }
    }
}
