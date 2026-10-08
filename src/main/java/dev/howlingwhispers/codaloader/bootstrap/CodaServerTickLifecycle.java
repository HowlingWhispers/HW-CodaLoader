package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaServerTickContext;
import dev.howlingwhispers.codaloader.api.CodaServerTicks;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/** Runs after native MinecraftServer ticks, on the authoritative server thread. */
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

        // Fixtures with no Minecraft world API still run the existing listeners.
        // No reflection is performed until a mod explicitly asks for dimensions
        // or an already-loaded inventory.
        try (MinecraftWorldInventoryView view = new MinecraftWorldInventoryView(server)) {
            CodaServerTicks.dispatch(new CodaServerTickContext(
                    context.sessionId(), context.tick(), Optional.of(view)));
        }
    }
}
