package net.minecraft.core.registries;
/** Test-only registry ID fixture. */
public final class BuiltInRegistries {
    public static final Registry ITEM=new Registry();
    public static final class Registry { public String getKey(Object item) { return item.toString(); } }
}
