package buildcraft.lib.platform.registry;

import dev.howlingwhispers.codaloader.api.CodaContext;

/** H.O.W.L. platform binding for BCCE's original ordered registry catalogs. */
public final class RegistryBinding implements BCRegistryBinder {
    private final CodaContext context;

    private RegistryBinding(CodaContext context) {
        this.context = java.util.Objects.requireNonNull(context);
    }

    public static RegistryBinding on(CodaContext context) { return new RegistryBinding(context); }

    @Override public <T> void register(BCDeferredRegister<T> catalog) {
        catalog.bindEntries(new BCDeferredRegister.EntryBinder<T>() {
            public <I extends T> void bind(BCRegistryEntry<I> entry) {
                var nativeEntry = context.registerNativeRegistry(catalog.registryId(),
                        entry.getId().toString(), registration -> entry.factory().get());
                entry.bind(nativeEntry, nativeEntry::isBound);
            }
        });
    }
}
