package dev.howlingwhispers.codaloader.bootstrap;
import dev.howlingwhispers.codaloader.api.*;
import java.lang.reflect.*;
import java.util.*;

/** Real Mojang ServerLevel, villager physics/pathfinding, loot, tools and inventories in a disposable GameTest world.
 * The integrated-server identity is a fixture, as GameTestServer has no client. Does not establish GUI/integrated lifecycle correctness.
 */
public final class LivingWorldNativeSmoke {
    static UUID id=UUID.randomUUID();static int tick,checks;static boolean finished,scope;static Object integrated;
    static CodaBlockPos stock,target;static double initialX;
    static Object call(Object o,String name,Object...args)throws Exception{return CommandReflection.call(o,name,args);}
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static Object pos(CodaBlockPos p)throws Exception{return Class.forName("net.minecraft.core.BlockPos").getConstructor(int.class,int.class,int.class).newInstance(p.x(),p.y(),p.z());}
    static Object block(String id)throws Exception{
        Object registry=Class.forName("net.minecraft.core.registries.BuiltInRegistries").getField("BLOCK").get(null);
        return call(registry,"getValue",call(Class.forName("net.minecraft.resources.Identifier"),"parse",id));
    }
    static Object item(String id,int n)throws Exception{
        Object registry=Class.forName("net.minecraft.core.registries.BuiltInRegistries").getField("ITEM").get(null);
        Object value=call(registry,"getValue",call(Class.forName("net.minecraft.resources.Identifier"),"parse",id));
        return Class.forName("net.minecraft.world.item.ItemStack").getConstructor(Class.forName("net.minecraft.world.level.ItemLike"),int.class).newInstance(value,n);
    }
    static void put(Object level,CodaBlockPos p,String name)throws Exception{call(level,"setBlock",pos(p),call(block(name),"defaultBlockState"),3);}
    public static void tick(Object server){
        if(finished)return;scope=true;
        try{
            tick++;Object level=call(server,"overworld");
            if(tick==1){
                // Identity fixture only. Every block, entity, path and inventory below belongs to a real, running ServerLevel.
                Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);
                integrated=u.getMethod("allocateInstance",Class.class).invoke(f.get(null),Class.forName("net.minecraft.client.server.IntegratedServer"));
                try(var view=new MinecraftWorldInventoryView(server)){
                    try{view.living("minecraft:overworld");throw new AssertionError("Dedicated world accepted");}catch(IllegalStateException expected){checks++;}
                }
                for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
                    call(level,"setChunkForced",x,z,true);call(level,"getChunk",x,z); // test setup alone loads chunks
                }
                for(int x=-3;x<=9;x++)for(int z=-3;z<=4;z++){
                    put(level,new CodaBlockPos(x,79,z),"minecraft:dirt");
                    for(int y=80;y<=84;y++)put(level,new CodaBlockPos(x,y,z),"minecraft:air");
                }
            }
            var world=new MinecraftLivingWorld(integrated,level,()->{if(!scope)throw new IllegalStateException("Expired test scope");});
            if(tick==2){
                check(world.block(new CodaBlockPos(0,79,0)).orElseThrow().id().equals("minecraft:dirt"),"Actual loaded block registry sample");
                check(world.block(new CodaBlockPos(16000,80,0)).isEmpty(),"Unloaded position is absent");
                check(world.spawn(id,"Frontier native test",new CodaBlockPos(0,80,0)),"Native villager spawn");
                check(!world.spawn(id,"Duplicate",new CodaBlockPos(1,80,0)),"Native duplicate UUID rejected");
                initialX=world.actor(id).orElseThrow().x();
            }
            if(tick==20){
                check(world.walk(id,new CodaBlockPos(5,80,0),.65),"Real navigation can reach the target after native entity activation");
            }
            if(tick>2&&tick<90)world.actor(id); // Frontier removes native competing jobs each decision
            if(tick==90){
                var actor=world.actor(id).orElseThrow();
                check(actor.x()>initialX+2,"Native entity actually walked, rather than only accepting a path");
                stock=new CodaBlockPos((int)Math.floor(actor.x())+1,80,0);target=new CodaBlockPos((int)Math.floor(actor.x()),80,1);
                put(level,stock,"minecraft:chest");Object chest=call(level,"getBlockEntity",pos(stock));
                call(chest,"setItem",0,item("minecraft:bread",4));call(chest,"setItem",1,item("minecraft:stone_axe",1));call(chest,"setItem",2,item("minecraft:oak_sapling",1));
                check(world.eat(id,stock,Set.of("minecraft:bread")),"Settler consumes native food");
                check(((Number)call(call(chest,"getItem",0),"getCount")).intValue()==3,"Exactly one food item consumed");
                check(world.equip(id,stock,Set.of("minecraft:stone_axe")),"Tool issued from real chest");
                check((Boolean)call(call(chest,"getItem",1),"isEmpty"),"Issued tool was removed from stockpile");
                put(level,target,"minecraft:oak_log");
                check(world.harvest(id,target,"minecraft:oak_log",stock,Set.of("minecraft:stone_axe")),"Native loot harvested with supplied tool");
                check(world.block(target).orElseThrow().air(),"Harvest removes real block");
                check(world.carried(id)==1,"Vanilla log drop is in settler's native inventory");
                check(world.collect(id,stock,CodaItemFilter.only("minecraft:oak_sapling"),1)==1,"Real sapling collected");
                check(world.plant(id,target,"minecraft:oak_sapling",stock),"Sapling planted with survival check and consumed inventory");
                check(world.block(target).orElseThrow().id().equals("minecraft:oak_sapling"),"Native planted block exists");
                check(world.carried(id)==1,"Planting consumed only the sapling");
                check(world.deliver(id,stock)==1&&world.carried(id)==0,"Real harvested log delivered to chest");
                check(world.equip(id,stock,Set.of()),"Changing to a bare-hand job returns the old tool through a native equipment container");
                check((Boolean)call(call(call(level,"getEntity",id),"getMainHandItem"),"isEmpty"),"Issued tool is not duplicated in the worker's hand");
                Object bed=block("minecraft:red_bed"),state=call(bed,"defaultBlockState");
                Object part=Class.forName("net.minecraft.world.level.block.BedBlock").getField("PART").get(null);
                Object head=Class.forName("net.minecraft.world.level.block.state.properties.BedPart").getField("HEAD").get(null);
                CodaBlockPos bedPos=new CodaBlockPos((int)Math.floor(actor.x()),80,-1);
                call(level,"setBlock",pos(bedPos),call(state,"setValue",part,head),3);
                check(world.sleep(id,bedPos)&&world.actor(id).orElseThrow().sleeping(),"Native bed sleep, not a simulated sleeping flag");
                world.wake(id);check(!world.actor(id).orElseThrow().sleeping(),"Native wake");
                float health=world.actor(id).orElseThrow().health();world.damage(id,1);
                check(world.actor(id).orElseThrow().health()<health,"Real starvation damage");
                scope=false;
                try{world.clock();throw new AssertionError("Expired scope accepted");}catch(IllegalStateException expected){checks++;}
                finished=true;System.out.println("PASS: "+checks+" running Snapshot 3 living-world checks");call(server,"halt",false);
            }
            if(tick>120)throw new AssertionError("Native scenario timed out");
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}finally{scope=false;}
    }
    public static void finish(){System.exit(finished?0:1);}
}
