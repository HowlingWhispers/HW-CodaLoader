package dev.howlingwhispers.codaloader.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import dev.howlingwhispers.codaloader.core.MiniJson;
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
        if (args.length > 0)
            Files.copy(Path.of(args[0]), jar);
        else
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
                    "assets/buildcraftcore/items/wrench.json",
                    "assets/buildcraftcore/models/item/wrench.json",
                    "assets/minecraft/atlases/blocks.json",
                    "assets/minecraft/atlases/items.json"}) {
                check(zip.getEntry(filename) != null, "required model or ORIGINAL texture: " + filename);
            }
            try (ZipFile source = new ZipFile(jar.toFile())) {
                for (String texture : List.of(
                        "assets/buildcrafttransport/textures/pipes/wood_item_clear.png",
                        "assets/buildcrafttransport/textures/pipes/cobblestone_item.png",
                        "assets/buildcraftcore/textures/items/wrench.png",
                        "assets/buildcraftcore/textures/blocks/engine/wood/side.png",
                        "assets/buildcraftcore/textures/blocks/engine/wood/back.png",
                        "assets/buildcraftlib/textures/blocks/engine/trunk_blue.png")) {
                    check(Arrays.equals(source.getInputStream(source.getEntry(texture)).readAllBytes(),
                            zip.getInputStream(zip.getEntry(texture)).readAllBytes()), "original texture bytes preserved: " + texture);
                }
            }
            java.util.Set<String> spriteIds = new java.util.HashSet<>();
            for (String atlas : List.of("blocks", "items")) {
                Map<?,?> json = (Map<?,?>) MiniJson.parse(new String(zip.getInputStream(
                        zip.getEntry("assets/minecraft/atlases/" + atlas + ".json")).readAllBytes(), StandardCharsets.UTF_8));
                List<?> sources = (List<?>) json.get("sources");
                check(sources.size() == (atlas.equals("blocks") ? 5 : 1), "sprites use their correct " + atlas + " atlas");
                for (Object entry : sources) {
                    Map<?,?> single = (Map<?,?>) entry;
                    check(single.get("type").equals("minecraft:single"), "native atlas source type");
                    String resource = single.get("resource").toString();
                    check(spriteIds.add(resource), "sprite is not duplicated across atlases: " + resource);
                    check(resource.endsWith("items/wrench") == atlas.equals("items"),
                            "block sprites never resolve to the items atlas");
                    String[] id = resource.split(":", 2);
                    check(zip.getEntry("assets/" + id[0] + "/textures/" + id[1] + ".png") != null,
                            "atlas sprite resolves to original image");
                }
            }
            Map<?,?> engine = (Map<?,?>) MiniJson.parse(new String(zip.getInputStream(
                    zip.getEntry("assets/buildcraftcore/models/block/engine_redstone.json")).readAllBytes(), StandardCharsets.UTF_8));
            Map<?,?> textures = (Map<?,?>) engine.get("textures");
            check(textures.get("back").equals("buildcraftcore:blocks/engine/wood/back"), "engine uses original back texture");
            check(textures.get("trunk").equals("buildcraftlib:blocks/engine/trunk_blue"), "engine uses original piston texture");
            Map<?,?> pipe = (Map<?,?>) MiniJson.parse(new String(zip.getInputStream(
                    zip.getEntry("assets/buildcrafttransport/models/block/wood_item.json")).readAllBytes(), StandardCharsets.UTF_8));
            Map<?,?> center = (Map<?,?>) ((List<?>) pipe.get("elements")).getFirst();
            check(center.get("from").toString().equals("[4, 4, 4]")
                    && center.get("to").toString().equals("[12, 12, 12]"), "original eight-pixel pipe body and UV bounds");
            for (String unsupported : List.of(
                    "assets/buildcraftlib/models/block/engine_base.json",
                    "assets/buildcraftcore/models/block/marker.json",
                    "assets/buildcraftcore/models/block/centeredTorch.json")) {
                check(zip.getEntry(unsupported) == null, "legacy model excluded from active pack: " + unsupported);
            }
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".json"))
                    check(MiniJson.parse(new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8)) instanceof Map,
                            "native adapter JSON parses: " + entry.getName());
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
            asset(zip, "assets/buildcraftcore/textures/blocks/engine/wood/back.png");
            asset(zip, "assets/buildcraftlib/textures/blocks/engine/trunk_blue.png");
            asset(zip, "assets/buildcraftcore/models/item/engine_redstone.json");
            asset(zip, "assets/buildcraftlib/models/block/engine_base.json");
            asset(zip, "assets/buildcraftcore/models/block/marker.json");
            asset(zip, "assets/buildcraftcore/models/block/centeredTorch.json");
        }
    }
}
