package dev.howlingwhispers.codaloader.bootstrap;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.howlingwhispers.codaloader.api.CodaContext;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.*;
import net.minecraft.advancements.predicates.entity.PlayerPredicate;
import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.loot.functions.*;

/** Actual Mojang pack repository, data registries and written-book components; no renderer or live player. */
public final class GuideBookNativeSmoke {
    static int checks;
    static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static Identifier id(String s){return Identifier.parse(s);}
    static JsonElement json(MultiPackResourceManager resources,String id)throws Exception{
        try(var reader=resources.getResource(id(id)).orElseThrow().openAsReader()){return JsonParser.parseReader(reader);}
    }
    public static void main(String[] args)throws Exception{
        Path game=Files.createTempDirectory("howl-guide-native-");Files.createDirectories(game.resolve("mods"));
        Files.copy(Path.of(args[0]),game.resolve("mods/sorting.jar"));System.setProperty("codaloader.root",game.toString());
        var context=new CodaContext("0.0.41","26.4-snapshot-3",game,game.resolve("config"),"howl_sorting_pipes",List.of("howl_sorting_pipes"));
        for(String name:List.of("extractor_pipe","transport_pipe","sorting_pipe","round_robin","equalizer"))context.registerBlock("howl_sorting_pipes:"+name,0.8f);
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var repository=ServerPacksSource.createVanillaTrustedRepository();repository.reload();repository.setSelected(List.of("vanilla"));
        check(repository.getAvailableIds().contains("howl/mod/howl_sorting_pipes"),"Embedded data discovered by actual server repository hook");
        check(repository.getSelectedIds().contains("vanilla")&&repository.getSelectedIds().contains("howl/mod/howl_sorting_pipes"),"Required mod data selected alongside vanilla");
        repository.setSelected(List.of());check(repository.getSelectedIds().contains("howl/mod/howl_sorting_pipes"),"Required data survives existing-world selection");
        repository.setSelected(List.of("vanilla"));
        try(var resources=new MultiPackResourceManager(PackType.SERVER_DATA,repository.openAllSelected())){
            for(String root:List.of("story/root","husbandry/root","adventure/root","nether/root","end/root"))
                check(resources.getResourceStack(id("minecraft:advancement/"+root+".json")).size()==1,"Vanilla advancement tree preserved: "+root);
            var base=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            // Run Mojang's actual full data loaders so forward references,
            // conditions and registry-backed recipes cannot silently fail.
            var pendingTags=TagLoader.loadTagsForExistingRegistries(resources,base);
            var updated=TagLoader.buildUpdatedLookups(base,pendingTags);
            var world=RegistryDataLoader.load(resources,updated,RegistryDataLoader.WORLD_REGISTRIES,Runnable::run).join();
            var lookups=Stream.concat(updated.stream(),world.listRegistries()).toList();
            var data=RegistryDataLoader.load(resources,lookups,RegistryDataLoader.RELOADABLE_REGISTRIES,Runnable::run).join();
            var all=HolderLookup.Provider.create(Stream.concat(lookups.stream(),data.listRegistries()));
            var ops=all.createSerializationContext(JsonOps.INSTANCE);
            var advancement=data.lookupOrThrow(Registries.ADVANCEMENT).getValue(id("howl_sorting_pipes:guide_unlock"));
            check(advancement!=null,"Guide unlock loads in actual advancement registry");
            check(advancement.display().isEmpty()&&advancement.parent().isEmpty(),"Guide reward does not replace or add a visible root tab");
            check(advancement.rewards().recipes().size()==5,"Five recipes unlocked by book advancement");
            check(advancement.rewards().loot().size()==1,"One guide loot reward");
            check(((PlayerTrigger.TriggerInstance)advancement.criteria().get("iron_ready").triggerInstance()).player().isPresent(),"Native tick criterion retains its player gate");
            String encoded=Advancement.CODEC.encodeStart(ops,advancement).getOrThrow().toString();
            check(encoded.contains("minecraft:type_specific/player")&&encoded.contains("minecraft:story/smelt_iron")&&encoded.contains("minecraft:story/iron_tools"),"Both prerequisite checks survive native criterion decoding");
            check(data.lookupOrThrow(Registries.LOOT_TABLE).getValue(id("howl_sorting_pipes:guide"))!=null,"Guide loot loads natively");
            for(String name:List.of("extractor_pipe","transport_pipe","sorting_pipe","round_robin","equalizer")){
                check(data.lookupOrThrow(Registries.RECIPE).getValue(id("howl_sorting_pipes:"+name))!=null,"Native crafting recipe "+name);
                check(data.lookupOrThrow(Registries.LOOT_TABLE).getValue(id("howl_sorting_pipes:blocks/"+name))!=null,"Native block drop "+name);
            }
            for(String key:List.of("story/root","story/smelt_iron","story/iron_tools","husbandry/root"))check(data.lookupOrThrow(Registries.ADVANCEMENT).getValue(id("minecraft:"+key))!=null,"Vanilla advancement registry intact: "+key);
            JsonObject gate=json(resources,"howl_sorting_pipes:advancement/guide_unlock.json").getAsJsonObject();
            var predicate=PlayerPredicate.CODEC.parse(ops,gate.getAsJsonObject("criteria").getAsJsonObject("iron_ready").getAsJsonObject("conditions").getAsJsonObject("player").getAsJsonObject("predicate").get("minecraft:type_specific/player")).getOrThrow();
            check(predicate.advancements().keySet().equals(Set.of(id("minecraft:story/smelt_iron"),id("minecraft:story/iron_tools"))),"Unlock requires precisely the chosen two advancements");
            Map<Identifier,?> prerequisites=predicate.advancements();
            // Exercise native completion predicates for all four combinations.
            for(int mask=0;mask<4;mask++){
                boolean ready=true;int bit=0;
                for(var entry:prerequisites.entrySet()){
                    var vanilla=data.lookupOrThrow(Registries.ADVANCEMENT).getValue(entry.getKey());
                    var progress=new AdvancementProgress();progress.update(vanilla.requirements());
                    if((mask&(1<<bit++))!=0)for(String criterion:vanilla.criteria().keySet())progress.grantProgress(criterion);
                    var test=entry.getValue().getClass().getDeclaredMethod("test",AdvancementProgress.class);test.setAccessible(true);
                    ready&=(Boolean)test.invoke(entry.getValue(),progress);
                }
                check(ready==(mask==3),"Both prerequisites required, combination "+mask);
            }
            var receipt=new AdvancementProgress();receipt.update(advancement.requirements());
            check(!receipt.isDone()&&receipt.grantProgress("iron_ready")&&receipt.isDone(),"First award completes native reward receipt");
            check(!receipt.grantProgress("iron_ready"),"Native completed criterion does not award again");
            var stored=AdvancementProgress.CODEC.encodeStart(ops,receipt).getOrThrow();
            var restored=AdvancementProgress.CODEC.parse(ops,stored).getOrThrow();restored.update(advancement.requirements());
            check(restored.isDone()&&!restored.grantProgress("iron_ready"),"Native reward receipt survives save/reload");
            JsonObject loot=json(resources,"howl_sorting_pipes:loot_table/guide.json").getAsJsonObject();
            JsonObject modifier=loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonObject("modifier");
            var function=LootItemFunctions.DIRECT_CODEC.parse(ops,modifier).getOrThrow();
            check(function instanceof SetComponentsFunction,"Native book modifier is recognized");
            // Data-pack defaults need a full game to bind; only the one stack
            // limit required by this headless written-book fixture is supplied.
            Items.WRITTEN_BOOK.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,1).build());
            var book=((SetComponentsFunction)function).run(new ItemStack(Items.WRITTEN_BOOK),null);
            var content=book.get(DataComponents.WRITTEN_BOOK_CONTENT);
            check(content!=null&&content.getPages(false).size()>=20,"Actual readable written guide book");
            check(content.title().raw().equals("Sorting Pipes Guide")&&content.author().equals("H.O.W.L."),"Native title and author");
            var splitter=new net.minecraft.client.StringSplitter((code,style)->glyphWidths().getOrDefault(code,6f)+(style.isBold()&&code!=' '?1f:0f));
            int page=0;
            for(var text:content.getPages(false)){
                int lines=splitter.splitLines(text,114,net.minecraft.network.chat.Style.EMPTY).size();
                check(lines*9<=128,"Guide page "+(++page)+" fits vanilla book text area: "+lines+" lines");
            }
            var saved=ItemStack.CODEC.encodeStart(ops,book).getOrThrow();var loaded=ItemStack.CODEC.parse(ops,saved).getOrThrow();
            check(loaded.get(DataComponents.WRITTEN_BOOK_CONTENT).equals(content),"Written guide components persist on stack save/reload");
            check(!Files.exists(game.resolve("saves")),"Auto activation does not edit player world folders");
        }
        System.out.println("PASS: "+checks+" actual Snapshot 3 guide, unlock, data and advancement checks");
    }
    private static Map<Integer,Float> widths;
    private static Map<Integer,Float> glyphWidths(){
        if(widths!=null)return widths;
        try {
            var map=new HashMap<Integer,Float>();map.put((int)' ',4f);
            var loader=GuideBookNativeSmoke.class.getClassLoader();
            try(var in=loader.getResourceAsStream("assets/minecraft/font/include/default.json")){
                var font=JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for(var entry:font.getAsJsonArray("providers")){
                    var provider=entry.getAsJsonObject();if(!provider.get("type").getAsString().equals("bitmap"))continue;
                    String file=provider.get("file").getAsString().replace("minecraft:","assets/minecraft/textures/");
                    java.awt.image.BufferedImage image;
                    try(var data=loader.getResourceAsStream(file)){image=javax.imageio.ImageIO.read(data);}
                    var rows=provider.getAsJsonArray("chars");int cw=image.getWidth()/rows.get(0).getAsString().codePointCount(0,rows.get(0).getAsString().length()),ch=image.getHeight()/rows.size();
                    float scale=(provider.has("height")?provider.get("height").getAsFloat():8f)/ch;
                    for(int y=0;y<rows.size();y++){
                        int[] chars=rows.get(y).getAsString().codePoints().toArray();
                        for(int x=0;x<chars.length;x++){
                            int right=0;
                            for(int gx=0;gx<cw;gx++)for(int gy=0;gy<ch;gy++)if((image.getRGB(x*cw+gx,y*ch+gy)>>>24)!=0)right=Math.max(right,gx+1);
                            if(right>0)map.putIfAbsent(chars[x],(float)Math.round(right*scale)+1);
                        }
                    }
                }
            }
            widths=Map.copyOf(map);return widths;
        }catch(Exception e){throw new IllegalStateException("Could not measure actual Snapshot font",e);}
    }
}
