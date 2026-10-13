package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.*;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Real Snapshot 3 registries, pipe states, native chests/stacks and production transfer adapter.
 * The test level/chunk shell is not a running Minecraft world or a GUI playtest.
 */
public final class SortingPipesNativeSmoke {
    private static final class SortingTransport {
        static final String EXTRACT="howl_sorting_pipes:extractor_pipe",PIPE="howl_sorting_pipes:transport_pipe",SORT="howl_sorting_pipes:sorting_pipe",ROUND="howl_sorting_pipes:round_robin",EQUAL="howl_sorting_pipes:equalizer";
    }
    private static int checks;
    static void check(boolean b,String what) { checks++; if(!b)throw new AssertionError(what); }
    public static final class Level {
        final ChunkSource source=new ChunkSource();
        public ChunkSource getChunkSource() { return source; }
    }
    public static final class ChunkSource {
        final Chunk chunk=new Chunk();
        public Chunk getChunkNow(int x,int z) { return x==0&&z==0?chunk:null; }
        public Object getChunk(int x,int z) { throw new AssertionError("Chunk load forbidden"); }
    }
    public static final class Chunk {
        final Map<Object,Object> entries=new HashMap<>();
        public Object getBlockEntity(Object pos) { return entries.get(pos); }
    }
    static Object call(Object target,String name,Object... args) throws Exception { return CommandReflection.call(target,name,args); }
    static Object itemStack(Object item,int count) throws Exception {
        Class<?> cls=Class.forName("net.minecraft.world.item.ItemStack"), i=Class.forName("net.minecraft.world.level.ItemLike");
        return cls.getConstructor(i,int.class).newInstance(item,count);
    }
    public static void main(String[] args) throws Exception {
        Path game=Files.createTempDirectory("howl-native-sort-");
        var context=new CodaContext("0.0.38", "26.4-snapshot-3",game,game.resolve("config"),"howl_sorting_pipes",List.of("howl_sorting_pipes"));
        var blocks=List.of(SortingTransport.EXTRACT,SortingTransport.PIPE,SortingTransport.SORT,SortingTransport.ROUND,SortingTransport.EQUAL);
        for (String id:blocks) context.registerBlock(id,0.8f);
        context.registerBlockEntityType("howl_sorting_pipes:pipe_holder",blocks.subList(0,3));
        context.registerCreativeTab("howl_sorting_pipes:pipes","Sorting Pipes",SortingTransport.SORT,blocks);
        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        Object registry=Class.forName("net.minecraft.core.registries.BuiltInRegistries").getField("BLOCK").get(null);
        Class<?> identifier=Class.forName("net.minecraft.resources.Identifier"), posCls=Class.forName("net.minecraft.core.BlockPos"), stateCls=Class.forName("net.minecraft.world.level.block.state.BlockState");
        Object pos=posCls.getConstructor(int.class,int.class,int.class).newInstance(0,64,0);
        for(String id:List.of(SortingTransport.EXTRACT,SortingTransport.PIPE,SortingTransport.SORT,SortingTransport.ROUND,SortingTransport.EQUAL)) {
            Object key=identifier.getMethod("parse",String.class).invoke(null,id),block=call(registry,"getValue",key);
            check(id.equals(call(registry,"getKey",block).toString()),"Real registered block "+id);
            Object state=call(block,"defaultBlockState");
            if(id.endsWith("_pipe")) {
                check(Class.forName("net.minecraft.world.level.block.PipeBlock").isInstance(block),"Six-direction native pipe geometry");
                Object entity=call(block,"newBlockEntity",pos,state);
                check(Class.forName("net.minecraft.world.level.block.entity.BlockEntity").isInstance(entity),"Native entity constructed");
                check(((Collection<?>)call(call(block,"getStateDefinition"),"getPossibleStates")).size()==64,"64 connection states registered");
            }
        }
        // Unsafe is used ONLY to provide an unstarted native integrated-server identity in this headless test.
        Class<?> unsafeClass=Class.forName("sun.misc.Unsafe"); Field field=unsafeClass.getDeclaredField("theUnsafe"); field.setAccessible(true); Object unsafe=field.get(null);
        Object server=unsafeClass.getMethod("allocateInstance",Class.class).invoke(unsafe,Class.forName("net.minecraft.client.server.IntegratedServer"));
        Level level=new Level();
        Object chestBlock=Class.forName("net.minecraft.world.level.block.Blocks").getField("CHEST").get(null);
        Object chestState=call(chestBlock,"defaultBlockState");
        Class<?> chestClass=Class.forName("net.minecraft.world.level.block.entity.ChestBlockEntity");
        Object source=chestClass.getConstructor(posCls,stateCls).newInstance(pos,chestState);
        Object targetPos=posCls.getConstructor(int.class,int.class,int.class).newInstance(1,64,0);
        Object target=chestClass.getConstructor(posCls,stateCls).newInstance(targetPos,chestState);
        level.source.chunk.entries.put(pos,source); level.source.chunk.entries.put(targetPos,target);
        Object iron=Class.forName("net.minecraft.world.item.Items").getField("IRON_INGOT").get(null),gold=Class.forName("net.minecraft.world.item.Items").getField("GOLD_INGOT").get(null);
        // Item defaults are data-pack bound in Snapshot 3; the headless shell
        // supplies only the real MAX_STACK_SIZE component needed by these test items.
        for (Object item:List.of(iron,gold)) {
            Object builder=Class.forName("net.minecraft.core.component.DataComponentMap").getMethod("builder").invoke(null);
            Object max=Class.forName("net.minecraft.core.component.DataComponents").getField("MAX_STACK_SIZE").get(null);
            call(builder,"set",max,64);
            call(call(item,"builtInRegistryHolder"),"bindComponents",call(builder,"build"));
        }
        call(source,"setItem",0,itemStack(gold,11)); Object ironStack=itemStack(iron,9);
        Object component=Class.forName("net.minecraft.core.component.DataComponents").getField("CUSTOM_NAME").get(null);
        Object customName=Class.forName("net.minecraft.network.chat.Component").getMethod("literal",String.class).invoke(null,"Coda's Iron");
        call(ironStack,"set",component,customName); call(source,"setItem",1,ironStack);
        MinecraftSingleplayerWorld world=new MinecraftSingleplayerWorld(server,level);
        var from=new CodaBlockPos(0,64,0); var to=new CodaBlockPos(1,64,0);
        check(world.itemInventory(from).orElseThrow().count("minecraft:iron_ingot")==9,"Real stack registry ID inspection");
        check(world.transferFiltered(from,to,3,CodaItemFilter.only("minecraft:iron_ingot"))==3,"Real native whitelist transfer");
        check(((Number)call(call(source,"getItem",0),"getCount")).intValue()==11,"Nonmatching source preserved");
        check(call(call(target,"getItem",0),"get",component).equals(customName),"Actual custom-name component retained");
        check(world.transferFiltered(from,to,4,new CodaItemFilter(CodaItemFilter.Mode.BLACKLIST,Set.of("minecraft:iron_ingot")))==4,"Real native blacklist transfer");
        check(world.transferFiltered(from,to,1,new CodaItemFilter(CodaItemFilter.Mode.WHITELIST,Set.of()))==0,"Empty native whitelist rejects all");
        check(!world.isLoaded(new CodaBlockPos(32,64,0)),"Unloaded native shell stays unloaded");
        checkNativePageControls(unsafe,unsafeClass,registry,identifier);
        System.out.println("PASS: "+checks+" exact Mojang Snapshot 3 native sorting-pipes checks");
    }
    private static void checkNativePageControls(Object unsafe,Class<?> unsafeClass,Object blocks,Class<?> identifier)throws Exception {
        // Construct a real Minecraft Screen with a fixture selection handler.
        // Native widgets run headlessly; this is not a rendered world/client.
        String name="dev/howlingwhispers/codaloader/bootstrap/NativePageFixtureScreen";
        String screen="net/minecraft/client/gui/screens/Screen",tab="net/minecraft/world/item/CreativeModeTab";
        var w=new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
        w.visit(org.objectweb.asm.Opcodes.V25,org.objectweb.asm.Opcodes.ACC_PUBLIC,name,null,screen,null);
        w.visitField(org.objectweb.asm.Opcodes.ACC_PUBLIC|org.objectweb.asm.Opcodes.ACC_STATIC,"selectedTab","L"+tab+";",null,null).visitEnd();
        for(String field:List.of("leftPos","topPos","imageWidth"))w.visitField(org.objectweb.asm.Opcodes.ACC_PUBLIC,field,"I",null,null).visitEnd();
        var init=w.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();
        init.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD,0);init.visitInsn(org.objectweb.asm.Opcodes.ACONST_NULL);init.visitInsn(org.objectweb.asm.Opcodes.ACONST_NULL);
        init.visitLdcInsn("Native page fixture");init.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC,"net/minecraft/network/chat/Component","literal","(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;",true);
        init.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL,screen,"<init>","(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;)V",false);
        for(var entry:Map.of("leftPos",20,"topPos",80,"imageWidth",195).entrySet()){
            init.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD,0);init.visitLdcInsn(entry.getValue());init.visitFieldInsn(org.objectweb.asm.Opcodes.PUTFIELD,name,entry.getKey(),"I");
        }
        init.visitInsn(org.objectweb.asm.Opcodes.RETURN);init.visitMaxs(0,0);init.visitEnd();
        var select=w.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC,"selectTab","(L"+tab+";)V",null,null);select.visitCode();
        select.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD,1);select.visitFieldInsn(org.objectweb.asm.Opcodes.PUTSTATIC,name,"selectedTab","L"+tab+";");
        select.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD,1);select.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC,"dev/howlingwhispers/codaloader/bootstrap/CodaCreativeTabPages","selected","(Ljava/lang/Object;)V",false);
        select.visitInsn(org.objectweb.asm.Opcodes.RETURN);select.visitMaxs(0,0);select.visitEnd();w.visitEnd();
        class FixtureLoader extends ClassLoader {Class<?> define(byte[] bytes){return defineClass(name.replace('/','.'),bytes,0,bytes.length);}}
        Class<?> fixture=new FixtureLoader().define(w.toByteArray());Object instance=fixture.getConstructor().newInstance();
        Object defaultTab=Class.forName("net.minecraft.world.item.CreativeModeTabs").getMethod("getDefaultTab").invoke(null);
        fixture.getField("selectedTab").set(null,defaultTab);
        Object creativeRegistry=Class.forName("net.minecraft.core.registries.BuiltInRegistries").getField("CREATIVE_MODE_TAB").get(null);
        Object id=identifier.getMethod("parse",String.class).invoke(null,"howl_sorting_pipes:pipes");Object modTab=call(creativeRegistry,"getValue",id);
        Class<?> tabClass=Class.forName("net.minecraft.world.item.CreativeModeTab");
        Field contents=tabClass.getDeclaredField("displayItems");contents.setAccessible(true);
        contents.set(modTab,List.of(Class.forName("net.minecraft.world.item.ItemStack").getField("EMPTY").get(null)));
        CodaCreativeTabPages.initialize(instance);
        Field children=Class.forName("net.minecraft.client.gui.screens.Screen").getDeclaredField("children");children.setAccessible(true);
        List<?> controls=(List<?>)children.get(instance);check(controls.size()==3,"Real native page controls added to screen");
        Object previous=controls.get(0),next=controls.get(1),label=controls.get(2);
        check(((Number)call(previous,"getX")).intValue()==26&&((Number)call(next,"getX")).intValue()==193,"Native buttons stay inside horizontal panel extent");
        check(((Number)call(next,"getY")).intValue()==31,"Native buttons sit above existing tab row");
        check(call(call(label,"getMessage"),"getString").equals("1/2"),"Native page count starts at vanilla page");
        Field callback=Class.forName("net.minecraft.client.gui.components.Button").getDeclaredField("onPress");callback.setAccessible(true);
        Class<?> press=Class.forName("net.minecraft.client.gui.components.Button$OnPress"),button=Class.forName("net.minecraft.client.gui.components.Button");
        press.getMethod("onPress",button).invoke(callback.get(next),next);
        check(fixture.getField("selectedTab").get(null)==modTab,"Next button selects real mod tab");
        check(CodaCreativeTabPages.visualColumn(modTab)==0&&CodaCreativeTabPages.visualRow(modTab).toString().equals("TOP"),"Real sorting tab maps into first in-frame mod slot");
        check(call(call(label,"getMessage"),"getString").equals("2/2"),"Native page count updates after navigation");
        press.getMethod("onPress",button).invoke(callback.get(previous),previous);
        check(fixture.getField("selectedTab").get(null)==defaultTab,"Previous button restores vanilla page");
        CodaCreativeTabPages.removed(instance);
    }

}
