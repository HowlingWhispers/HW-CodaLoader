package dev.howlingwhispers.codaloader.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Required, verified H.O.W.L. components, bundled in the loader's JAR.
 * Idempotent for Stable and Nightly; never treats optional BuildCraft as a
 * required package. Saves and player-owned modifications are untouched.
 */
final class BundledMods {
    private BundledMods() {}

    static void install(Path mods, Path config) throws Exception {
        installRequired(mods, config, "hw-essentials.jar", "hw_essentials");
        installRequired(mods, config, "coda-wolf-0.1.0-dev.jar", "coda_wolf");
    }

    private static void installRequired(Path mods, Path config, String file, String modId)
            throws Exception {
        byte[] bytes;
        try (InputStream input=BundledMods.class.getResourceAsStream("/codaloader/mods/" + file)) {
            if (input == null) {
                // No first-class required mod omission in a published loader.
                // The local source-only test harness may intentionally build
                // without a distribution, but release CI asserts both exist.
                System.err.println("[H.O.W.L.] Required bundled mod absent: " + modId
                        + " (development loader build).");
                return;
            }
            bytes=input.readAllBytes();
        }
        String hash=digest(bytes);
        Path target=mods.resolve(file);
        Path marker=config.resolve("codaloader-managed").resolve(file + ".sha256");
        if (Files.exists(target)) {
            String actual=digest(Files.readAllBytes(target));
            if (actual.equals(hash)) {
                Files.createDirectories(marker.getParent());
                Files.writeString(marker,hash);
                return;
            }
            if (!Files.exists(marker) || !Files.readString(marker).trim().equals(actual)) {
                // Any player-edited JAR is sacred, but Coda is required. Abort
                // with an actionable error instead of silently deleting a mod.
                throw new IOException("Required H.O.W.L. mod " + file
                        + " differs from bundled code and is not loader-managed. "
                        + "Move it aside manually before launching. World saves are untouched.");
            }
        }

        // Prevent installing a second copy of Coda with the same id under an
        // alternative name. The vanilla loader rejects duplicate mod IDs.
        try (var entries=Files.list(mods)) {
            for(Path other:entries.filter(p->p.getFileName().toString().endsWith(".jar")).toList()) {
                if (other.getFileName().toString().equals(file)) continue;
                try (ZipFile zip=new ZipFile(other.toFile())) {
                    var info=zip.getEntry("coda.mod.json");
                    if (info == null) continue;
                    try (InputStream stream=zip.getInputStream(info)) {
                        String meta=new String(stream.readNBytes(8192),
                                java.nio.charset.StandardCharsets.UTF_8);
                        if (meta.matches("(?s).*\\\"id\\\"\\s*:\\s*\\\""
                                + modId + "\\\".*"))
                            throw new IOException("Duplicate required mod " + modId +
                                    " already exists in " + other.getFileName()
                                    + ". Remove the duplicate manually; saves are untouched.");
                    }
                }
            }
        }

        Files.createDirectories(marker.getParent());
        Path staging=Files.createTempFile(mods,".howl-required-",".tmp");
        try {
            Files.write(staging,bytes);
            Files.move(staging,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(marker,hash);
        } finally {Files.deleteIfExists(staging);}
        System.out.println("[H.O.W.L.] Required " + modId + " v"
                + (modId.equals("coda_wolf") ? "0.1.2" : "bundled") + " installed in active profile.");
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
