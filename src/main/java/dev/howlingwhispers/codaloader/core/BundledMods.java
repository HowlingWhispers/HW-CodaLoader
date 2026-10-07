package dev.howlingwhispers.codaloader.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Keeps the official bundled mod available in both standalone and launcher-managed game roots. */
final class BundledMods {
    private BundledMods() {}
    static void install(Path mods, Path config) throws Exception {
        try (InputStream input = BundledMods.class.getResourceAsStream("/codaloader/mods/hw-essentials.jar")) {
            if (input == null) return; // Development builds may omit the distribution resource.
            byte[] bytes = input.readAllBytes();
            String hash = digest(bytes);
            Path target = mods.resolve("hw-essentials.jar");
            Path marker = config.resolve("codaloader-managed/hw-essentials.sha256");
            if (Files.exists(target)) {
                String actual = digest(Files.readAllBytes(target));
                if (!actual.equals(hash) && (!Files.exists(marker) || !Files.readString(marker).trim().equals(actual)))
                    throw new IOException("hw-essentials.jar was installed or changed manually. Move it aside before using the bundled HW Essentials.");
                if (actual.equals(hash)) {
                    Files.createDirectories(marker.getParent());
                    Files.writeString(marker, hash);
                    return;
                }
            }
            Files.createDirectories(marker.getParent());
            Path staging = Files.createTempFile(mods, "hw-essentials-", ".tmp");
            try {
                Files.write(staging, bytes);
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(marker, hash);
            } finally { Files.deleteIfExists(staging); }
            System.out.println("[CodaLoader] HW Essentials installed in the active game profile.");
        }
    }
    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
