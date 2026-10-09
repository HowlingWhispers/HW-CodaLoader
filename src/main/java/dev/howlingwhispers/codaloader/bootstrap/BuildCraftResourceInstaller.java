package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.CodaTarget;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Uses the unchanged assets from BuildCraft 8.0.0's verified Nightly JAR.
 * Generates ONLY the modern Minecraft 26.4 item-definition and geometric
 * model adapter required to display them. No substitute painted textures.
 * Writes one owned ZIP under resourcepacks, no second mods directory.
 */
final class BuildCraftResourceInstaller {
    private static final String MOD = "buildcraft-cml-0.1.0-dev.jar";
    private static final String RESOURCE = "HOWL-BuildCraft-8.0.0.zip";
    private static final int MAX_ENTRIES = 12_000;
    private static final long MAX_BYTES = 60L * 1024 * 1024;

    private BuildCraftResourceInstaller() {}

    static String packName() { return RESOURCE; }

    static boolean prepare(Path game) throws IOException {
        Path mod = game.resolve("mods").resolve(MOD);
        if (!Files.isRegularFile(mod)) return false;
        Path output = game.resolve("resourcepacks").resolve(RESOURCE);
        Path marker = game.resolve("resourcepacks").resolve(RESOURCE + ".sha256");
        Files.createDirectories(output.getParent());
        Path stage = Files.createTempFile(output.getParent(), ".buildcraft-assets-", ".zip");
        try {
            try (ZipFile original = new ZipFile(mod.toFile());
                 ZipOutputStream dest = new ZipOutputStream(Files.newOutputStream(stage))) {
                Set<String> paths = new HashSet<>();
                int count = 0;
                long bytes = 0;
                for (var e = original.entries(); e.hasMoreElements();) {
                    ZipEntry entry = e.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.startsWith("assets/")
                            || !(name.startsWith("assets/buildcraftcore/")
                                || name.startsWith("assets/buildcrafttransport/")
                                || name.startsWith("assets/buildcraftlib/")
                                || name.startsWith("assets/buildcraftenergy/")
                                || name.startsWith("assets/buildcraftfactory/")
                                || name.startsWith("assets/buildcraftbuilders/")
                                || name.startsWith("assets/buildcraftsilicon/")
                                || name.startsWith("assets/buildcraftrobotics/")))
                        continue;
                    if (name.contains("..") || name.contains("\\") || name.length() > 256)
                        throw new IOException("Unsafe BuildCraft resource path");
                    // Original files remain intact in the mod JAR. The active
                    // pack contains original PNGs and native JSON adapters,
                    // not Forge's expression models or uppercase identifiers.
                    if (!name.matches("assets/[a-z0-9_.-]+/textures/[a-z0-9_./-]+\\.png(?:\\.mcmeta)?"))
                        continue;
                    if (!paths.add(name)) throw new IOException("Duplicate BuildCraft asset " + name);
                    if (++count > MAX_ENTRIES || entry.getSize() > MAX_BYTES)
                        throw new IOException("BuildCraft source resource limit exceeded");
                    ZipEntry outputEntry = new ZipEntry(name);
                    outputEntry.setTime(0L);
                    dest.putNextEntry(outputEntry);
                    try (InputStream stream = original.getInputStream(entry)) {
                        byte[] buf = new byte[8192];
                        for (int n; (n = stream.read(buf)) >= 0;) {
                            bytes += n;
                            if (bytes > MAX_BYTES) throw new IOException("Oversized upstream assets");
                            dest.write(buf, 0, n);
                        }
                    }
                    dest.closeEntry();
                }
                if (!paths.contains("assets/buildcrafttransport/textures/pipes/wood_item_clear.png")
                    || !paths.contains("assets/buildcrafttransport/textures/pipes/cobblestone_item.png")
                    || !paths.contains("assets/buildcraftcore/textures/items/wrench.png")
                    || !paths.contains("assets/buildcraftcore/textures/blocks/engine/wood/side.png"))
                    throw new IOException("Original BuildCraft 8.0.0 art was not embedded");

                put(dest, paths, "pack.mcmeta", "{\"pack\":{\"description\":\"BuildCraft 8.0.0 original resources, H.O.W.L. 26.4 adapter\",\"min_format\":["
                    + CodaTarget.MINECRAFT_RESOURCE_PACK_FORMAT + ",0],\"max_format\":["
                    + CodaTarget.MINECRAFT_RESOURCE_PACK_FORMAT + ",0]}}");

                // Original sprites are kept byte-for-byte. Only the Mojang
                // version-dependent block geometry and item-definition JSON
                // format is generated here, never BuildCraft visual artwork.
                pipe(dest, paths, "buildcrafttransport", "wood_item",
                        "buildcrafttransport:pipes/wood_item_clear");
                pipe(dest, paths, "buildcrafttransport", "cobblestone_item",
                        "buildcrafttransport:pipes/cobblestone_item");
                engine(dest, paths);
                put(dest, paths, "assets/buildcraftcore/models/item/wrench.json",
                        "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"buildcraftcore:items/wrench\"}}");
                itemDefinition(dest, paths, "buildcraftcore", "wrench");
                // A sprite ID belongs to ONE atlas. Duplicating it across
                // items and blocks makes Snapshot 3 reject block models.
                String blocksAtlas = "{\"sources\":["
                        + single("buildcrafttransport:pipes/wood_item_clear") + ","
                        + single("buildcrafttransport:pipes/cobblestone_item") + ","
                        + single("buildcraftcore:blocks/engine/wood/side") + ","
                        + single("buildcraftcore:blocks/engine/wood/back") + ","
                        + single("buildcraftlib:blocks/engine/trunk_blue") + "]}";
                put(dest, paths, "assets/minecraft/atlases/blocks.json", blocksAtlas);
                put(dest, paths, "assets/minecraft/atlases/items.json",
                        "{\"sources\":[" + single("buildcraftcore:items/wrench") + "]}");
                put(dest, paths, "assets/buildcraftcore/lang/en_us.json",
                        "{\"item.buildcraftcore.wrench\":\"BuildCraft Wrench\","
                        + "\"block.buildcraftcore.engine_redstone\":\"Redstone Engine\","
                        + "\"item.buildcraftcore.engine_redstone\":\"Redstone Engine\"}");
                put(dest, paths, "assets/buildcrafttransport/lang/en_us.json",
                        "{\"block.buildcrafttransport.wood_item\":\"Wooden Transport Pipe\","
                        + "\"item.buildcrafttransport.wood_item\":\"Wooden Transport Pipe\","
                        + "\"block.buildcrafttransport.cobblestone_item\":\"Cobblestone Transport Pipe\","
                        + "\"item.buildcrafttransport.cobblestone_item\":\"Cobblestone Transport Pipe\"}");
            }
            String hash = hash(stage);
            if (Files.exists(output)) {
                if (!Files.isRegularFile(marker) ||
                        !hash(output).equals(Files.readString(marker).trim())) {
                    System.err.println("[H.O.W.L.] BuildCraft resourcepack was edited by the player; preserving it.");
                    return false;
                }
            }
            Files.move(stage, output, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(marker, hash, StandardCharsets.UTF_8);
            System.out.println("[H.O.W.L.] Original BuildCraft 8.0.0 art pack prepared: " + output);
            return true;
        } finally { Files.deleteIfExists(stage); }
    }

