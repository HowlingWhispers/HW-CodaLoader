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
    static Object registry(String registry,String id)throws Exception{
        Object r=Class.forName("net.minecraft.core.registries.BuiltInRegistries").getField(registry).get(null);
        return call(r,"getValue",call(Class.forName("net.minecraft.resources.Identifier"),"parse",id));
    }
    static Object nativeMob(Object level,String name,String type)throws Exception{
        return Class.forName(name).getConstructor(Class.forName("net.minecraft.world.entity.EntityType"),Class.forName("net.minecraft.world.level.Level"))
                .newInstance(registry("ENTITY_TYPE",type),level);
    }
    static boolean avatar(Object e)throws Exception{return Class.forName("net.minecraft.world.entity.decoration.Mannequin").isInstance(e);}
    static void legacy(Object level,CodaLivingWorld world)throws Exception{
        UUID oldId=UUID.randomUUID();Object old=nativeMob(level,"net.minecraft.world.entity.npc.villager.Villager","minecraft:villager");
        call(old,"setUUID",oldId);call(old,"addTag","howl.settler");call(old,"setPos",-2.5,80.0,-2.5);
        call(old,"setCustomName",call(Class.forName("net.minecraft.network.chat.Component"),"literal","LegacySettler"));call(old,"setHealth",7f);
        call(call(old,"getInventory"),"setItem",0,item("minecraft:bread",3));
        Object hand=Class.forName("net.minecraft.world.entity.EquipmentSlot").getField("MAINHAND").get(null);
        call(old,"setItemSlot",hand,item("minecraft:stone_pickaxe",1));check((Boolean)call(level,"addFreshEntity",old),"Legacy fixture spawned");
        check(world.actor(oldId).orElseThrow().health()==7,"Migration retains native health and saved identity");
        Object migrated=call(level,"getEntity",oldId);check(avatar(migrated)&&(Boolean)call(old,"isRemoved"),"Loaded legacy villager replaced by one player-shaped NPC");
        check(world.carried(oldId)==3,"Migration preserves carried food");
        check(call(call(migrated,"getMainHandItem"),"getCount").equals(1),"Migration retains native tool");
        check(world.rename(oldId,"jeb_"),"Rename sets native profile");
        check(call(call(migrated,"getCustomName"),"getString").equals("jeb_")&&call(call(migrated,"getProfile"),"name").equals(Optional.of("jeb_")),"Display name and skin account agree");
        Object victim=nativeMob(level,"net.minecraft.world.entity.npc.villager.Villager","minecraft:villager");UUID victimId=UUID.randomUUID();
        call(victim,"setUUID",victimId);call(victim,"setPos",-2.5,80.0,2.5);call(victim,"addTag","howl.settler");
        call(victim,"setCustomName",call(Class.forName("net.minecraft.network.chat.Component"),"literal","Edda 2073"));call(victim,"setItemSlot",hand,item("minecraft:iron_axe",1));
        call(level,"addFreshEntity",victim);
        Object zombie=nativeMob(level,"net.minecraft.world.entity.monster.zombie.Zombie","minecraft:zombie");
        check((Boolean)call(zombie,"convertVillagerToZombieVillager",level,victim),"Reproduces old vanilla conversion of a tagged villager");
        check(world.actor(victimId).isEmpty(),"Vanilla conversion loses the saved actor UUID");
        check(!world.recover(victimId,"Wrong name",new CodaBlockPos(-3,80,2)),"Recovery refuses unrelated names");
        check(world.recover(victimId,"Edda 2073",new CodaBlockPos(-3,80,2)),"Nearby uniquely named Frontier zombie repairs to original saved UUID");
        Object recovered=call(level,"getEntity",victimId);check(avatar(recovered),"Recovered settler has player body");
        check(MinecraftSingleplayerWorld.itemId(call(recovered,"getMainHandItem")).equals("minecraft:iron_axe"),"Recovery preserves existing equipment");
        check(!world.recover(victimId,"Edda 2073",new CodaBlockPos(-3,80,2)),"Repeat recovery cannot duplicate a settler");
        Object ordinary=nativeMob(level,"net.minecraft.world.entity.monster.zombie.ZombieVillager","minecraft:zombie_villager");
        call(ordinary,"setPos",-2.5,80.0,2.5);call(ordinary,"setCustomName",call(Class.forName("net.minecraft.network.chat.Component"),"literal","Ordinary"));call(level,"addFreshEntity",ordinary);
        check(!world.recover(UUID.randomUUID(),"Ordinary",new CodaBlockPos(-3,80,2))&&(Boolean)call(ordinary,"isAlive"),"Ordinary zombie villagers are not adopted or repaired");
        for(int i=0;i<2;i++){
            Object duplicate=nativeMob(level,"net.minecraft.world.entity.monster.zombie.ZombieVillager","minecraft:zombie_villager");
            call(duplicate,"setPos",-2.5+i,80.0,2.5);call(duplicate,"addTag","howl.settler");
            call(duplicate,"setCustomName",call(Class.forName("net.minecraft.network.chat.Component"),"literal","Ambiguous"));call(level,"addFreshEntity",duplicate);
        }
        check(!world.recover(UUID.randomUUID(),"Ambiguous",new CodaBlockPos(-3,80,2)),"Ambiguous legacy names cannot overwrite a saved identity");
        world.damage(oldId,20);check(world.actor(oldId).orElseThrow().health()==0,"Player-shaped NPCs remain mortal");
        check(world.carried(oldId)==0&&(Boolean)call(call(migrated,"getMainHandItem"),"isEmpty"),"Death drops and clears carried food and equipped tool");
        int bread=0,picks=0;
        for(Object drop:(Iterable<?>)call(level,"getAllEntities"))if(Class.forName("net.minecraft.world.entity.item.ItemEntity").isInstance(drop)){
            Object stack=call(drop,"getItem");String key=MinecraftSingleplayerWorld.itemId(stack);
            if(key.equals("minecraft:bread"))bread+=((Number)call(stack,"getCount")).intValue();
            if(key.equals("minecraft:stone_pickaxe"))picks+=((Number)call(stack,"getCount")).intValue();
        }
        check(bread==3&&picks==1,"Death produces exactly the actual supplies, without duplicates");
    }
    static void savedInventory(Object level,Object e)throws Exception{
        Object reporter=Class.forName("net.minecraft.util.ProblemReporter").getField("DISCARDING").get(null),registries=call(level,"registryAccess");
        Object bag=CodaSettlerAvatarLifecycle.inventory(e),stack=call(bag,"getItem",0);
        Object customName=Class.forName("net.minecraft.core.component.DataComponents").getField("CUSTOM_NAME").get(null);
        call(stack,"set",customName,call(Class.forName("net.minecraft.network.chat.Component"),"literal","Frontier saved log"));
        Object out=call(Class.forName("net.minecraft.world.level.storage.TagValueOutput"),"createWithContext",reporter,registries);
        call(e,"saveWithoutId",out);Object tag=call(out,"buildResult");
        Object loaded=nativeMob(level,"net.minecraft.world.entity.decoration.Mannequin","minecraft:mannequin");
        call(loaded,"load",call(Class.forName("net.minecraft.world.level.storage.TagValueInput"),"create",reporter,registries,tag));
        check(call(loaded,"getUUID").equals(call(e,"getUUID")),"Native avatar save/reload preserves UUID");
        check(call(call(CodaSettlerAvatarLifecycle.inventory(loaded),"getItem",0),"getCount").equals(1),"Native avatar save/reload preserves carried log");
        check(call(call(call(CodaSettlerAvatarLifecycle.inventory(loaded),"getItem",0),"get",customName),"getString").equals("Frontier saved log"),"Inventory save/reload preserves native ItemStack components");
        check(call(call(loaded,"getProfile"),"name").equals(call(call(e,"getProfile"),"name")),"Native avatar save/reload preserves skin profile");
    }
    public record TargetSource(Object server,Object level,Object player){
        public Object getServer(){return server;}
        public Object getLevel(){return level;}
        public Object getPlayerOrException(){return player;}
    }
    public record WrongThread(){public boolean isSameThread(){return false;}}
    static void targeting(Object server,Object level)throws Exception{
        Class<?> profile=Class.forName("com.mojang.authlib.GameProfile"), info=Class.forName("net.minecraft.server.level.ClientInformation");
        Object player=Class.forName("net.minecraft.server.level.ServerPlayer").getConstructor(
                Class.forName("net.minecraft.server.MinecraftServer"),Class.forName("net.minecraft.server.level.ServerLevel"),profile,info).newInstance(
                        server,level,profile.getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),"TargetTest"),call(info,"createDefault"));
        call(player,"setPos",.5,80.0,.5);call(player,"setYRot",0f);call(player,"setYHeadRot",0f);call(player,"setXRot",0f);
        var adapter=new MinecraftCommandContext(new TargetSource(server,level,player));
        CodaBlockPos selected=new CodaBlockPos(0,81,3), wall=new CodaBlockPos(0,81,2);
        put(level,selected,"minecraft:chest");
        check(adapter.targetBlock().orElseThrow().equals(new CodaBlockTarget("minecraft:overworld",selected)),"Real server-player view selects chest within native reach");
        put(level,selected,"minecraft:barrel");check(adapter.targetBlock().orElseThrow().position().equals(selected),"Real view selects barrel");
        put(level,wall,"minecraft:stone");check(adapter.targetBlock().orElseThrow().position().equals(wall),"View ray stops at intervening wall");
        put(level,wall,"minecraft:air");put(level,selected,"minecraft:air");
        check(adapter.targetBlock().isEmpty(),"Looking at air gives no block target");
        CodaBlockPos far=new CodaBlockPos(0,81,7);put(level,far,"minecraft:chest");
        check(adapter.targetBlock().isEmpty(),"Container outside native interaction reach is not selected");put(level,far,"minecraft:air");
        put(level,selected,"minecraft:chest");call(player,"setYRot",180f);call(player,"setYHeadRot",180f);
        check(adapter.targetBlock().isEmpty(),"Container behind the player is not selected");
        call(player,"setPos",16000.5,80.0,.5);call(player,"setYRot",0f);call(player,"setYHeadRot",0f);
        Object chunks=call(level,"getChunkSource");int count=((Number)call(chunks,"getLoadedChunksCount")).intValue();
        check(adapter.targetBlock().isEmpty(),"Unloaded view corridor is refused");
        check(((Number)call(chunks,"getLoadedChunksCount")).intValue()==count,"Targeting never loads an absent chunk");
        try{new MinecraftCommandContext(new TargetSource(new WrongThread(),level,player)).targetBlock();throw new AssertionError("Wrong thread accepted");}
        catch(IllegalStateException expected){checks++;}
        put(level,selected,"minecraft:air");
    }
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
                targeting(server,level);
                check(world.block(new CodaBlockPos(0,79,0)).orElseThrow().id().equals("minecraft:dirt"),"Actual loaded block registry sample");
                check(world.block(new CodaBlockPos(16000,80,0)).isEmpty(),"Unloaded position is absent");
                check(world.spawn(id,"Frontier native test",new CodaBlockPos(0,80,0)),"Native villager spawn");
                check(avatar(call(level,"getEntity",id)),"New settlers are player-shaped NPCs, not villagers or zombies");
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
                savedInventory(level,call(level,"getEntity",id));
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
                legacy(level,world);
                scope=false;
                try{world.clock();throw new AssertionError("Expired scope accepted");}catch(IllegalStateException expected){checks++;}
                finished=true;System.out.println("PASS: "+checks+" running Snapshot 3 living-world checks");call(server,"halt",false);
            }
            if(tick>120)throw new AssertionError("Native scenario timed out");
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}finally{scope=false;}
    }
    public static void finish(){System.exit(finished?0:1);}
}
