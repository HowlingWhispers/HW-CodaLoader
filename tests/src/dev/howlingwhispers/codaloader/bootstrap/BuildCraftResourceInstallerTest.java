package dev.howlingwhispers.codaloader.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Original-art packaging must be self-contained and safe for edited profiles. */
public final class BuildCraftResourceInstallerTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void asset(ZipOutputStream zip, String name) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(new byte[]{1,2,3,4});
        zip.closeEntry();
    }
    public static void main(String[] args) throws Exception {
        Path game = Files.createTempDirectory("howl-buildcraft-original-art");
        Files.createDirectories(game.resolve("mods"));
        Path jar = game.resolve("mods/buildcraft-cml-0.1.0-dev.jar");
        usingJar(jar);
        check(BuildCraftResourceInstaller.prepare(game), "resource pack generated");
        Path result = game.resolve("resourcepacks/HOWL-BuildCraft-8.0.0.zip");
        check(Files.exists(result), "one generated resourcepack ZIP");
        try (ZipFile zip = new ZipFile(result.toFile())) {
            for (String filename : new String[]{
                    "pack.mcmeta",
                    "assets/buildcrafttransport/textures/pipes/wood_item_clear.png",
                    "assets/buildcrafttransport/textures/pipes/cobblestone_item.png",
                    "assets/buildcraftcore/textures/items/wrench.png",
                    "assets/buildcrafttransport/models/block/wood_item.json",
                    "assets/buildcrafttransport/blockstates/wood_item.json",
                    "assets/buildcrafttransport/items/wood_item.json",
                    "assets/buildcrafttransport/models/item/cobblestone_item.json",
                    "assets/buildcraftcore/models/item/engine_redstone.json",
                    "assets/buildcraftcore/items/wrench.json"}) {
                check(zip.getEntry(filename) != null, "required model or ORIGINAL texture: " + filename);
            }
        }
        String before = Files.readString(game.resolve("resourcepacks/HOWL-BuildCraft-8.0.0.zip.sha256"));
        check(BuildCraftResourceInstaller.prepare(game), "identical pack regenerate");
        check(Files.readString(game.resolve("resourcepacks/HOWL-BuildCraft-8.0.0.zip.sha256"))
            .equals(before), "stable source assets produce stable hash");
        Files.write(result, new byte[]{9, 8, 7});
        check(!BuildCraftResourceInstaller.prepare(game), "player-modified pack not overwritten");
        check(Files.readAllBytes(result)[0] == 9, "user modifications preserved");
        System.out.println("PASS: " + checks + " original-art adapter pack checks");
    }
    private static void usingJar(Path jar) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            asset(zip, "assets/buildcrafttransport/textures/pipes/wood_item_clear.png");
            asset(zip, "assets/buildcrafttransport/textures/pipes/cobblestone_item.png");
            asset(zip, "assets/buildcraftcore/textures/items/wrench.png");
            asset(zip, "assets/buildcraftcore/textures/blocks/engine/wood/side.png");
            asset(zip, "assets/buildcraftcore/models/item/engine_redstone.json");
        }
    }
}
