package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaEntityAppearance;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.objectweb.asm.*;

/**
 * Headless verification of Coda-specific renderer ASM and atlas generation.
 * Given a Mojang client JAR, also checks the actual named Snapshot 3 class.
 */
public final class CodaWolfTextureTest {
    private static int checks;
    private static void check(boolean good, String label) {
        checks++;
        if (!good) throw new AssertionError(label);
    }
    private static final String RENDERER =
            "net/minecraft/client/renderer/entity/WolfRenderer";
    private static final String METHOD = "extractRenderState";
    private static final String DESC =
            "(Lnet/minecraft/world/entity/animal/wolf/Wolf;"
          + "Lnet/minecraft/client/renderer/entity/state/WolfRenderState;F)V";
    private static final String SELECTOR =
            "dev/howlingwhispers/codaloader/bootstrap/CodaWolfClientTextures";

    static byte[] renderer(String method, boolean twice) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,RENDERER,null,"java/lang/Object",null);
        var init=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD,0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0,0);
        init.visitEnd();
        var mv=writer.visitMethod(Opcodes.ACC_PUBLIC,method,DESC,null,null);
        mv.visitCode();
        if(twice) {
            var end = new org.objectweb.asm.Label();
            mv.visitVarInsn(Opcodes.ALOAD,1);
            mv.visitJumpInsn(Opcodes.IFNONNULL,end);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitLabel(end);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0,0);
        mv.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
    static void verifyTransformed(byte[] modified, int expectedCalls) {
        int[] calls={0};
        new ClassReader(modified).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int a,String name,String desc,
                    String signature,String[] exceptions) {
                if(!METHOD.equals(name)||!DESC.equals(desc))return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int op,String owner,String name,
                                                          String signature,boolean itf) {
                        if(owner.equals(SELECTOR) && name.equals("select") &&
                                signature.equals("(Ljava/lang/Object;Ljava/lang/Object;)V"))
                            calls[0]++;
                    }
                };
            }
        },0);
        check(calls[0]==expectedCalls,"Hook occurs exactly once at accepted renderer return");
    }
    public static void main(String[] args) throws Exception {
        var hook=new CodaWolfTextureTransformer();
        byte[] fixture=renderer(METHOD,false);
        byte[] result=hook.transform(null,RENDERER,null,null,fixture);
        check(result!=null,"Exact Snapshot 3 signature accepted");
        verifyTransformed(result,1);
        check(hook.transform(null,"other/renderer",null,null,fixture)==null,
                "Other entity renderers unchanged");
        check(hook.transform(null,RENDERER,null,null,renderer("changed",false))==null,
                "Unexpected Minecraft version fails closed");
        check(hook.transform(null,RENDERER,null,null,renderer(METHOD,true))==null,
                "Ambiguous multiple returns fail closed");

        // Live wolf renderer accepts direct PNG file identifiers, never the
        // shortened atlas-style asset IDs. Catch checkerboard regressions
        // before publishing another loader to players.
        check(CodaWolfClientTextures.wolfTextureLocation("codawolf:entity/coda_tame")
                  .equals("codawolf:textures/entity/coda_tame.png"),
                "Tamed Coda renderer uses actual resource pack PNG path");
        check(CodaWolfClientTextures.wolfTextureLocation("codawolf:entity/coda_angry")
                  .equals("codawolf:textures/entity/coda_angry.png"),
                "Angry Coda renderer uses actual resource pack PNG path");
        check(CodaWolfClientTextures.wolfTextureLocation(
                  "codawolf:textures/entity/coda_tame.png")
                  .equals("codawolf:textures/entity/coda_tame.png"),
                "Already-correct entity texture identifier preserved");
        try {
            CodaWolfClientTextures.wolfTextureLocation("codawolf:entity/../bad");
            throw new AssertionError("Unsafe entity texture path accepted");
        } catch (IllegalArgumentException expected) { checks++; }

        UUID coda=UUID.randomUUID(),ordinary=UUID.randomUUID();
        check(CodaEntityAppearance.wolfSkin(ordinary).isEmpty(),
                "Vanilla wolves are not registered");
        CodaEntityAppearance.setWolfSkin(coda,
                "codawolf:entity/coda_tame","codawolf:entity/coda_angry");
        check(CodaEntityAppearance.wolfSkin(coda).orElseThrow().angryTexture()
                .equals("codawolf:entity/coda_angry"),"Coda UUID picks both skin states");
        check(CodaEntityAppearance.wolfSkin(ordinary).isEmpty(),
                "Other wolf UUID keeps original skin");
        // The same world can replace Coda after bed sleep with a *different*
        // vanilla Wolf UUID. Only the new entity may receive her registered skin.
        UUID respawned=UUID.randomUUID();
        CodaEntityAppearance.clearWolfSkin(coda);
        CodaEntityAppearance.setWolfSkin(respawned,
                "codawolf:entity/coda_tame", "codawolf:entity/coda_angry");
        check(CodaEntityAppearance.wolfSkin(coda).isEmpty(),
                "Old dead wolf UUID no longer owns the skin");
        check(CodaEntityAppearance.wolfSkin(respawned).isPresent(),
                "New Coda UUID is eligible for her appearance");
        check(CodaEntityAppearance.wolfSkin(ordinary).isEmpty(),
                "Respawn does not recolor unrelated vanilla wolves");
        CodaEntityAppearance.clearWolfSkin(respawned);
        check(CodaEntityAppearance.wolfSkin(coda).isEmpty(),
                "World exit removes Coda-specific registration");

        BufferedImage uv=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);
        uv.setRGB(1,1,0xFF808080);
        uv.setRGB(2,2,0xFF000000);
        uv.setRGB(3,3,0x40FFFFFF);
        java.io.ByteArrayOutputStream raw=new java.io.ByteArrayOutputStream();
        ImageIO.write(uv,"png",raw);
        BufferedImage generated=CodaWolfResourceInstaller.recolor(
                new ByteArrayInputStream(raw.toByteArray()),false);
        check(generated.getWidth()==64&&generated.getHeight()==32,
                "Generated image keeps exact vanilla UV size");
        check(generated.getRGB(0,0)==0,"Transparent unused UV pixels remain transparent");
        check((generated.getRGB(3,3)>>>24)==0x40,
                "Pixel alpha transparency preserved on fur");
        check((generated.getRGB(1,1)&0xFFFFFF)!=0x808080,
                "Original gray converted into icy-blue Coda coloration");
        try {
            var bad=new BufferedImage(128,64,BufferedImage.TYPE_INT_ARGB);
            raw.reset();ImageIO.write(bad,"png",raw);
            CodaWolfResourceInstaller.recolor(new ByteArrayInputStream(raw.toByteArray()),false);
            throw new AssertionError("Misaligned AI texture was accepted");
        } catch (java.io.IOException expected) {checks++;}

        if(args.length>0){
            try(JarFile jar=new JarFile(Path.of(args[0]).toFile())){
                ZipEntry renderer=jar.getEntry(RENDERER+".class");
                check(renderer!=null,"Real Mojang wolf renderer exists");
                byte[] authentic=jar.getInputStream(renderer).readAllBytes();
                byte[] patched=hook.transform(null,RENDERER,null,null,authentic);
                check(patched!=null,"Production hook recognizes exact Mojang Snapshot 3 wolf class");
                verifyTransformed(patched,1);
                ZipEntry source=jar.getEntry(
                    "assets/minecraft/textures/entity/wolf/wolf_snowy_tame.png");
                check(source!=null,"Real Mojang snowy wolf UV source available");
                BufferedImage actual=CodaWolfResourceInstaller.recolor(
                    jar.getInputStream(source),false);
                check(actual.getWidth()==64&&actual.getHeight()==32,
                      "Official Snapshot 3 Coda skin is valid 64x32 alpha atlas");
                ZipEntry angry=jar.getEntry(
                    "assets/minecraft/textures/entity/wolf/wolf_snowy_angry.png");
                check(angry!=null,"Original angry wolf variant available");
            }
            Path world=Files.createTempDirectory("coda-exclusive-skin-");
            check(!CodaWolfResourceInstaller.prepare(world),
                  "No Coda Companion mod means no generated resourcepack");
            Path mods=world.resolve("mods"); Files.createDirectories(mods);
            try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(
                    mods.resolve("coda-wolf-0.1.0-dev.jar")))){
                zip.putNextEntry(new ZipEntry("coda.mod.json"));
                zip.write("{\"schema\":1,\"id\":\"coda_wolf\"}".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            check(CodaWolfResourceInstaller.prepare(world),
                  "Coda-exclusive texture pack generated only with installed mod");
            Path pack=world.resolve("resourcepacks/HOWL-Coda-Wolf.zip");
            try(ZipFile contents=new ZipFile(pack.toFile())) {
                check(contents.getEntry("assets/codawolf/textures/entity/coda_tame.png")!=null,
                    "Coda tame art gets its own namespace");
                for (String texture : new String[]{
                        "codawolf:entity/coda_tame", "codawolf:entity/coda_angry"}) {
                    String identifier=CodaWolfClientTextures.wolfTextureLocation(texture);
                    String[] pieces=identifier.split(":",2);
                    String archiveEntry="assets/"+pieces[0]+"/"+pieces[1];
                    check(contents.getEntry(archiveEntry)!=null,
                          "WolfRenderState.texture exactly resolves to a real pack file: "
                                  + identifier);
                }
                check(contents.getEntry("assets/codawolf/textures/entity/coda_angry.png")!=null,
                    "Coda angry art gets its own namespace");
                check(contents.getEntry("assets/minecraft/textures/entity/wolf/wolf_tame.png")==null,
                    "Ordinary vanilla wolf skin is not replaced");
                check(contents.getEntry("pack.mcmeta")!=null,
                    "Modern Snapshot 3 pack metadata included");
            }
            check(CodaWolfResourceInstaller.prepare(world),
                  "Coda pack can be refreshed idempotently from original Mojang UV");
            Files.writeString(pack,"PLAYER-EDITED");
            check(!CodaWolfResourceInstaller.prepare(world),
                  "Modified cosmetic resourcepack is not overwritten");
            check(Files.readString(pack).equals("PLAYER-EDITED"),
                  "Player-edited resourcepack remains intact");
        }
        System.out.println("PASS: "+checks+" Coda wolf exclusive texture and UV tests");
    }
}
