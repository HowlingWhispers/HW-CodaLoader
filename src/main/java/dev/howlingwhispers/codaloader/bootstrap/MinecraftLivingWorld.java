package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.*;
import java.util.*;

/** Exact Snapshot 3 integrated-server bridge. No native objects leave the loader. */
final class MinecraftLivingWorld implements CodaLivingWorld {
    static final String TAG = "howl.settler";
    private final Object level, server;
    private final ClassLoader loader;
    private final Runnable requireActive;
    private final MinecraftSingleplayerWorld storage;

    MinecraftLivingWorld(Object server, Object level, Runnable requireActive) throws Exception {
        this.server = server; this.level = level; this.requireActive = requireActive;
        loader = level.getClass().getClassLoader();
        storage = new MinecraftSingleplayerWorld(server, level); // rejects dedicated servers
    }
    private Object call(Object target, String name, Object... args) throws Exception {
        requireActive.run(); return CommandReflection.call(target, name, args);
    }
    private Class<?> type(String name) throws Exception { requireActive.run(); return Class.forName(name,true,loader); }
    private Object pos(CodaBlockPos p) throws Exception {
        return type("net.minecraft.core.BlockPos").getConstructor(int.class,int.class,int.class).newInstance(p.x(),p.y(),p.z());
    }
    private Object state(CodaBlockPos p) throws Exception {
        requireActive.run();
        if (!storage.isLoaded(p)) return null;
        Object chunk = call(call(level,"getChunkSource"),"getChunkNow",p.x()>>4,p.z()>>4);
        return call(chunk,"getBlockState",pos(p));
    }
    private String key(String registry, Object value) throws Exception {
        return call(type("net.minecraft.core.registries.BuiltInRegistries").getField(registry).get(null),"getKey",value).toString();
    }
    private Object value(String registry, String id) throws Exception {
        Object r=type("net.minecraft.core.registries.BuiltInRegistries").getField(registry).get(null);
        Object v=call(r,"getValue",call(type("net.minecraft.resources.Identifier"),"parse",id));
        if (v==null || !id.equals(call(r,"getKey",v).toString())) throw new IllegalArgumentException("Unknown registry ID: "+id);
        return v;
    }
    @Override public long clock() throws Exception { return ((Number)call(level,"getOverworldClockTime")).longValue(); }
    @Override public Optional<Block> block(CodaBlockPos p) throws Exception {
        Object s=state(p); if(s==null)return Optional.empty();
        Map<String,String> properties=new HashMap<>();
        for(Object property:(Collection<?>)call(s,"getProperties"))
            properties.put(call(property,"getName").toString(),call(s,"getValue",property).toString().toLowerCase(Locale.ROOT));
        return Optional.of(new Block(key("BLOCK",call(s,"getBlock")),properties,
                (Boolean)call(s,"isSolid"),!(Boolean)call(call(s,"getFluidState"),"isEmpty")));
    }
    private Object entity(UUID id) throws Exception {
        Object e=call(level,"getEntity",Objects.requireNonNull(id));
        if(e==null)return null;
        if(!type("net.minecraft.world.entity.npc.villager.Villager").isInstance(e)
                || !((Set<?>)call(e,"entityTags")).contains(TAG))
            throw new IllegalArgumentException("Entity is not a H.O.W.L. settler");
        return e;
    }
    @Override public Optional<Actor> actor(UUID id) throws Exception {
        Object e=entity(id); if(e==null)return Optional.empty();
        // Vanilla physics/navigation still tick, but its jobs, breeding and bed selection do not compete.
        call(e,"removeFreeWill");
        return Optional.of(new Actor(id,number(e,"getX"),number(e,"getY"),number(e,"getZ"),
                ((Number)call(e,"getHealth")).floatValue(),(Boolean)call(e,"isSleeping")));
    }
    private double number(Object o,String name)throws Exception{return ((Number)call(o,name)).doubleValue();}
    private boolean near(Object e,CodaBlockPos p,double distance)throws Exception{
        return Math.pow(number(e,"getX")-p.x()-.5,2)+Math.pow(number(e,"getY")-p.y(),2)+Math.pow(number(e,"getZ")-p.z()-.5,2)<=distance*distance;
    }
    private boolean standable(CodaBlockPos p)throws Exception{
        var feet=block(p);var head=block(new CodaBlockPos(p.x(),p.y()+1,p.z()));
        var floor=block(new CodaBlockPos(p.x(),p.y()-1,p.z()));
        return feet.isPresent()&&head.isPresent()&&floor.isPresent()
                &&feet.get().air()&&head.get().air()&&floor.get().solid()&&!floor.get().fluid()
                &&!Set.of("minecraft:magma_block","minecraft:campfire","minecraft:soul_campfire","minecraft:cactus").contains(floor.get().id());
    }
    @Override public boolean spawn(UUID id,String name,CodaBlockPos feet)throws Exception{
        requireActive.run(); Objects.requireNonNull(id);
        if(name==null||name.isBlank()||name.length()>64)throw new IllegalArgumentException("Invalid settler name");
        if(call(level,"getEntityInAnyDimension",id)!=null)return false;
        if(!standable(feet))return false;
        Object nativePos=pos(feet);
        if(!(Boolean)call(level,"isInWorldBounds",nativePos)
                ||!(Boolean)call(call(level,"getWorldBorder"),"isWithinBounds",nativePos))return false;
        Object e=type("net.minecraft.world.entity.npc.villager.Villager")
                .getConstructor(type("net.minecraft.world.entity.EntityType"),type("net.minecraft.world.level.Level"))
                .newInstance(value("ENTITY_TYPE","minecraft:villager"),level);
        call(e,"setUUID",id);call(e,"addTag",TAG);
        call(e,"setPos",feet.x()+.5,(double)feet.y(),feet.z()+.5);
        if(!(Boolean)call(level,"noCollision",e))return false;
        call(e,"setPersistenceRequired");call(e,"removeFreeWill");
        call(e,"setCustomName",call(type("net.minecraft.network.chat.Component"),"literal",name));
        call(e,"setCustomNameVisible",true);
        call(e,"setOffers",type("net.minecraft.world.item.trading.MerchantOffers").getConstructor().newInstance());
        call(call(e,"getNavigation"),"setCanOpenDoors",true);
        return (Boolean)call(level,"addFreshEntity",e);
    }
    /** Preflight the whole native pathfinding region; do not generate neighbouring chunks. */
    private boolean navigationLoaded(Object e)throws Exception{
        int x=(int)Math.floor(number(e,"getX")),z=(int)Math.floor(number(e,"getZ"));
        for(int cx=(x-48)>>4;cx<=(x+48)>>4;cx++)for(int cz=(z-48)>>4;cz<=(z+48)>>4;cz++)
            if(!storage.isLoaded(new CodaBlockPos(cx<<4,64,cz<<4)))return false;
        return true;
    }
    @Override public boolean walk(UUID id,CodaBlockPos target,double speed)throws Exception{
        if(!Double.isFinite(speed)||speed<.1||speed>1.2)throw new IllegalArgumentException("Invalid walking speed");
        Object e=entity(id);if(e==null||!near(e,target,32)||!storage.isLoaded(target)||!navigationLoaded(e))return false;
        Object nav=call(e,"getNavigation");call(nav,"setRequiredPathLength",32f);
        Object path=call(nav,"createPath",pos(target),1);
        if(path==null||!(Boolean)call(path,"canReach"))return false;
        return (Boolean)call(nav,"moveTo",path,speed);
    }
    @Override public boolean sleep(UUID id,CodaBlockPos bed)throws Exception{
        Object e=entity(id);if(e==null||!near(e,bed,2.5))return false;
        var b=block(bed);if(b.isEmpty()||!b.get().id().endsWith("_bed")||!"head".equals(b.get().properties().get("part")))return false;
        if((Boolean)call(e,"isSleeping"))return true;
        if("true".equals(b.get().properties().get("occupied")))return false;
        call(call(e,"getNavigation"),"stop");
        return (Boolean)call(e,"startSleeping",pos(bed));
    }
    @Override public void wake(UUID id)throws Exception{Object e=entity(id);if(e!=null&&(Boolean)call(e,"isSleeping"))call(e,"stopSleeping");}
    @Override public void damage(UUID id,float amount)throws Exception{
        if(!Float.isFinite(amount)||amount<=0||amount>20)throw new IllegalArgumentException("Invalid damage");
        Object e=entity(id);if(e!=null)call(e,"hurtServer",level,call(call(e,"damageSources"),"starve"),amount);
    }
    @Override public void discard(UUID id)throws Exception{Object e=entity(id);if(e!=null)call(e,"discard");}
    private Object inventory(Object e)throws Exception{return call(e,"getInventory");}
    private int count(Object stack)throws Exception{return ((Number)call(stack,"getCount")).intValue();}
    private int size(Object container)throws Exception{return ((Number)call(container,"getContainerSize")).intValue();}
    private Object copy(Object stack)throws Exception{return call(stack,"copy");}
    private Object empty()throws Exception{return type("net.minecraft.world.item.ItemStack").getField("EMPTY").get(null);}
    private int find(Object container,Set<String> ids)throws Exception{
        int n=size(container);if(n<1||n>54)throw new IllegalArgumentException("Unsupported native inventory");
        for(int i=0;i<n;i++){Object s=call(container,"getItem",i);if(count(s)>0&&ids.contains(MinecraftSingleplayerWorld.itemId(s)))return i;}return -1;
    }
    private void set(Object container,int slot,Object stack)throws Exception{
        call(container,"setItem",slot,stack);call(container,"setChanged");
    }
    private void consume(Object container,int slot)throws Exception{
        Object old=copy(call(container,"getItem",slot));
        try{set(container,slot,count(old)==1?empty():call(old,"copyWithCount",count(old)-1));}
        catch(Exception ex){try{set(container,slot,old);}catch(Exception rollback){ex.addSuppressed(rollback);}throw ex;}
    }
    @Override public boolean eat(UUID id,CodaBlockPos stockpile,Set<String> food)throws Exception{
        Objects.requireNonNull(food);Object e=entity(id);if(e==null||!near(e,stockpile,3))return false;
        Object chest=storage.chest(stockpile);if(chest==null)return false;
        int slot=find(chest,food);if(slot<0)return false;consume(chest,slot);return true;
    }
    @Override public int carried(UUID id)throws Exception{
        Object e=entity(id);if(e==null)return 0;Object bag=inventory(e);int total=0;
        for(int i=0;i<size(bag);i++)total+=count(call(bag,"getItem",i));return total;
    }
    @Override public int deliver(UUID id,CodaBlockPos stockpile)throws Exception{
        Object e=entity(id);if(e==null||!near(e,stockpile,3))return 0;
        Object chest=storage.chest(stockpile);if(chest==null)return 0;
        return MinecraftSingleplayerWorld.moveContainers(inventory(e),chest,64,CodaItemFilter.all());
    }
    @Override public int collect(UUID id,CodaBlockPos stockpile,CodaItemFilter filter,int maximum)throws Exception{
        Object e=entity(id);if(e==null||!near(e,stockpile,3))return 0;
        Object chest=storage.chest(stockpile);if(chest==null)return 0;
        return MinecraftSingleplayerWorld.moveContainers(chest,inventory(e),maximum,filter);
    }
    private Object tool(Object e,CodaBlockPos supplies,Set<String> tools)throws Exception{
        Object held=call(e,"getMainHandItem");
        if(count(held)>0&&!tools.contains(MinecraftSingleplayerWorld.itemId(held))){
            if(!near(e,supplies,3))return null;
            Object destination=storage.chest(supplies);if(destination==null)return null;
            Object hand=type("net.minecraft.world.entity.EquipmentSlot").getField("MAINHAND").get(null);
            Object equipment=call(e,"createEquipmentSlotContainer",hand);
            if(MinecraftSingleplayerWorld.moveContainers(equipment,destination,1,CodaItemFilter.all())!=1)return null;
            held=call(e,"getMainHandItem");
        }
        if(tools.isEmpty())return empty();
        if(count(held)>0)return held;
        Object chest=inventory(e);int slot=find(chest,tools);
        if(slot<0&&near(e,supplies,3)){chest=storage.chest(supplies);if(chest!=null)slot=find(chest,tools);}
        if(chest==null||slot<0)return null;
        Object old=copy(call(chest,"getItem",slot)),issued=call(old,"copyWithCount",1);
        Object hand=type("net.minecraft.world.entity.EquipmentSlot").getField("MAINHAND").get(null);
        try{consume(chest,slot);call(e,"setItemSlot",hand,issued);return issued;}
        catch(Exception ex){set(chest,slot,old);call(e,"setItemSlot",hand,held);throw ex;}
    }
    @Override public boolean equip(UUID id,CodaBlockPos supplies,Set<String> tools)throws Exception{
        Object e=entity(id);return e!=null&&tool(e,supplies,tools)!=null;
    }
    @Override public boolean harvest(UUID id,CodaBlockPos target,String expectedId,CodaBlockPos supplies,Set<String> tools)throws Exception{
        Object e=entity(id);if(e==null)return false;
        Object held=tools.isEmpty()?empty():call(e,"getMainHandItem");
        if((!tools.isEmpty()&&(count(held)==0||!tools.contains(MinecraftSingleplayerWorld.itemId(held))))||!near(e,target,6))return false;
        Object s=state(target);if(s==null||!expectedId.equals(key("BLOCK",call(s,"getBlock")))
                ||(Boolean)call(s,"hasBlockEntity")||!(Boolean)call(call(s,"getFluidState"),"isEmpty")
                ||((Number)call(s,"getDestroySpeed",level,pos(target))).floatValue()<0)return false;
        if((Boolean)call(s,"requiresCorrectToolForDrops")&&!(Boolean)call(held,"isCorrectToolForDrops",s))return false;
        Object bag=inventory(e);List<Object> before=new ArrayList<>(),after=new ArrayList<>();
        for(int i=0;i<size(bag);i++){before.add(copy(call(bag,"getItem",i)));after.add(copy(call(bag,"getItem",i)));}
        @SuppressWarnings("unchecked") List<Object> drops=(List<Object>)call(type("net.minecraft.world.level.block.Block"),"getDrops",s,level,pos(target),null,e,held);
        for(Object drop:drops){int remaining=count(drop);
            for(int i=0;i<after.size()&&remaining>0;i++){
                Object current=after.get(i);int n=count(current);
                if(n>0&&!(Boolean)call(type("net.minecraft.world.item.ItemStack"),"isSameItemSameComponents",current,drop))continue;
                int maximum=Math.min(((Number)call(drop,"getMaxStackSize")).intValue(),((Number)call(bag,"getMaxStackSize")).intValue());
                int add=Math.min(remaining,maximum-n);if(add>0){after.set(i,call(drop,"copyWithCount",n+add));remaining-=add;}
            }if(remaining>0)return false;
        }
        Object originalTool=copy(held),hand=type("net.minecraft.world.entity.EquipmentSlot").getField("MAINHAND").get(null);
        if(!(Boolean)call(level,"destroyBlock",pos(target),false,e,512))return false;
        try{
            for(int i=0;i<after.size();i++)set(bag,i,after.get(i));
            if(!tools.isEmpty())call(held,"hurtAndBreak",1,e,hand);
            return true;
        }catch(Exception ex){
            try{call(level,"setBlock",pos(target),s,3);for(int i=0;i<before.size();i++)set(bag,i,before.get(i));call(e,"setItemSlot",hand,originalTool);}
            catch(Exception rollback){ex.addSuppressed(rollback);}throw ex;
        }
    }
    @Override public boolean plant(UUID id,CodaBlockPos target,String itemId,CodaBlockPos supplies)throws Exception{
        Object e=entity(id);if(e==null||!near(e,target,3))return false;
        var old=block(target);if(old.isEmpty()||!old.get().air()||!storage.isLoaded(new CodaBlockPos(target.x(),target.y()-1,target.z())))return false;
        Object item=value("ITEM",itemId);
        if(!type("net.minecraft.world.item.BlockItem").isInstance(item))return false;
        Object block=call(item,"getBlock");
        if(!type("net.minecraft.world.level.block.CropBlock").isInstance(block)
                &&!type("net.minecraft.world.level.block.SaplingBlock").isInstance(block))return false;
        Object planted=call(block,"defaultBlockState");if(!(Boolean)call(planted,"canSurvive",level,pos(target)))return false;
        Object container=inventory(e);int slot=find(container,Set.of(itemId));
        if(slot<0&&near(e,supplies,3)){container=storage.chest(supplies);if(container!=null)slot=find(container,Set.of(itemId));}
        if(container==null||slot<0)return false;
        Object original=copy(call(container,"getItem",slot)),air=state(target);
        consume(container,slot);
        try{if((Boolean)call(level,"setBlock",pos(target),planted,3))return true;set(container,slot,original);return false;}
        catch(Exception ex){try{call(level,"setBlock",pos(target),air,3);set(container,slot,original);}catch(Exception rollback){ex.addSuppressed(rollback);}throw ex;}
    }
    @Override public void announce(String message)throws Exception{
        if(message==null||message.length()>512)throw new IllegalArgumentException("Invalid settlement notice");
        Object text=call(type("net.minecraft.network.chat.Component"),"literal","[Frontier] "+message);
        for(Object player:(List<?>)call(level,"players"))call(player,"sendSystemMessage",text);
    }
}
