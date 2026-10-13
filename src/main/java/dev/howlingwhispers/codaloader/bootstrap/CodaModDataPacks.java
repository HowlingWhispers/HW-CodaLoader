package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.CodaTarget;
import dev.howlingwhispers.codaloader.core.MiniJson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Mod-owned server data, discovered through vanilla's pack repository before world loading. */
public final class CodaModDataPacks {
    private static final int MAX_PACK=8*1024*1024, MAX_ENTRY=1024*1024;
    private CodaModDataPacks() {}
    record Declared(String id,Path file) {}

    static List<Declared> prepare(Path game)throws IOException {
        Path mods=game.resolve("mods"); if(!Files.isDirectory(mods))return List.of();
        List<Path> jars;
        try(var files=Files.list(mods)){jars=files.filter(p->Files.isRegularFile(p)&&p.toString().endsWith(".jar")).sorted().toList();}
        var declared=new ArrayList<Declared>();var ids=new HashSet<String>();
        for(Path jar:jars)try(ZipFile zip=new ZipFile(jar.toFile())) {
            ZipEntry manifest=zip.getEntry("coda.mod.json");if(manifest==null)continue;
            byte[] meta;try(var in=zip.getInputStream(manifest)){meta=in.readNBytes(8193);}
            if(meta.length>8192)throw new IOException("Oversized mod metadata");
            Object decoded=MiniJson.parse(new String(meta,StandardCharsets.UTF_8));
            if(!(decoded instanceof Map<?,?> m)||!m.containsKey("dataPack"))continue;
            String id=Objects.toString(m.get("id"),""),name=Objects.toString(m.get("dataPack"),"");
            if(!id.matches("[a-z0-9_.-]{1,64}")||!name.matches("[a-zA-Z0-9_.-]+\\.zip")
                    ||!CodaTarget.MINECRAFT_VERSION.equals(m.get("minecraft"))||!ids.add(id))
                throw new IOException("Invalid or duplicate mod data-pack declaration");
            ZipEntry entry=zip.getEntry(name);if(entry==null)throw new IOException("Declared data pack missing: "+id);
            byte[] bytes;try(var in=zip.getInputStream(entry)){bytes=in.readNBytes(MAX_PACK+1);}
            if(bytes.length>MAX_PACK)throw new IOException("Oversized mod data pack");
            validate(id,bytes);
            Path cache=game.resolve(".howl/mod-datapacks");
            if(Files.isSymbolicLink(game.resolve(".howl"))||Files.isSymbolicLink(cache))throw new IOException("Linked mod data cache");
            Files.createDirectories(cache);Path file=cache.resolve(id+".zip");
            if(Files.isSymbolicLink(file))throw new IOException("Linked mod data pack");
            if(!Files.isRegularFile(file)||!Arrays.equals(bytes,Files.readAllBytes(file))){
                Path stage=Files.createTempFile(cache,".mod-data-",".tmp");
                try{Files.write(stage,bytes);Files.move(stage,file,StandardCopyOption.REPLACE_EXISTING);}
                finally{Files.deleteIfExists(stage);}
            }
            declared.add(new Declared(id,file));
        }catch(IllegalArgumentException e){throw new IOException("Invalid mod data in "+jar.getFileName(),e);}
        return List.copyOf(declared);
    }

    private static void validate(String id,byte[] bytes)throws IOException {
        var names=new HashSet<String>();long total=0;boolean metadata=false,data=false;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            for(ZipEntry e;(e=zip.getNextEntry())!=null;){
                String name=e.getName();
                if(names.size()>=512||!names.add(name)||name.contains("..")||name.contains("\\")||name.startsWith("/"))throw new IOException("Unsafe mod data-pack layout");
                if(e.isDirectory())continue;
                boolean tag=name.equals("data/minecraft/tags/function/load.json")||name.equals("data/minecraft/tags/function/tick.json");
                if(!name.equals("pack.mcmeta")&&!name.startsWith("data/"+id+"/")&&!tag)throw new IOException("Mod data must belong to "+id+"; vanilla overrides are not allowed");
                byte[] content=zip.readNBytes(MAX_ENTRY+1);total+=content.length;
                if(content.length>MAX_ENTRY||total>16*1024*1024)throw new IOException("Mod data expansion too large");
                if(name.equals("pack.mcmeta")){
                    Object parsed=MiniJson.parse(new String(content,StandardCharsets.UTF_8));
                    if(!(parsed instanceof Map<?,?> root)||!(root.get("pack") instanceof Map<?,?> pack)
                            ||!format(pack.get("min_format"))||!format(pack.get("max_format")))throw new IOException("Wrong Snapshot data-pack format");
                    // No filtering or overlays that could hide vanilla advancements/recipes.
                    if(root.containsKey("filter")||root.containsKey("overlays"))throw new IOException("Mod data filters and overlays are not supported");
                    metadata=true;
                }else if(tag){
                    Object parsed=MiniJson.parse(new String(content,StandardCharsets.UTF_8));
                    if(!(parsed instanceof Map<?,?> t)||Boolean.TRUE.equals(t.get("replace"))||!(t.get("values") instanceof List<?> values)
                            ||values.stream().anyMatch(v->!(v instanceof String s)||!s.startsWith(id+":")))throw new IOException("Mod function tags must append their own functions");
                }else data=true;
            }
        }
        if(!metadata||!data)throw new IOException("Missing mod data-pack metadata or data");
    }
    private static boolean format(Object v){
        return v instanceof Number n&&n.intValue()==CodaTarget.MINECRAFT_DATA_PACK_FORMAT;
    }

    /** Called at the end of BuiltInPackSource.loadPacks; client repositories are untouched. */
    public static void discover(Object source,Consumer<Object> output) {
        if(!source.getClass().getName().equals("net.minecraft.server.packs.repository.ServerPacksSource"))return;
        try {
            Path game=Path.of(System.getProperty("codaloader.root","run"));
            for(Declared spec:prepare(game))output.accept(nativePack(spec));
        }catch(Exception e){throw new IllegalStateException("H.O.W.L. mod data-pack discovery failed",e);}
    }
    private static Class<?> type(String name)throws ClassNotFoundException{return Class.forName("net.minecraft."+name);}
    private static Object nativePack(Declared spec)throws ReflectiveOperationException {
        Class<?> component=type("network.chat.Component"),source=type("server.packs.repository.PackSource"),location=type("server.packs.PackLocationInfo");
        Object title=component.getMethod("literal",String.class).invoke(null,"H.O.W.L. "+spec.id());
        Object info=location.getConstructor(String.class,component,source,Optional.class).newInstance("howl/mod/"+spec.id(),title,source.getField("BUILT_IN").get(null),Optional.empty());
        Object supplier=type("server.packs.FilePackResources$FileResourcesSupplier").getConstructor(Path.class).newInstance(spec.file());
        Class<?> position=type("server.packs.repository.Pack$Position"),selection=type("server.packs.PackSelectionConfig"),packType=type("server.packs.PackType");
        // Required keeps mod recipes present in existing worlds too. BOTTOM
        // leaves world packs above it so player customizations retain priority.
        Object config=selection.getConstructor(boolean.class,position,boolean.class).newInstance(true,position.getField("BOTTOM").get(null),false);
        Object pack=type("server.packs.repository.Pack").getMethod("readMetaAndCreate",location,type("server.packs.repository.Pack$ResourcesSupplier"),packType,selection)
                .invoke(null,info,supplier,packType.getField("SERVER_DATA").get(null),config);
        if(pack==null)throw new IllegalStateException("Minecraft refused mod data pack "+spec.id());
        return pack;
    }
}
