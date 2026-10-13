package dev.howlingwhispers.codaloader.bootstrap;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class ModDataPacksTest {
    private static int checks;
    static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static byte[] pack(Map<String,String> files)throws Exception{
        var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){
            for(var e:files.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
        }return bytes.toByteArray();
    }
    static void mod(Path game,byte[] bytes)throws Exception{
        Files.createDirectories(game.resolve("mods"));
        try(var zip=new ZipOutputStream(Files.newOutputStream(game.resolve("mods/test.jar")))){
            zip.putNextEntry(new ZipEntry("coda.mod.json"));zip.write("{\"id\":\"test_mod\",\"minecraft\":\"26.4-snapshot-3\",\"dataPack\":\"data.zip\"}".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("data.zip"));zip.write(bytes);zip.closeEntry();
        }
    }
    static void rejects(Map<String,String> files,String why)throws Exception{
        Path game=Files.createTempDirectory("howl-bad-data-");mod(game,pack(files));
        try{CodaModDataPacks.prepare(game);throw new AssertionError("Accepted "+why);}catch(IOException expected){checks++;}
        check(!Files.exists(game.resolve(".howl/mod-datapacks/test_mod.zip")),"Invalid data never cached: "+why);
    }
    public static void main(String[] args)throws Exception{
        var files=new HashMap<String,String>();files.put("pack.mcmeta","{\"pack\":{\"description\":\"Test\",\"min_format\":123,\"max_format\":123}}");
        files.put("data/test_mod/recipe/test.json","{}");
        byte[] original=pack(files);Path game=Files.createTempDirectory("howl-mod-data-");mod(game,original);
        var prepared=CodaModDataPacks.prepare(game);check(prepared.size()==1,"Declared server pack discovered");
        check(Arrays.equals(original,Files.readAllBytes(prepared.getFirst().file())),"Exact embedded bytes installed");
        check(CodaModDataPacks.prepare(game).equals(prepared),"Preparation repeat is stable");
        check(!Files.exists(game.resolve("saves")),"No existing world files edited");
        files.put("data/test_mod/recipe/test.json","{\"updated\":true}");byte[] update=pack(files);mod(game,update);
        check(Arrays.equals(update,Files.readAllBytes(CodaModDataPacks.prepare(game).getFirst().file())),"Mod update refreshes data");
        Files.delete(game.resolve("mods/test.jar"));check(CodaModDataPacks.prepare(game).isEmpty(),"Removed mod's cached data is not discovered");
        var wrong=new HashMap<>(files);wrong.put("data/minecraft/advancement/story/root.json","{}");rejects(wrong,"vanilla advancement override");
        wrong=new HashMap<>(files);wrong.put("pack.mcmeta","{\"pack\":{\"min_format\":123,\"max_format\":123},\"filter\":{}}");rejects(wrong,"vanilla data filter");
        wrong=new HashMap<>(files);wrong.put("pack.mcmeta","{\"pack\":{\"min_format\":122,\"max_format\":122}}");rejects(wrong,"wrong Snapshot format");
        wrong=new HashMap<>(files);wrong.put("data/minecraft/tags/function/tick.json","{\"replace\":true,\"values\":[\"test_mod:tick\"]}");rejects(wrong,"replace vanilla function tags");
        wrong=new HashMap<>(files);wrong.put("data/minecraft/tags/function/tick.json","{\"values\":[\"foreign:tick\"]}");rejects(wrong,"foreign function tag");
        files.put("data/minecraft/tags/function/tick.json","{\"replace\":false,\"values\":[\"test_mod:tick\"]}");mod(game,pack(files));check(CodaModDataPacks.prepare(game).size()==1,"Own function tag appends accepted");
        System.out.println("PASS: "+checks+" declared mod server-data checks");
    }
}
