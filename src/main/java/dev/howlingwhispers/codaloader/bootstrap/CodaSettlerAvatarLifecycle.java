package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.reflect.*;
import java.util.*;

/** Server-only control and native ItemStack persistence for tagged Mojang mannequins. */
public final class CodaSettlerAvatarLifecycle {
    private CodaSettlerAvatarLifecycle() {}
    private static final Map<Object,State> STATES=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class State { Object inventory,path;double speed; }
    static Object call(Object o,String name,Object...args)throws Exception{return CommandReflection.call(o,name,args);}
    static Class<?> type(Object e,String name)throws Exception{return Class.forName(name,true,e.getClass().getClassLoader());}
    static boolean tagged(Object e)throws Exception{
        return ((Set<?>)call(e,"entityTags")).contains(MinecraftLivingWorld.TAG);
    }
    private static boolean server(Object e)throws Exception{
        return !(Boolean)call(call(e,"level"),"isClientSide");
    }
    private static State state(Object e){synchronized(STATES){return STATES.computeIfAbsent(e,k->new State());}}
    static Object inventory(Object e)throws Exception{
        State s=state(e);
        if(s.inventory==null)s.inventory=type(e,"net.minecraft.world.SimpleContainer").getConstructor(int.class).newInstance(8);
        return s.inventory;
    }
    static void path(Object e,Object path,double speed){State s=state(e);s.path=path;s.speed=speed;}
    static void stop(Object e)throws Exception{state(e).path=null;inputs(e,0);}
    private static void inputs(Object e,float forward)throws Exception{
        type(e,"net.minecraft.world.entity.LivingEntity").getField("zza").setFloat(e,forward);
        type(e,"net.minecraft.world.entity.LivingEntity").getField("xxa").setFloat(e,0);
    }
    static void configure(Object e,String name)throws Exception{
        String username=name.split(" ",2)[0];
        if(!username.matches("[A-Za-z0-9_]{3,16}"))username="Steve";
        Class<?> profile=type(e,"net.minecraft.world.item.component.ResolvableProfile");
        Object skin=call(profile,"createUnresolved",username);
        Class<?> mannequin=type(e,"net.minecraft.world.entity.decoration.Mannequin");
        Method setProfile=mannequin.getDeclaredMethod("setProfile",profile);setProfile.setAccessible(true);setProfile.invoke(e,skin);
        Method hide=mannequin.getDeclaredMethod("setHideDescription",boolean.class);hide.setAccessible(true);hide.invoke(e,true);
        call(e,"setCustomName",call(type(e,"net.minecraft.network.chat.Component"),"literal",name));
        call(e,"setCustomNameVisible",true);
    }
    public static void beforeTick(Object e){
        try{
            if(!server(e)||!tagged(e))return;
            State s=state(e);inputs(e,0);
            if(s.path==null||!(Boolean)call(e,"isAlive")||(Boolean)call(e,"isSleeping"))return;
            Object level=call(e,"level"),chunks=call(level,"getChunkSource");
            double x=((Number)call(e,"getX")).doubleValue(),y=((Number)call(e,"getY")).doubleValue(),z=((Number)call(e,"getZ")).doubleValue();
            while(!(Boolean)call(s.path,"isDone")){
                Object node=call(s.path,"getNextEntityPos",e);Class<?> vec=type(e,"net.minecraft.world.phys.Vec3");
                double nx=vec.getField("x").getDouble(node),ny=vec.getField("y").getDouble(node),nz=vec.getField("z").getDouble(node);
                if(Math.hypot(nx-x,nz-z)<.3&&Math.abs(ny-y)<.6){call(s.path,"advance");continue;}
                // Physics and path following must not pull an absent neighbouring chunk into memory.
                for(int cx=(int)Math.floor(Math.min(x,nx)-1)>>4;cx<=(int)Math.floor(Math.max(x,nx)+1)>>4;cx++)
                    for(int cz=(int)Math.floor(Math.min(z,nz)-1)>>4;cz<=(int)Math.floor(Math.max(z,nz)+1)>>4;cz++)
                        if(call(chunks,"getChunkNow",cx,cz)==null){stop(e);call(e,"setDeltaMovement",0.0,0.0,0.0);return;}
                float yaw=(float)Math.toDegrees(Math.atan2(-(nx-x),nz-z));
                call(e,"setYRot",yaw);call(e,"setYHeadRot",yaw);call(e,"setYBodyRot",yaw);
                call(e,"setSpeed",(float)(.3*s.speed));inputs(e,1);
                if(ny>y+.45&&(Boolean)call(e,"onGround"))call(e,"jumpFromGround");
                return;
            }
            stop(e);
        }catch(Exception ex){throw new IllegalStateException("Frontier avatar movement failed",ex);}
    }
    public static void write(Object e,Object output){
        try{
            if(!server(e)||!tagged(e))return;
            Object codec=type(e,"net.minecraft.world.item.ItemStack").getField("CODEC").get(null);
            call(inventory(e),"storeAsItemList",call(output,"list","howl_frontier_inventory",codec));
        }catch(Exception ex){throw new IllegalStateException("Cannot save Frontier inventory",ex);}
    }
    public static void read(Object e,Object input){
        try{
            if(!server(e)||!tagged(e))return;
            Object codec=type(e,"net.minecraft.world.item.ItemStack").getField("CODEC").get(null);
            state(e).inventory=null;
            call(inventory(e),"fromItemList",call(input,"listOrEmpty","howl_frontier_inventory",codec));
            state(e).path=null;
        }catch(Exception ex){throw new IllegalStateException("Cannot load Frontier inventory",ex);}
    }
    public static void drop(Object e,Object level){
        try{
            if(!server(e)||!tagged(e))return;
            Object bag=inventory(e),empty=type(e,"net.minecraft.world.item.ItemStack").getField("EMPTY").get(null);
            for(int i=0;i<8;i++){
                Object item=call(bag,"getItem",i);
                if(!(Boolean)call(item,"isEmpty")&&call(e,"spawnAtLocation",level,call(item,"copy"))!=null)call(bag,"setItem",i,empty);
            }
            for(Object slot:type(e,"net.minecraft.world.entity.EquipmentSlot").getEnumConstants()){
                Object item=call(e,"getItemBySlot",slot);
                if(!(Boolean)call(item,"isEmpty")&&call(e,"spawnAtLocation",level,call(item,"copy"))!=null)call(e,"setItemSlot",slot,empty);
            }
        }catch(Exception ex){throw new IllegalStateException("Cannot drop Frontier supplies",ex);}
    }
}
