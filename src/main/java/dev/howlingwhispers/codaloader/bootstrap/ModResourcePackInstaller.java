package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Exposes explicitly declared mod assets to vanilla before the first client resource reload. */
public final class ModResourcePackInstaller {
    private static final int MAX_PACK=8*1024*1024,MAX_ENTRY=1024*1024;
    private ModResourcePackInstaller(){}
    static List<String> prepare(Path game) throws IOException {
        Path mods=game.resolve("mods");if(!Files.isDirectory(mods))return List.of();
        List<Path> jars;
        try(var entries=Files.list(mods)){jars=entries.filter(p->Files.isRegularFile(p)&&p.getFileName().toString().endsWith(".jar")).sorted().toList();}
        var installed=new ArrayList<String>();
        for(Path jar:jars){
            try(ZipFile source=new ZipFile(jar.toFile())){
                ZipEntry manifest=source.getEntry("coda.mod.json");if(manifest==null)continue;
                if(manifest.getSize()>8192)throw new IOException("Oversized mod metadata");
                Object decoded;
                try(InputStream in=source.getInputStream(manifest)){decoded=MiniJson.parse(new String(in.readNBytes(8193),StandardCharsets.UTF_8));}
                if(!(decoded instanceof Map<?,?> metadata)||!metadata.containsKey("resourcePack"))continue;
                String id=Objects.toString(metadata.get("id"),""),resource=Objects.toString(metadata.get("resourcePack"),"");
                if(!id.matches("[a-z0-9_.-]{1,64}")||!resource.matches("[a-zA-Z0-9_.-]+\\.zip")
                    ||!CodaTarget.MINECRAFT_VERSION.equals(metadata.get("minecraft")))throw new IOException("Invalid mod resource declaration");
                ZipEntry embedded=source.getEntry(resource);if(embedded==null||embedded.getSize()>MAX_PACK)throw new IOException("Declared resource pack missing or oversized: "+id);
                byte[] bytes;
                try(InputStream in=source.getInputStream(embedded)){bytes=in.readNBytes(MAX_PACK+1);}
                if(bytes.length>MAX_PACK)throw new IOException("Oversized mod resource pack");
                validate(id,bytes);
                Path packs=game.resolve("resourcepacks");Files.createDirectories(packs);
                String file="HOWL-Mod-"+id+".zip";Path target=packs.resolve(file),marker=packs.resolve(file+".sha256");
                String hash=hash(bytes);
                if(Files.exists(target)&&!hash.equals(hash(Files.readAllBytes(target)))){
                    if(!Files.isRegularFile(marker)||!Files.readString(marker).trim().equals(hash(Files.readAllBytes(target))))
                        throw new IOException("Player-edited "+file+" preserved. Move it aside before launching to restore mod textures.");
                }
                if(!Files.exists(target)||!hash.equals(hash(Files.readAllBytes(target)))){
                    Path staged=Files.createTempFile(packs,".howl-mod-assets-",".tmp");
                    try{Files.write(staged,bytes);Files.move(staged,target,StandardCopyOption.REPLACE_EXISTING);}
                    finally{Files.deleteIfExists(staged);}
                }
                Files.writeString(marker,hash,StandardCharsets.UTF_8);installed.add("file/"+file);
                System.out.println("[H.O.W.L.] Declared mod resource pack prepared: "+id);
            }catch(IllegalArgumentException e){throw new IOException("Invalid declared mod resources in "+jar.getFileName(),e);}
        }
        return List.copyOf(installed);
    }
    private static void validate(String id,byte[] bytes) throws IOException{
        Set<String> names=new HashSet<>();long total=0;boolean assets=false,meta=false;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            for(ZipEntry e;(e=zip.getNextEntry())!=null;){
                String name=e.getName();
                if(names.size()>=512||!names.add(name)||name.contains("..")||name.contains("\\")||name.startsWith("/"))throw new IOException("Unsafe mod pack layout");
                if(e.isDirectory())continue;
                if(!name.equals("pack.mcmeta")&&!name.startsWith("assets/"+id+"/"))throw new IOException("Mod pack assets must belong to "+id);
                byte[] data=zip.readNBytes(MAX_ENTRY+1);total+=data.length;
                if(data.length>MAX_ENTRY||total>16*1024*1024)throw new IOException("Mod pack expansion too large");
                if(name.equals("pack.mcmeta")){
                    Object parsed=MiniJson.parse(new String(data,StandardCharsets.UTF_8));
                    if(!(parsed instanceof Map<?,?> root)||!(root.get("pack") instanceof Map<?,?> pack)
                        ||!format(pack.get("min_format"))||!format(pack.get("max_format")))throw new IOException("Wrong Snapshot resource-pack format");
                    meta=true;
                }else assets=true;
            }
        }
        if(!meta||!assets)throw new IOException("Mod pack metadata or assets missing");
    }
    private static boolean format(Object value){
        if(value instanceof Number n)return n.intValue()==CodaTarget.MINECRAFT_RESOURCE_PACK_FORMAT;
        return value instanceof List<?> l&&l.size()==2&&l.getFirst() instanceof Number n&&n.intValue()==CodaTarget.MINECRAFT_RESOURCE_PACK_FORMAT&&l.get(1) instanceof Number minor&&minor.intValue()==0;
    }
    private static String hash(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}
    }
}
