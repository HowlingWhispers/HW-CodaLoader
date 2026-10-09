package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.CodaTarget;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Makes the optional BuildCraft Lite JAR's original licensed pipe textures and
 * native block models visible to the vanilla Minecraft resource manager.
 *
 * The H.O.W.L. mod classloader isn't itself a vanilla resource-pack source.
 * Never overwrite player-modified packs, read arbitrary jar paths or enable
 * assets belonging to another mod with the same file name.
 */
public final class BuildCraftLiteResourceInstaller {
    static final String PACK = "HOWL-BuildCraft-Lite.zip";
    private static final String ID = "hw_buildcraft_lite";
    private static final String ASSET_ROOT = "assets/hw_buildcraft_lite/";
    private static final int MAX_ASSET_BYTES = 1_048_576;
    private static final int MAX_FILES = 128;

    private BuildCraftLiteResourceInstaller() {}

    static boolean prepare(Path game) throws IOException {
        Path mods = game.resolve("mods");
        if (!Files.isDirectory(mods)) return false;
        List<Path> jars;
        try (var candidates = Files.list(mods)) {
            jars = candidates.filter(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().endsWith(".jar")).sorted().toList();
        }
        for (Path jar : jars) {
            if (isLiteJar(jar)) return install(game, jar);
        }
        return false;
    }

    private static boolean isLiteJar(Path path) {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry manifest = zip.getEntry("coda.mod.json");
            if (manifest == null || manifest.getSize() > 8192) return false;
            String metadata;
            try (InputStream in = zip.getInputStream(manifest)) {
                metadata = new String(in.readNBytes(8193), StandardCharsets.UTF_8);
            }
            return metadata.matches("(?s).*\\\"id\\\"\\s*:\\s*\\\"" + ID + "\\\".*");
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean install(Path game, Path jar) throws IOException {
        Path packs = game.resolve("resourcepacks");
        Files.createDirectories(packs);
        Path target = packs.resolve(PACK);
        Path marker = packs.resolve(PACK + ".sha256");

        if (Files.isRegularFile(target) && (!Files.isRegularFile(marker)
                || !sha256(target).equals(Files.readString(marker).trim()))) {
            System.err.println("[H.O.W.L.] Player-edited BuildCraft Lite pack preserved: " + target);
            return false;
        }

        Path temp = Files.createTempFile(packs, ".howl-buildcraft-lite-", ".zip");
        try (ZipFile source = new ZipFile(jar.toFile())) {
            List<? extends ZipEntry> entries = source.stream()
                    .filter(e -> !e.isDirectory() && e.getName().startsWith(ASSET_ROOT))
                    .sorted(java.util.Comparator.comparing(ZipEntry::getName)).toList();
            if (entries.isEmpty() || entries.size() > MAX_FILES)
                throw new IOException("BuildCraft Lite asset set missing or unreasonably large");
            if (source.getEntry(ASSET_ROOT + "textures/block/wood_item.png") == null
                    || source.getEntry(ASSET_ROOT + "textures/block/stone_item.png") == null
                    || source.getEntry(ASSET_ROOT + "blockstates/wooden_transport_pipe.json") == null
                    || source.getEntry(ASSET_ROOT + "blockstates/stone_transport_pipe.json") == null
                    || source.getEntry(ASSET_ROOT + "blockstates/redstone_engine.json") == null
                    || source.getEntry(ASSET_ROOT + "items/wrench.json") == null
                    || source.getEntry(ASSET_ROOT + "textures/item/wrench.png") == null)
                throw new IOException("BuildCraft Lite is missing required models/textures");

            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp))) {
                String packMeta = "{\"pack\":{\"description\":\"Original BuildCraft pipe art for H.O.W.L. Lite\","
                        + "\"min_format\":[" + CodaTarget.MINECRAFT_RESOURCE_PACK_FORMAT + ",0],"
                        + "\"max_format\":[" + CodaTarget.MINECRAFT_RESOURCE_PACK_FORMAT + ",0]}}";
                put(out, "pack.mcmeta", packMeta.getBytes(StandardCharsets.UTF_8));
                for (ZipEntry entry : entries) {
                    String name = entry.getName();
                    if (name.contains("..") || name.contains("\\") || entry.getSize() > MAX_ASSET_BYTES)
                        throw new IOException("Invalid BuildCraft Lite asset path or size");
                    try (InputStream in = source.getInputStream(entry)) {
                        byte[] data = in.readNBytes(MAX_ASSET_BYTES + 1);
                        if (data.length > MAX_ASSET_BYTES) throw new IOException("Oversized mod asset");
                        put(out, name, data);
                    }
                }
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(marker, sha256(target), StandardCharsets.UTF_8);
            System.out.println("[H.O.W.L.] Original BuildCraft Lite models/textures enabled: " + target);
            return true;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void put(ZipOutputStream out, String path, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(0);
        out.putNextEntry(entry);
        out.write(data);
        out.closeEntry();
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                for (int n; (n = in.read(buffer)) != -1;) digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
    }
}
