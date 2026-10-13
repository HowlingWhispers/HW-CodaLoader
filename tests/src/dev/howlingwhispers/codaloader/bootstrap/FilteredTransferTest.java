package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.Set;

public final class FilteredTransferTest {
    private static int checks;
    private static void check(boolean yes,String what) { checks++; if (!yes) throw new AssertionError(what); }
    public static void main(String[] args) throws Exception {
        var server=new IntegratedServer(); var level=new MinecraftServer.Level("minecraft:overworld");
        var chunk=new MinecraftServer.Chunk(); level.getChunkSource().addLoaded(0,0,chunk); server.addWorld(level);
        var from=new CodaBlockPos(0,64,0); var to=new CodaBlockPos(1,64,0);
        var source=new ChestBlockEntity(new ItemStack("minecraft:gold_ingot",10,64,"name:Gold"),new ItemStack("minecraft:iron_ingot",8,64,"name:Iron"));
        var target=new ChestBlockEntity(ItemStack.EMPTY,ItemStack.EMPTY);
        chunk.put(new BlockPos(0,64,0),source); chunk.put(new BlockPos(1,64,0),target);
        var world=new MinecraftSingleplayerWorld(server,level);
        check(world.itemInventory(from).orElseThrow().count("minecraft:iron_ingot")==8,"Exact item IDs read");
        check(world.transferFiltered(from,to,3,CodaItemFilter.only("minecraft:iron_ingot"))==3,"Whitelist skips unmatched first slot");
        check(source.getItem(0).getCount()==10 && source.getItem(1).getCount()==5,"Only matching stock decreases");
        check(target.getItem(0).components().equals("name:Iron"),"Filtered transfer retains data components");
        check(world.transferFiltered(from,to,2,new CodaItemFilter(CodaItemFilter.Mode.BLACKLIST,Set.of("minecraft:iron_ingot")))==2,"Blacklist transfers other item");
        check(target.getItem(1).components().equals("name:Gold"),"Distinct components intact");
        check(world.transferFiltered(from,to,1,new CodaItemFilter(CodaItemFilter.Mode.WHITELIST,Set.of()))==0,"Empty whitelist writes nothing");
        target.failNextWrite();
        try { world.transferFiltered(from,to,1,CodaItemFilter.only("minecraft:iron_ingot")); throw new AssertionError("Setter error missing"); }
        catch (java.io.IOException expected) { checks++; }
        check(source.getItem(1).getCount()==5 && target.getItem(0).getCount()==3,"Filtered setter failure restores original stock");
        check(level.getChunkSource().forcedLoads()==0,"No chunks forced loaded");
        System.out.println("PASS: "+checks+" native adapter filtered transfer fixture checks");
    }
}
