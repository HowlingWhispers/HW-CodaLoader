package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaServerTickContext;
import dev.howlingwhispers.codaloader.api.CodaServerTicks;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

/** Runs after each native server tick, on the server thread. */
public final class CodaServerTickLifecycle {
    private static final Map<Object, State> STATES = new WeakHashMap<>();

    private static final class State {
        final String sessionId = UUID.randomUUID().toString();
        long ticks;
    }

    private CodaServerTickLifecycle() {}

    public static void afterTick(Object server) {
        Objects.requireNonNull(server, "server");
        CodaServerTickContext context;
        synchronized (STATES) {
            State state = STATES.computeIfAbsent(server, unused -> new State());
            context = new CodaServerTickContext(state.sessionId, ++state.ticks);
        }
        // The tick API deliberately does not expose unsafe Minecraft objects.
        CodaServerTicks.dispatch(context);
    }
}
