package dev.howlingwhispers.codaloader.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Native content factories, evaluated at Minecraft's registry bootstrap boundary. */
public final class CodaRegistryFactories {
    /** key is the actual Minecraft ResourceKey for this entry, not a substitute key. */
    public record Registration(String registry, String id, Object key) {}

    @FunctionalInterface
    public interface Factory<T> {
        T create(Registration registration) throws Exception;
    }

    /** A deferred reference suitable for binding a mod's existing content catalog. */
    public static final class Entry<T> implements Supplier<T> {
        private final String owner, registry, id;
        private final Factory<T> factory;
        private volatile T value;

        private Entry(String owner, String registry, String id, Factory<T> factory) {
            this.owner = owner;
            this.registry = registry;
            this.id = id;
            this.factory = factory;
        }

        public String owner() { return owner; }
        public String registry() { return registry; }
        public String id() { return id; }
        public boolean isBound() { return value != null; }

        @Override public T get() {
            T current = value;
            if (current == null) throw new IllegalStateException("Native entry is not registered yet: " + id);
            return current;
        }

        /** Loader-side operation; factories never run while a catalog is declared. */
        public T create(Object key) throws Exception {
            return Objects.requireNonNull(factory.create(new Registration(registry, id, key)),
                    "Native registry factory returned null: " + id);
        }

        /** Loader-side operation, called only after native registration succeeds. */
        public synchronized void bind(T registered) {
            if (value != null) throw new IllegalStateException("Native entry already bound: " + id);
            value = Objects.requireNonNull(registered);
        }
    }

    private static final Map<String, Entry<?>> ENTRIES = new LinkedHashMap<>();
    private static boolean sealed;

    private CodaRegistryFactories() {}

    public static synchronized <T> Entry<T> register(String owner, String registry,
                                                    String id, Factory<T> factory) {
        if (sealed) throw new IllegalStateException("Native factory declarations closed before registry freeze");
        if (owner == null || !owner.matches("[a-z0-9_.-]{1,64}"))
            throw new IllegalArgumentException("Invalid mod owner: " + owner);
        requireId(registry);
        requireId(id);
        Entry<T> entry = new Entry<>(owner, registry, id, Objects.requireNonNull(factory));
        if (ENTRIES.putIfAbsent(registry + "/" + id, entry) != null)
            throw new IllegalArgumentException("Duplicate native entry: " + registry + "/" + id);
        return entry;
    }

    private static void requireId(String id) {
        if (id == null || id.length() > 150 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid native identifier: " + id);
    }

    /** Loader-only boundary. Declaration order is retained; dependencies must be declared first. */
    public static synchronized List<Entry<?>> seal() {
        sealed = true;
        return List.copyOf(new ArrayList<>(ENTRIES.values()));
    }
}