    private static void put(ZipOutputStream output, Set<String> existing, String path, String text)
            throws IOException {
        // A generated modern adapter may override a legacy 1.12 model.
        // Otherwise duplicate ZIP names cause Minecraft resourcepack issues.
        if (!existing.add(path)) {
            // Legacy entries MUST NOT be emitted earlier with the same path.
            throw new IOException("Original BuildCraft asset conflicts with generated 26.4 resource: " + path);
        }
        ZipEntry newEntry = new ZipEntry(path);
        newEntry.setTime(0L);
        output.putNextEntry(newEntry);
        output.write(text.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static void itemDefinition(ZipOutputStream zip, Set<String> paths,
                                       String namespace, String id) throws IOException {
        put(zip, paths, "assets/" + namespace + "/items/" + id + ".json",
            "{\"model\":{\"type\":\"minecraft:model\",\"model\":\""
                + namespace + ":item/" + id + "\"}}");
    }

    private static String faces() {
        return "{\"north\":{\"texture\":\"#pipe\"},\"south\":{\"texture\":\"#pipe\"},"
             + "\"east\":{\"texture\":\"#pipe\"},\"west\":{\"texture\":\"#pipe\"},"
             + "\"up\":{\"texture\":\"#pipe\"},\"down\":{\"texture\":\"#pipe\"}}";
    }
    private static String element(int x1, int y1, int z1, int x2, int y2, int z2) {
        return "{\"from\":[" + x1 + "," + y1 + "," + z1 + "],\"to\":["
                + x2 + "," + y2 + "," + z2 + "],\"faces\":" + faces() + "}";
    }
    private static void pipe(ZipOutputStream zip, Set<String> paths,
                             String ns, String id, String texture) throws IOException {
        String model = "{\"textures\":{\"pipe\":\"" + texture + "\",\"particle\":\""
                + texture + "\"},\"elements\":["
                + element(4,4,4,12,12,12) + ","
                + element(0,4,4,4,12,12) + "," + element(12,4,4,16,12,12) + ","
                + element(4,0,4,12,4,12) + "," + element(4,12,4,12,16,12) + ","
                + element(4,4,0,12,12,4) + "," + element(4,4,12,12,12,16)
                + "]}";
        put(zip, paths, "assets/" + ns + "/models/block/" + id + ".json", model);
        put(zip, paths, "assets/" + ns + "/blockstates/" + id + ".json",
            "{\"variants\":{\"\":{\"model\":\"" + ns + ":block/" + id + "\"}}}");
        put(zip, paths, "assets/" + ns + "/models/item/" + id + ".json",
            "{\"parent\":\"" + ns + ":block/" + id + "\"}");
        itemDefinition(zip, paths, ns, id);
    }

    private static void engine(ZipOutputStream zip, Set<String> paths) throws IOException {
        String ns = "buildcraftcore", id = "engine_redstone";
        // Original 8.0.0 engine_base dimensions and UVs, evaluated at
        // rest (progress=0, stage=blue). Do not stretch the side texture
        // over the piston trunk or replace the original back texture.
        String model = "{\"textures\":{"
                + "\"side\":\"buildcraftcore:blocks/engine/wood/side\","
                + "\"back\":\"buildcraftcore:blocks/engine/wood/back\","
                + "\"trunk\":\"buildcraftlib:blocks/engine/trunk_blue\","
                + "\"particle\":\"buildcraftcore:blocks/engine/wood/side\"},\"elements\":["
                + engineBase(0,4) + "," + engineBase(4,8) + ","
                + "{\"from\":[4,4,4],\"to\":[12,16,12],\"faces\":"
                + engineFaces("trunk", "trunk", "[8,0,16,12]", "[0,0,8,8]") + "}]}";
        put(zip, paths, "assets/" + ns + "/models/block/" + id + ".json", model);
        put(zip, paths, "assets/" + ns + "/blockstates/" + id + ".json",
            "{\"variants\":{\"\":{\"model\":\"" + ns + ":block/" + id + "\"}}}");
        // Override obsolete 1.12 engine item model without copying another texture.
        // Original path was models/item/engine_redstone.json: skip it on import.
        put(zip, paths, "assets/" + ns + "/models/item/" + id + ".json",
            "{\"parent\":\"" + ns + ":block/" + id + "\"}");
        itemDefinition(zip, paths, ns, id);
    }

    private static String single(String texture) {
        return "{\"type\":\"minecraft:single\",\"resource\":\"" + texture + "\"}";
    }
    private static String engineBase(int bottom, int top) {
        return "{\"from\":[0," + bottom + ",0],\"to\":[16," + top
                + ",16],\"faces\":" + engineFaces("side", "back", "[0,0,16,4]", "[0,0,16,16]") + "}";
    }
    private static String engineFaces(String side, String end, String sideUv, String endUv) {
        String lateral = "{\"texture\":\"#" + side + "\",\"uv\":" + sideUv + "}";
        String cap = "{\"texture\":\"#" + end + "\",\"uv\":" + endUv + "}";
        return "{\"north\":" + lateral + ",\"south\":" + lateral
                + ",\"east\":" + lateral + ",\"west\":" + lateral
                + ",\"up\":" + cap + ",\"down\":" + cap + "}";
    }

    private static String hash(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            try {
                MessageDigest dig = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) >= 0;) dig.update(buf, 0, n);
                return HexFormat.of().formatHex(dig.digest());
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IOException(impossible);
            }
        }
    }
}
