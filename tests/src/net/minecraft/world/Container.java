package net.minecraft.world;

import net.minecraft.world.item.ItemStack;

/** Minimal fixture for a vanilla chest/barrel-like block entity. */
public interface Container {
    int getContainerSize();
    ItemStack getItem(int slot);
}
