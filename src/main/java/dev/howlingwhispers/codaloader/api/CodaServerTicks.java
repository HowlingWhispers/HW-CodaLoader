package dev.howlingwhispers.codaloader.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loader-owned tick listener registry. Registrations happen at mod initialize.
 * Dispatch is driven ONLY by the MinecraftServer class hook on the server thread.
 */
public final class CodaServerTicks {
    private CodaServerTicks() {}

    private static final Map<String, Listener> LISTENERS = new LinkedHashMap<>();

    private static final class Listener {
        final String owner;
        final String id;
        final CodaServerTick callback;
        int failures;
        boolean disabled;

        Listener(String owner, String id, CodaServerTick callback) {
            this.owner = owner;
            this.id = id;
            this.callback = callback;
        }
    }

    public static synchronized void register(String owner, String id, CodaServerTick callback) {
        if (owner == null || !owner.matches("[a-z][a-z0-9_]{0,63}")
                || id == null || !id.matches("[a-z][a-z0-9_]{0,63}"))
            throw new IllegalArgumentException("Tick listener owner and ID must be lowercase namespaced identifiers");
        Objects.requireNonNull(callback, "callback");
        String key = owner + ":" + id;
        if (LISTENERS.containsKey(key)) throw new IllegalStateException("Duplicate server tick listener: " + key);
        LISTENERS.put(key, new Listener(owner, id, callback));
    }

    /**
     * Called by the loader's internal server tick lifecycle. Mods should never
     * invoke this directly, and must not assume any other thread is safe.
     */
    public static void dispatch(CodaServerTickContext context) {
        Objects.requireNonNull(context, "context");
        List<Listener> listeners;
        synchronized (CodaServerTicks.class) {
            listeners = new ArrayList<>(LISTENERS.values());
        }
        for (Listener listener : listeners) {
            synchronized (CodaServerTicks.class) {
                if (listener.disabled) continue;
            }
            try {
                listener.callback.onTick(context);
                synchronized (CodaServerTicks.class) {
                    listener.failures = 0;
                }
            } catch (Throwable failure) {
                boolean disable;
                synchronized (CodaServerTicks.class) {
                    listener.failures++;
                    disable = listener.failures >= 3;
                    listener.disabled = disable;
                }
                System.err.println("[H.O.W.L.] Server-tick callback " + listener.owner + ":" + listener.id
                        + " failed" + (disable ? " and has been disabled for this session" : "")
                        + ": " + failure);
            }
        }
    }
}
