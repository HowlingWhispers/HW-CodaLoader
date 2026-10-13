package dev.howlingwhispers.codaloader.bootstrap;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import java.io.*;

public final class ModResourcePackInstallerTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static byte[] pack(String path,String value)throws Exception{
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)){
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));zip.write("{\"pack\":{\"min_format\":[100,0],\"max_format\":[100,0],\"description\":\"Test\"}}".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry(path));zip.write(value.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    static void mod(Path path,byte[] assets,boolean declared)throws Exception{
        try(var zip=new ZipOutputStream(Files.newOutputStream(path))){
            zip.putNextEntry(new ZipEntry("coda.mod.json"));zip.write(("{\"id\":\"howl_sorting_pipes\",\"minecraft\":\"26.4-snapshot-3\""+(declared?",\"resourcePack\":\"sorting-pipes-resources.zip\"":"")+"}").getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("sorting-pipes-resources.zip"));zip.write(assets);zip.closeEntry();
        }
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("howl-auto-mod-pack-");
        try{
            Path jar=Files.createDirectories(root.resolve("mods")).resolve("sorting.jar");
            byte[] first=pack("assets/howl_sorting_pipes/items/sorting_pipe.json","original-model");mod(jar,first,true);
            Path options=root.resolve("options.txt");Files.writeString(options,"lang:en_us\nresourcePacks:[\"vanilla\",\"file/Personal.zip\"]\n");
            new MinecraftBootstrap(root,root.resolve("base")).prepareCodaWolfResources();
            Path pack=root.resolve("resourcepacks/HOWL-Mod-howl_sorting_pipes.zip");
            check(Arrays.equals(first,Files.readAllBytes(pack)),"Declared embedded pack installed byte-for-byte");
            String activated=Files.readString(options);
            check(activated.contains("file/HOWL-Mod-howl_sorting_pipes.zip"),"Pack auto-selected before initial Minecraft reload");
            check(activated.contains("file/Personal.zip")&&activated.contains("lang:en_us"),"Player options and unrelated packs retained");
            new MinecraftBootstrap(root,root.resolve("base")).prepareCodaWolfResources();check(Files.readString(options).equals(activated),"Repeated launch does not duplicate selection");
            byte[] second=pack("assets/howl_sorting_pipes/items/sorting_pipe.json","updated-model");mod(jar,second,true);
            new MinecraftBootstrap(root,root.resolve("base")).prepareCodaWolfResources();check(Arrays.equals(second,Files.readAllBytes(pack)),"Managed pack refreshes when mod updates");
            check(Files.readString(options).equals(activated),"Update retains one canonical resource-pack entry");
            Files.writeString(pack,"PLAYER EDIT");
            try{ModResourcePackInstaller.prepare(root);throw new AssertionError("Edited resources overwritten");}catch(IOException expected){checks++;}
            check(Files.readString(pack).equals("PLAYER EDIT"),"Player-edited resources preserved");
            Files.delete(pack);Files.deleteIfExists(pack.resolveSibling(pack.getFileName()+".sha256"));
            mod(jar,pack("assets/minecraft/items/stone.json","foreign"),true);
            try{ModResourcePackInstaller.prepare(root);throw new AssertionError("Foreign namespace accepted");}catch(IOException expected){checks++;}
            check(!Files.exists(pack),"Invalid pack never committed");
            mod(jar,first,false);check(ModResourcePackInstaller.prepare(root).isEmpty(),"Non-declared legacy mod assets untouched");
            if(args.length>0){
                Files.copy(Path.of(args[0]),jar,StandardCopyOption.REPLACE_EXISTING);
                new MinecraftBootstrap(root,root.resolve("base")).prepareCodaWolfResources();
                try(ZipFile zip=new ZipFile(pack.toFile())){
                    for(String name:List.of("extractor_pipe","transport_pipe","sorting_pipe","round_robin","equalizer")){
                        check(zip.getEntry("assets/howl_sorting_pipes/items/"+name+".json")!=null,"Real item model visible to resource manager: "+name);
                        check(zip.getEntry("assets/howl_sorting_pipes/models/item/"+name+".json")!=null,"Real model parent available: "+name);
                        check(zip.getEntry("assets/howl_sorting_pipes/blockstates/"+name+".json")!=null,"Real blockstate visible: "+name);
                    }
                }
            }
            System.out.println("PASS: "+checks+" automatic mod asset activation and update checks");
        }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
