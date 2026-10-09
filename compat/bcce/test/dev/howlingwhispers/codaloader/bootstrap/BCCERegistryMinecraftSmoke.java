package dev.howlingwhispers.codaloader.bootstrap;

import buildcraft.lib.platform.registry.BCDeferredRegister;
import buildcraft.lib.platform.registry.BCRegistryEntry;
import buildcraft.lib.platform.registry.RegistryBinding;
import dev.howlingwhispers.codaloader.api.CodaContext;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** Runs BCCE's actual catalog code with real Snapshot 3 objects and the H.O.W.L. binding. */
public final class BCCERegistryMinecraftSmoke {
    private static final AtomicInteger constructions = new AtomicInteger();
    private static BCDeferredRegister<SoundEvent> catalog;
    private static BCRegistryEntry<SoundEvent> first, dependent;

    public static void declare() {
        catalog = BCDeferredRegister.create("minecraft:sound_event", "bcce_bridge_test");
        first = catalog.register("engine", id -> {
            constructions.incrementAndGet();
            return SoundEvent.createVariableRangeEvent(id);
        });
        var context = new CodaContext("0.0.30", "26.4-snapshot-3", Path.of("."),
                Path.of("."), "bcce_bridge_test", List.of("bcce_bridge_test"));
        catalog.register(RegistryBinding.on(context));
        // BCCE permits catalog entries declared after its binder is attached.
        dependent = catalog.register("pipe", id -> {
            if (!first.isBound() || first.value() == null) throw new AssertionError("BCCE dependency order lost");
            constructions.incrementAndGet();
            return SoundEvent.createVariableRangeEvent(id);
        });
        if (constructions.get() != 0 || first.isPresent()) throw new AssertionError("BCCE factory ran early");
        try {
            first.get();
            throw new AssertionError("BCCE unregistered entry readable");
        } catch (IllegalStateException expected) { }
    }

    public static void verify() {
        if (constructions.get() != 2 || !first.isBound() || !dependent.isPresent())
            throw new AssertionError("Original BCCE entries not bound once");
        if (BuiltInRegistries.SOUND_EVENT.getValue(first.getId()) != first.get()
                || BuiltInRegistries.SOUND_EVENT.getValue(dependent.getId()) != dependent.value())
            throw new AssertionError("BCCE catalog and vanilla registry instance differ");
        try {
            catalog.register("late", SoundEvent::createVariableRangeEvent);
            throw new AssertionError("BCCE registration allowed after freeze");
        } catch (IllegalStateException expected) { }
        if (catalog.entries().size() != 2) throw new AssertionError("Rejected entry left phantom BCCE descriptor");
        System.out.println("Original BCCE catalog -> H.O.W.L. -> actual Snapshot 3 registry checks passed.");
    }
}
