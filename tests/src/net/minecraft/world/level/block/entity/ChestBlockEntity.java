package net.minecraft.world.level.block.entity;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Small fixture. Reject injection on demand to exercise rollback. */
public class ChestBlockEntity implements Container {
    private final ItemStack[] stacks;
    private boolean failNextWrite;
    private int updates;

    public ChestBlockEntity(ItemStack... stacks) { this.stacks = stacks.clone(); }
    @Override public int getContainerSize() { return stacks.length; }
    @Override public ItemStack getItem(int slot) { return stacks[slot]; }
    public void setItem(int slot, ItemStack item) {
        if (failNextWrite) {
            failNextWrite = false;
            throw new IllegalStateException("Fixture rejected write");
        }
        stacks[slot] = item;
    }
    public void setChanged() { updates++; }
    public void failNextWrite() { failNextWrite = true; }
    public int updates() { return updates; }
}
