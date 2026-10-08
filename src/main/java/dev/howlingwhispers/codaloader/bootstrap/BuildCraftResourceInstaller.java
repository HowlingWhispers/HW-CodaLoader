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
                    // Minecraft 26.4 uses modern item model entrypoints.
                    // Preserve original textures, replacing only these specific
                    // old 1.12 JSON descriptors with native-format adapters.
                    if (name.equals("assets/buildcraftcore/models/item/engine_redstone.json")
                        || name.equals("assets/buildcraftcore/lang/en_us.json")
                        || name.equals("assets/buildcrafttransport/lang/en_us.json"))
                        continue;
                    // 1.12 models are preserved except for the explicitly
                    // translated 26.4 block/item definitions below.
                    if (!paths.add(name)) throw new IOException("Duplicate BuildCraft asset " + name);
                    if (++count > MAX_ENTRIES || entry.getSize() > MAX_BYTES)
                        throw new IOException("BuildCraft source resource limit exceeded");
                    dest.putNextEntry(new ZipEntry(name));
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
                    || !paths.contains("assets/buildcraftcore/textures/items/wrench.png"))
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
                itemDefinition(dest, paths, "buildcraftcore", "wrench");
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
        output.putNextEntry(new ZipEntry(path));
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
                + element(5,5,5,11,11,11) + ","
                + element(0,6,6,5,10,10) + "," + element(11,6,6,16,10,10) + ","
                + element(6,0,6,10,5,10) + "," + element(6,11,6,10,16,10) + ","
                + element(6,6,0,10,10,5) + "," + element(6,6,11,10,10,16)
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
        String texture = "buildcraftcore:blocks/engine/wood/side";
        String model = "{\"textures\":{\"pipe\":\"" + texture + "\",\"particle\":\""
                + texture + "\"},\"elements\":["
                + element(2,0,2,14,3,14) + "," + element(4,3,4,12,12,12)
                + "," + element(6,12,6,10,16,10) + "]}";
        put(zip, paths, "assets/" + ns + "/models/block/" + id + ".json", model);
        put(zip, paths, "assets/" + ns + "/blockstates/" + id + ".json",
            "{\"variants\":{\"\":{\"model\":\"" + ns + ":block/" + id + "\"}}}");
        // Override obsolete 1.12 engine item model without copying another texture.
        // Original path was models/item/engine_redstone.json: skip it on import.
        put(zip, paths, "assets/" + ns + "/models/item/" + id + ".json",
            "{\"parent\":\"" + ns + ":block/" + id + "\"}");
        itemDefinition(zip, paths, ns, id);
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
