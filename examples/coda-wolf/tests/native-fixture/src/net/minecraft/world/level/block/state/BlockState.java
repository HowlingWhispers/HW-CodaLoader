package net.minecraft.world.level.block.state;
import net.minecraft.world.level.block.Block;
public record BlockState(Block block) {
    public Block getBlock() {return block;}
}
