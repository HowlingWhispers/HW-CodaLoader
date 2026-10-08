package dev.howlingwhispers.codaloader.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Safe, cross-platform provisioning for the required H.O.W.L. worldgen pack.
 *
 * IMPORTANT: An actual Minecraft new-world hook must call this BEFORE the first
 * data-pack discovery. Merely placing a ZIP in datapacks does not enable it.
 * This class deliberately does not edit level.dat or migrate existing worlds.
 */
public final class RequiredWorldPacks {
    private static final int MAX_ZIP_BYTES = 8 * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_BYTES = 32L * 1024 * 1024;
    private static final String DESTINATION = "hw-quiet-underground.zip";
    private static final Set<String> REQUIRED_ENTRIES = Set.of(
            "pack.mcmeta",
            "data/minecraft/worldgen/carver/canyon.json",
            "data/minecraft/worldgen/carver/cave.json",
            "data/minecraft/worldgen/carver/cave_extra_underground.json",
            "data/minecraft/worldgen/density_function/overworld/final_density.json");

    public enum Result { INSTALLED, ALREADY_PRESENT, EXISTING_WORLD }

    private RequiredWorldPacks() {}

    public static Result prepareNewWorld(Path worldDir, Path archive, String expectedSha256) throws IOException {
        if (worldDir == null || archive == null || expectedSha256 == null)
            throw new IllegalArgumentException("World path, archive and expected hash are required");
        if (Files.isSymbolicLink(worldDir) || !Files.isDirectory(worldDir, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Refusing missing or symbolic-link world directory");
        if (has(worldDir.resolve("level.dat")) || has(worldDir.resolve("level.dat_old")))
            return Result.EXISTING_WORLD;
        for (String name : Set.of("region", "entities", "poi", "DIM-1", "DIM1")) {
            if (has(worldDir.resolve(name)))
                throw new IOException("Refusing world with generated data: " + name);
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(worldDir)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (!name.equals("session.lock") && !name.equals("datapacks"))
                    throw new IOException("Refusing non-empty world-creation directory: " + name);
            }
        }

        // Fully validate the source BEFORE creating or touching anything under the world.
        byte[] body = validateBundle(archive, expectedSha256);

        Path datapacks = worldDir.resolve("datapacks");
        if (Files.isSymbolicLink(datapacks))
            throw new IOException("Refusing symbolic-link datapacks folder");
        if (has(datapacks) && !Files.isDirectory(datapacks, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("World datapacks location is not a directory");

        Path target = datapacks.resolve(DESTINATION);
        if (Files.isSymbolicLink(target))
            throw new IOException("Refusing symbolic-link worldgen target");
        if (has(target)) {
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    && Files.size(target) == body.length
                    && MessageDigest.isEqual(digest(target), digest(body)))
                return Result.ALREADY_PRESENT;
            throw new IOException("Different pack already exists; refusing to overwrite world data");
        }

        Files.createDirectories(datapacks);
        Path staging = Files.createTempFile(datapacks, ".howl-worldgen-", ".tmp");
        try {
            // Durably write to a file on the SAME filesystem as destination.
            try (FileChannel out = FileChannel.open(staging, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(body);
                while (buffer.hasRemaining()) out.write(buffer);
                out.force(true);
            }
            // Hard-link creation is atomic and strictly fails if the target already exists.
            // It avoids the replace-on-collision behavior of some ATOMIC_MOVE providers.
            Files.createLink(target, staging);
            return Result.INSTALLED;
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    private static boolean has(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    /** Check checksum, bounded ZIP contents and exact Snapshot 3 pack format 123. */
    private static byte[] validateBundle(Path archive, String expectedSha) throws IOException {
        if (Files.isSymbolicLink(archive) || !Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Required worldgen bundle is missing or a symbolic link");
        long size = Files.size(archive);
        if (size < 1 || size > MAX_ZIP_BYTES)
            throw new IOException("Required worldgen bundle size is invalid");
        if (!expectedSha.matches("(?i)[0-9a-f]{64}"))
            throw new IOException("Required worldgen SHA-256 is not configured");
        byte[] bytes = Files.readAllBytes(archive);
        if (!hex(digest(bytes)).equalsIgnoreCase(expectedSha))
            throw new IOException("Required worldgen SHA-256 mismatch");

        Set<String> found = new HashSet<>();
        long decompressed = 0;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            int count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                count++;
                if (count > 2048 || !found.add(entry.getName())
                        || entry.getName().startsWith("/") || entry.getName().contains(".."))
                    throw new IOException("Invalid worldgen ZIP layout");
                long length = entry.getSize();
                if (length < 0 || length > MAX_UNCOMPRESSED_BYTES)
                    throw new IOException("Unbounded worldgen ZIP entry: " + entry.getName());
                decompressed += length;
                if (decompressed > MAX_UNCOMPRESSED_BYTES)
                    throw new IOException("Worldgen ZIP exceeds uncompressed size limit");
            }
            if (!found.containsAll(REQUIRED_ENTRIES))
                throw new IOException("Worldgen bundle is missing required Snapshot 3 definitions");
            ZipEntry meta = zip.getEntry("pack.mcmeta");
            if (meta.getSize() > 4096)
                throw new IOException("Invalid oversized worldgen metadata");
            String json;
            try (InputStream in = zip.getInputStream(meta)) {
                json = new String(in.readNBytes(4097), java.nio.charset.StandardCharsets.UTF_8);
            }
            if (json.length() > 4096)
                throw new IOException("Invalid oversized worldgen metadata");
            Object parsed;
            try {
                parsed = MiniJson.parse(json);
            } catch (RuntimeException error) {
                throw new IOException("Invalid worldgen pack.mcmeta JSON", error);
            }
            if (!(parsed instanceof Map<?, ?> root) || !(root.get("pack") instanceof Map<?, ?> pack)
                    || !(pack.get("min_format") instanceof Number minimum)
                    || !(pack.get("max_format") instanceof Number maximum)
                    || minimum.intValue() != 123 || maximum.intValue() != 123)
                throw new IOException("Required worldgen pack is not Snapshot 3 format 123");
        }
        return bytes;
    }

    private static byte[] digest(Path path) throws IOException {
        try (InputStream stream = Files.newInputStream(path)) {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) sha.update(buffer, 0, read);
            return sha.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String hex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
