package net.minecraft.world;

import net.minecraft.world.item.ItemStack;

/** Minimal fixture for a vanilla chest/barrel-like block entity. */
public interface Container {
    default int getMaxStackSize() { return 64; }
    default boolean canPlaceItem(int slot, ItemStack stack) { return true; }
    int getContainerSize();
    ItemStack getItem(int slot);
}
