package net.minecraft.world.item;

/** Copy-preserving Minecraft stack fixture, never packaged in H.O.W.L. */
public final class ItemStack {
    public static final ItemStack EMPTY = new ItemStack("minecraft:air", 0, 64, "");
    private final String itemId;
    private final int count;
    private final int max;
    private final String components;

    public ItemStack(int count, int max) { this("minecraft:stone", count, max, ""); }
    public ItemStack(String itemId, int count, int max, String components) {
        this.itemId = itemId;
        this.count = count;
        this.max = max;
        this.components = components;
    }

    public int getCount() { return count; }
    public int getMaxStackSize() { return max; }
    public boolean isEmpty() { return count == 0; }
    public ItemStack copy() { return new ItemStack(itemId, count, max, components); }
    public ItemStack copyWithCount(int amount) { return new ItemStack(itemId, amount, max, components); }
    public String components() { return components; }

    public static boolean isSameItemSameComponents(ItemStack left, ItemStack right) {
        return left.itemId.equals(right.itemId) && left.components.equals(right.components);
    }
}
