package dev.howlingwhispers.codaloader.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipFile;

/** Verify the exact optional BuildCraft Lite JAR is rendered through a real pack. */
public final class BuildCraftLiteResourceInstallerTest {
    private static int checks;
    private static void check(boolean yes, String what) {
        checks++;
        if (!yes) throw new AssertionError(what);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("howl-bclite-pack-");
        try {
            Path mods = Files.createDirectories(root.resolve("mods"));
            Path jar = Path.of("dist/buildcraft-lite-0.1.0-dev.jar").toAbsolutePath();
            check(Files.isRegularFile(jar), "Built optional BuildCraft Lite JAR missing");
            Files.copy(jar, mods.resolve("buildcraft-lite.jar"), StandardCopyOption.REPLACE_EXISTING);
            check(BuildCraftLiteResourceInstaller.prepare(root),
                    "Mod asset pack should be discovered and enabled");
            Path pack = root.resolve("resourcepacks/HOWL-BuildCraft-Lite.zip");
            check(Files.isRegularFile(pack), "Generated resource pack absent");
            try (ZipFile z = new ZipFile(pack.toFile())) {
                for (String required : java.util.List.of(
                        "pack.mcmeta",
                        "assets/hw_buildcraft_lite/blockstates/wooden_transport_pipe.json",
                        "assets/hw_buildcraft_lite/blockstates/stone_transport_pipe.json",
                        "assets/hw_buildcraft_lite/items/wooden_transport_pipe.json",
                        "assets/hw_buildcraft_lite/items/stone_transport_pipe.json",
                        "assets/hw_buildcraft_lite/textures/block/wood_item.png",
                        "assets/hw_buildcraft_lite/textures/block/stone_item.png",
                        "assets/hw_buildcraft_lite/blockstates/redstone_engine.json",
                        "assets/hw_buildcraft_lite/items/redstone_engine.json",
                        "assets/hw_buildcraft_lite/items/wrench.json",
                        "assets/hw_buildcraft_lite/textures/item/wrench.png")) {
                    check(z.getEntry(required) != null, "Missing pack resource: " + required);
                }
            }
            // A base model with baked six arms would render dangling stubs
            // regardless of which neighboring blocks actually exist.
            try (ZipFile zip = new ZipFile(pack.toFile())) {
                for (String kind : java.util.List.of("wooden", "stone")) {
                    String blockstatePath = "assets/hw_buildcraft_lite/blockstates/"
                            + kind + "_transport_pipe.json";
                    String model = new String(zip.getInputStream(zip.getEntry(blockstatePath))
                            .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    check(model.contains("multipart"), "Pipe states must be conditional");
                    for (String direction : java.util.List.of(
                            "east", "west", "up", "down", "south", "north")) {
                        check(model.contains(direction), "Missing connection face " + direction);
                        check(zip.getEntry("assets/hw_buildcraft_lite/models/block/"
                                + kind + "_pipe_" + direction + ".json") != null,
                                "Missing conditional arm " + direction);
                    }
                }
                check(zip.getEntry("assets/hw_buildcraft_lite/textures/block/engine_wood_side.png") != null,
                        "Missing original Redstone Engine art");
            }
            // An older retired BuildCraft pack may still be selected by options.txt.
            // The Lite pack must own the active visuals without deleting player files.
            Path options = root.resolve("options.txt");
            Files.writeString(options, "lang:en_us\nresourcePacks:[\"vanilla\","
                    + "\"file/HOWL-BuildCraft-8.0.0.zip\","
                    + "\"file/HOWL-BuildCraft-Lite.zip\",\"file/Personal.zip\"]\n");
            MinecraftBootstrap.disableRetiredBuildCraftPack(root);
            String selected = Files.readString(options);
            check(!selected.contains("file/HOWL-BuildCraft-8.0.0.zip"),
                    "Retired BuildCraft pack no longer selected");
            check(selected.contains("file/HOWL-BuildCraft-Lite.zip"),
                    "Lite pack remains enabled");
            check(selected.contains("file/Personal.zip") && selected.contains("lang:en_us"),
                    "Unrelated player options and packs preserved");
            MinecraftBootstrap.disableRetiredBuildCraftPack(root);
            check(Files.readString(options).equals(selected),
                    "Repeated cleanup does not change player settings");
            byte[] initial = Files.readAllBytes(pack);
            Files.writeString(pack, "PLAYER EDITED PACK");
            check(!BuildCraftLiteResourceInstaller.prepare(root),
                    "Player edits must never be overwritten");
            check(Files.readString(pack).equals("PLAYER EDITED PACK"),
                    "Modified pack bytes must be preserved");
            Files.write(pack, initial);
            check(BuildCraftLiteResourceInstaller.prepare(root),
                    "Untampered pack can be refreshed");
            System.out.println("PASS: " + checks + " original BCCE art/resource-pack safety checks");
        } finally {
            try (var walk = Files.walk(root)) {
                for (Path file : walk.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(file);
            }
        }
    }
}
