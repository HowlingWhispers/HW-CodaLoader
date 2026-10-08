package net.minecraft.world.item;

/** Fixture only. Never shipped inside H.O.W.L. */
public record ItemStack(int count, int maxStackSize) {
    public int getCount() { return count; }
    public int getMaxStackSize() { return maxStackSize; }
}
