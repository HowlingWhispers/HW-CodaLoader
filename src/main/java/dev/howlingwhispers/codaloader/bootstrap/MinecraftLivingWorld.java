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
        if(!((Set<?>)call(e,"entityTags")).contains(TAG))throw new IllegalArgumentException("Entity is not a H.O.W.L. settler");
        if(type("net.minecraft.world.entity.npc.villager.Villager").isInstance(e)){
            if((Boolean)call(e,"isAlive"))e=replace(e,id,call(call(e,"getCustomName"),"getString").toString());
        }else if(!type("net.minecraft.world.entity.decoration.Mannequin").isInstance(e))
            throw new IllegalArgumentException("Entity is not a H.O.W.L. settler avatar");
        return e;
    }
    @Override public Optional<Actor> actor(UUID id) throws Exception {
        Object e=entity(id); if(e==null)return Optional.empty();
        // Mojang avatar physics, equipment and skin synchronization remain authoritative.
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
        Object e=newAvatar();
        call(e,"setUUID",id);call(e,"addTag",TAG);
        call(e,"setPos",feet.x()+.5,(double)feet.y(),feet.z()+.5);
        if(!(Boolean)call(level,"noCollision",e))return false;
        CodaSettlerAvatarLifecycle.configure(e,name);
        return (Boolean)call(level,"addFreshEntity",e);
    }
    private Object newAvatar()throws Exception{
        return type("net.minecraft.world.entity.decoration.Mannequin")
                .getConstructor(type("net.minecraft.world.entity.EntityType"),type("net.minecraft.world.level.Level"))
                .newInstance(value("ENTITY_TYPE","minecraft:mannequin"),level);
    }
    /** Copy native save data before replacing a loaded legacy actor, retaining its identity and real gear. */
    private Object replace(Object old,UUID id,String name)throws Exception{
        Object reporter=type("net.minecraft.util.ProblemReporter").getField("DISCARDING").get(null);
        Object registries=call(level,"registryAccess");
        Object output=call(type("net.minecraft.world.level.storage.TagValueOutput"),"createWithContext",reporter,registries);
        call(old,"saveWithoutId",output);
        Object input=call(type("net.minecraft.world.level.storage.TagValueInput"),"create",reporter,registries,call(output,"buildResult"));
        Object avatar=newAvatar();call(avatar,"load",input);call(avatar,"setUUID",id);
        CodaSettlerAvatarLifecycle.configure(avatar,name);
        if(type("net.minecraft.world.entity.npc.villager.Villager").isInstance(old)){
            Object from=call(old,"getInventory"),to=CodaSettlerAvatarLifecycle.inventory(avatar);
            for(int i=0;i<size(from);i++)call(to,"setItem",i,copy(call(from,"getItem",i)));
        }
        call(old,"discard");
        if(!(Boolean)call(level,"addFreshEntity",avatar)){
            var undo=type("net.minecraft.world.entity.Entity").getDeclaredMethod("unsetRemoved");undo.setAccessible(true);undo.invoke(old);
            if(!(Boolean)call(level,"addFreshEntity",old))throw new IllegalStateException("Cannot restore legacy settler after refused avatar migration");
            throw new IllegalStateException("Minecraft refused settler avatar migration; original entity restored");
        }
        return avatar;
    }
    @Override public boolean rename(UUID id,String name)throws Exception{
        if(name==null||name.isBlank()||name.length()>64)throw new IllegalArgumentException("Invalid settler name");
        Object e=entity(id);if(e==null||!(Boolean)call(e,"isAlive"))return false;
        CodaSettlerAvatarLifecycle.configure(e,name);return true;
    }
    @Override public boolean recover(UUID id,String name,CodaBlockPos playerPosition)throws Exception{
        requireActive.run();Objects.requireNonNull(id);
        if(name==null||name.isBlank()||name.length()>64)throw new IllegalArgumentException("Invalid settler name");
        if(!storage.isLoaded(playerPosition)||call(level,"getEntityInAnyDimension",id)!=null)return false;
        Object box=type("net.minecraft.world.phys.AABB").getConstructor(double.class,double.class,double.class,
                double.class,double.class,double.class).newInstance(playerPosition.x()-8.0,playerPosition.y()-8.0,playerPosition.z()-8.0,
                        playerPosition.x()+9.0,playerPosition.y()+9.0,playerPosition.z()+9.0);
        List<Object> matches=new ArrayList<>();
        for(Object e:(List<?>)call(level,"getEntitiesOfClass",type("net.minecraft.world.entity.monster.zombie.ZombieVillager"),box)){
            if(!((Set<?>)call(e,"entityTags")).contains(TAG)||!near(e,playerPosition,8))continue;
            Object label=call(e,"getCustomName");
            if(label!=null&&name.equals(call(label,"getString"))&&(Boolean)call(e,"isAlive"))matches.add(e);
        }
        if(matches.size()!=1)return false;
        Object recovered=replace(matches.getFirst(),id,name);
        return call(level,"getEntity",id)==recovered;
    }
    /** Preflight the whole native pathfinding region; do not generate neighbouring chunks. */
    private boolean navigationLoaded(Object e)throws Exception{
        int x=(int)Math.floor(number(e,"getX")),z=(int)Math.floor(number(e,"getZ"));
        Object follow=type("net.minecraft.world.entity.ai.attributes.Attributes").getField("FOLLOW_RANGE").get(null);
        double range=((Number)call(e,"getAttributeValue",follow)).doubleValue();
        if(!Double.isFinite(range)||range<0||range>128)return false;
        int radius=(int)Math.ceil(Math.max(32,range))+8; // exact PathNavigationRegion padding
        for(int cx=(x-radius)>>4;cx<=(x+radius)>>4;cx++)for(int cz=(z-radius)>>4;cz<=(z+radius)>>4;cz++)
            if(!storage.isLoaded(new CodaBlockPos(cx<<4,64,cz<<4)))return false;
        return true;
    }
    @Override public boolean walk(UUID id,CodaBlockPos target,double speed)throws Exception{
        if(!Double.isFinite(speed)||speed<.1||speed>1.2)throw new IllegalArgumentException("Invalid walking speed");
        Object e=entity(id);if(e==null||!near(e,target,32)||!storage.isLoaded(target))return false;
        Object planner=type("net.minecraft.world.entity.npc.villager.Villager")
                .getConstructor(type("net.minecraft.world.entity.EntityType"),type("net.minecraft.world.level.Level"))
                .newInstance(value("ENTITY_TYPE","minecraft:villager"),level);
        call(planner,"setPos",number(e,"getX"),number(e,"getY"),number(e,"getZ"));
        call(planner,"setOnGround",call(e,"onGround"));
        if(!navigationLoaded(planner))return false;
        Object nav=call(planner,"getNavigation");call(nav,"setRequiredPathLength",32f);
        Object path=call(nav,"createPath",pos(target),1);
        if(path==null||!(Boolean)call(path,"canReach"))return false;
        CodaSettlerAvatarLifecycle.path(e,path,speed);return true;
    }
    @Override public boolean sleep(UUID id,CodaBlockPos bed)throws Exception{
        Object e=entity(id);if(e==null||!near(e,bed,2.5))return false;
        var b=block(bed);if(b.isEmpty()||!b.get().id().endsWith("_bed")||!"head".equals(b.get().properties().get("part")))return false;
        if((Boolean)call(e,"isSleeping"))return true;
        if("true".equals(b.get().properties().get("occupied")))return false;
        CodaSettlerAvatarLifecycle.stop(e);
        return (Boolean)call(e,"startSleeping",pos(bed));
    }
    @Override public void wake(UUID id)throws Exception{Object e=entity(id);if(e!=null&&(Boolean)call(e,"isSleeping"))call(e,"stopSleeping");}
    @Override public void damage(UUID id,float amount)throws Exception{
        if(!Float.isFinite(amount)||amount<=0||amount>20)throw new IllegalArgumentException("Invalid damage");
        Object e=entity(id);if(e!=null)call(e,"hurtServer",level,call(call(e,"damageSources"),"starve"),amount);
    }
    @Override public void discard(UUID id)throws Exception{Object e=entity(id);if(e!=null)call(e,"discard");}
    private Object inventory(Object e)throws Exception{return CodaSettlerAvatarLifecycle.inventory(e);}
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
            Object equipment=type("net.minecraft.world.SimpleContainer").getConstructor(int.class).newInstance(1);
            call(equipment,"setItem",0,copy(held));
            if(MinecraftSingleplayerWorld.moveContainers(equipment,destination,1,CodaItemFilter.all())!=1)return null;
            call(e,"setItemSlot",hand,call(equipment,"getItem",0));
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
    /**
     * L8 (0.0.47): craft like a player at a crafting table (or in the 2x2 grid without one).
     * Uses the game's own recipe, ingredients and results; each craft is all-or-nothing.
     */
    @Override public int craft(UUID id,CodaBlockPos workbench,CodaBlockPos supplies,String recipeId,int times)throws Exception{
        if(times<1||times>64)throw new IllegalArgumentException("Craft 1 to 64 times");
        Object recipe=craftingRecipe(Objects.requireNonNull(recipeId));
        Object e=entity(id);if(e==null)return 0;
        boolean shaped=type("net.minecraft.world.item.crafting.ShapedRecipe").isInstance(recipe);
        List<Optional<?>> cells=new ArrayList<>();int w,h;
        if(shaped){
            w=((Number)call(recipe,"getWidth")).intValue();h=((Number)call(recipe,"getHeight")).intValue();
            for(Object cell:(List<?>)call(recipe,"getIngredients"))cells.add((Optional<?>)cell);
        }else{
            List<?> ingredients=(List<?>)call(call(recipe,"placementInfo"),"ingredients");
            int n=ingredients.size();if(n<1||n>9)return 0;
            w=n<=4?2:3;h=n<=4?2:3;
            for(Object ingredient:ingredients)cells.add(Optional.of(ingredient));
            while(cells.size()<w*h)cells.add(Optional.empty());
        }
        if(workbench==null){
            if(w>2||h>2)return 0;
        }else{
            Object table=state(workbench);
            if(table==null||!"minecraft:crafting_table".equals(key("BLOCK",call(table,"getBlock"))))return 0;
            if(!near(e,workbench,3))return 0;
            if(Math.pow(workbench.x()-supplies.x(),2)+Math.pow(workbench.y()-supplies.y(),2)+Math.pow(workbench.z()-supplies.z(),2)>9)return 0;
        }
        Object bag=inventory(e);
        Object chest=supplies!=null&&near(e,supplies,3)?storage.chest(supplies):null;
        int done=0;
        for(int t=0;t<times;t++){
            List<Object> bagBefore=snapshot(bag),chestBefore=chest==null?List.of():snapshot(chest);
            boolean ok=false;
            try{ok=craftOnce(recipe,cells,w,h,bag,chest);}
            finally{if(!ok){restore(bag,bagBefore);if(chest!=null)restore(chest,chestBefore);}}
            if(!ok)break;
            done++;
        }
        return done;
    }
    private Object craftingRecipe(String recipeId)throws Exception{
        Object key=call(type("net.minecraft.resources.ResourceKey"),"create",
                type("net.minecraft.core.registries.Registries").getField("RECIPE").get(null),
                call(type("net.minecraft.resources.Identifier"),"parse",recipeId));
        Optional<?> holder=(Optional<?>)call(call(server,"getRecipeManager"),"byKey",key);
        if(holder.isEmpty())throw new IllegalArgumentException("Unknown recipe: "+recipeId);
        Object recipe=call(holder.get(),"value");
        // Only ordinary crafting-table recipes: no special recipes (dyeing, repairs, maps...).
        if(!type("net.minecraft.world.item.crafting.ShapedRecipe").isInstance(recipe)
                &&!type("net.minecraft.world.item.crafting.ShapelessRecipe").isInstance(recipe))
            throw new IllegalArgumentException("Not a crafting-table recipe: "+recipeId);
        return recipe;
    }
    /** One craft: take one matching item per cell (own inventory first), check with the game, add the results. */
    private boolean craftOnce(Object recipe,List<Optional<?>> cells,int w,int h,Object bag,Object chest)throws Exception{
        List<Object> grid=new ArrayList<>();
        for(Optional<?> cell:cells){
            if(cell.isEmpty()){grid.add(empty());continue;}
            Object ingredient=cell.get(),taken=null;
            for(Object container:chest==null?List.of(bag):List.of(bag,chest)){
                for(int i=0;i<size(container)&&taken==null;i++){
                    Object stack=call(container,"getItem",i);
                    if(count(stack)>0&&(Boolean)call(ingredient,"test",stack)){taken=call(stack,"copyWithCount",1);consume(container,i);}
                }
                if(taken!=null)break;
            }
            if(taken==null)return false;
            grid.add(taken);
        }
        Object input=call(type("net.minecraft.world.item.crafting.CraftingInput"),"of",w,h,grid);
        if(!(Boolean)call(recipe,"matches",input,level))return false;
        List<Object> out=new ArrayList<>();
        Object result=call(recipe,"assemble",input);
        if(count(result)<1)return false;
        out.add(result);
        for(Object rest:(Iterable<?>)call(recipe,"getRemainingItems",input))if(count(rest)>0)out.add(rest);
        return addAll(bag,out);
    }
    private List<Object> snapshot(Object container)throws Exception{
        List<Object> copy=new ArrayList<>();for(int i=0;i<size(container);i++)copy.add(copy(call(container,"getItem",i)));return copy;
    }
    private void restore(Object container,List<Object> copy)throws Exception{
        for(int i=0;i<copy.size();i++)set(container,i,copy.get(i));
    }
    /** Put stacks into an inventory like a pickup would, or change nothing and return false. */
    private boolean addAll(Object container,List<Object> stacks)throws Exception{
        List<Object> after=snapshot(container);
        Object stackType=type("net.minecraft.world.item.ItemStack");
        for(Object stack:stacks){
            int remaining=count(stack);
            for(int pass=0;pass<2&&remaining>0;pass++)
                for(int i=0;i<after.size()&&remaining>0;i++){
                    Object current=after.get(i);int n=count(current);
                    if(pass==0&&(n==0||!(Boolean)call(stackType,"isSameItemSameComponents",current,stack)))continue;
                    if(pass==1&&n!=0)continue;
                    int maximum=Math.min(((Number)call(stack,"getMaxStackSize")).intValue(),((Number)call(container,"getMaxStackSize")).intValue());
                    int add=Math.min(remaining,maximum-n);
                    if(add>0){after.set(i,call(stack,"copyWithCount",n+add));remaining-=add;}
                }
            if(remaining>0)return false;
        }
        restore(container,after);
        return true;
    }
    private static final java.util.regex.Pattern STATE=java.util.regex.Pattern.compile(
            "([a-z0-9_.-]+:[a-z0-9_./-]+)(?:\\[([a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*)\\])?");
    /** A block state from "namespace:id[name=value,...]"; unnamed properties keep their defaults. */
    private Object blockState(String text)throws Exception{
        var m=STATE.matcher(Objects.requireNonNull(text));
        if(!m.matches())throw new IllegalArgumentException("Invalid block state: "+text);
        Object state=call(value("BLOCK",m.group(1)),"defaultBlockState");
        if(m.group(2)==null)return state;
        for(String pair:m.group(2).split(",")){
            String name=pair.substring(0,pair.indexOf('=')),wanted=pair.substring(pair.indexOf('=')+1);
            Object property=null;
            for(Object candidate:(Collection<?>)call(state,"getProperties"))if(call(candidate,"getName").equals(name))property=candidate;
            if(property==null)throw new IllegalArgumentException(m.group(1)+" has no property "+name);
            Optional<?> parsed=(Optional<?>)call(property,"getValue",wanted);
            if(parsed.isEmpty())throw new IllegalArgumentException(m.group(1)+" property "+name+" cannot be "+wanted);
            state=call(state,"setValue",property,parsed.get());
        }
        return state;
    }
    /** Air, or something a placed block may replace (grass, flowers); never a fluid or an unloaded block. */
    private boolean replaceable(CodaBlockPos p)throws Exception{
        Object s=state(p);if(s==null)return false;
        if(!(Boolean)call(call(s,"getFluidState"),"isEmpty"))return false;
        return (Boolean)call(s,"isAir")||(Boolean)call(s,"canBeReplaced");
    }
    private boolean inside(CodaBlockPos p)throws Exception{
        Object at=pos(p);
        return (Boolean)call(level,"isInWorldBounds",at)&&(Boolean)call(call(level,"getWorldBorder"),"isWithinBounds",at);
    }
    private boolean clear(Object state,CodaBlockPos p)throws Exception{
        return (Boolean)call(level,"isUnobstructed",state,pos(p),call(type("net.minecraft.world.phys.shapes.CollisionContext"),"empty"));
    }
    /** The second half of a door or bed placed from {@code first}, or null for single blocks. Second halves are refused. */
    private Object[] secondHalf(Object first,CodaBlockPos at)throws Exception{
        Object block=call(first,"getBlock");
        if(type("net.minecraft.world.level.block.DoorBlock").isInstance(block)){
            Object half=type("net.minecraft.world.level.block.DoorBlock").getField("HALF").get(null);
            Object upper=type("net.minecraft.world.level.block.state.properties.DoubleBlockHalf").getField("UPPER").get(null);
            if(call(first,"getValue",half)==upper)throw new IllegalArgumentException("Place a door by its lower half");
            return new Object[]{new CodaBlockPos(at.x(),at.y()+1,at.z()),call(first,"setValue",half,upper)};
        }
        // Snapshot 3 beds share AbstractBedBlock, which owns PART, FACING and the head-placing setPlacedBy.
        if(type("net.minecraft.world.level.block.AbstractBedBlock").isInstance(block)){
            Class<?> bed=type("net.minecraft.world.level.block.AbstractBedBlock");
            Object part=bed.getField("PART").get(null),head=type("net.minecraft.world.level.block.state.properties.BedPart").getField("HEAD").get(null);
            if(call(first,"getValue",part)==head)throw new IllegalArgumentException("Place a bed by its foot");
            Object facing=call(first,"getValue",bed.getField("FACING").get(null));
            int dx=((Number)call(facing,"getStepX")).intValue(),dz=((Number)call(facing,"getStepZ")).intValue();
            return new Object[]{new CodaBlockPos(at.x()+dx,at.y(),at.z()+dz),call(first,"setValue",part,head)};
        }
        return null;
    }
    @Override public boolean place(UUID id,CodaBlockPos target,String blockState,CodaBlockPos supplies)throws Exception{
        requireActive.run();Objects.requireNonNull(target);
        Object placed=blockState(blockState),block=call(placed,"getBlock");
        String itemId=key("ITEM",call(block,"asItem"));
        if(itemId.equals("minecraft:air"))throw new IllegalArgumentException("No item places "+blockState);
        Object[] second=secondHalf(placed,target);
        Object e=entity(id);if(e==null||!(Boolean)call(e,"isAlive")||!near(e,target,6))return false;
        if(!inside(target)||!replaceable(target)||!(Boolean)call(placed,"canSurvive",level,pos(target))||!clear(placed,target))return false;
        CodaBlockPos other=second==null?null:(CodaBlockPos)second[0];
        if(other!=null&&(!inside(other)||!replaceable(other)||!clear(second[1],other)))return false;
        // One real item: the settler's own inventory first, then the nearby stockpile.
        Object container=inventory(e);int slot=find(container,Set.of(itemId));
        if(slot<0&&supplies!=null&&near(e,supplies,3)){container=storage.chest(supplies);if(container!=null)slot=find(container,Set.of(itemId));}
        if(container==null||slot<0)return false;
        Object original=copy(call(container,"getItem",slot)),used=call(original,"copyWithCount",1);
        Object before=state(target),beforeOther=other==null?null:state(other);
        consume(container,slot);
        try{
            if(!(Boolean)call(level,"setBlock",pos(target),placed,3)){set(container,slot,original);return false;}
            if(other!=null){
                call(block,"setPlacedBy",level,pos(target),placed,e,used); // vanilla adds the door's upper half or the bed's head
                if(state(other)==null||call(state(other),"getBlock")!=block)throw new IllegalStateException("Minecraft did not complete the second half");
            }
            return true;
        }catch(Exception ex){
            try{
                call(level,"setBlock",pos(target),before,3);
                if(other!=null)call(level,"setBlock",pos(other),beforeOther,3);
                set(container,slot,original);
            }catch(Exception rollback){ex.addSuppressed(rollback);}
            throw ex;
        }
    }
    private static final Set<String> PATHABLE=Set.of("minecraft:grass_block","minecraft:dirt","minecraft:coarse_dirt",
            "minecraft:podzol","minecraft:mycelium","minecraft:rooted_dirt");
    @Override public boolean shapePath(UUID id,CodaBlockPos target)throws Exception{
        requireActive.run();Objects.requireNonNull(target);
        Object e=entity(id);if(e==null||!(Boolean)call(e,"isAlive")||!near(e,target,6))return false;
        Object held=call(e,"getMainHandItem");
        // Shovels by registry ID: Snapshot item classes change, the vanilla IDs do not.
        if(count(held)==0||!MinecraftSingleplayerWorld.itemId(held).endsWith("_shovel"))return false;
        Object s=state(target);var above=block(new CodaBlockPos(target.x(),target.y()+1,target.z()));
        if(s==null||above.isEmpty()||!above.get().air()||!PATHABLE.contains(key("BLOCK",call(s,"getBlock"))))return false;
        Object path=call(value("BLOCK","minecraft:dirt_path"),"defaultBlockState");
        Object hand=type("net.minecraft.world.entity.EquipmentSlot").getField("MAINHAND").get(null),originalTool=copy(held);
        if(!(Boolean)call(level,"setBlock",pos(target),path,11))return false;
        try{call(held,"hurtAndBreak",1,e,hand);return true;}
        catch(Exception ex){
            try{call(level,"setBlock",pos(target),s,11);call(e,"setItemSlot",hand,originalTool);}catch(Exception rollback){ex.addSuppressed(rollback);}
            throw ex;
        }
    }
    @Override public void announce(String message)throws Exception{
        if(message==null||message.length()>512)throw new IllegalArgumentException("Invalid settlement notice");
        Object text=call(type("net.minecraft.network.chat.Component"),"literal","[Frontier] "+message);
        for(Object player:(List<?>)call(level,"players"))call(player,"sendSystemMessage",text);
    }
}
