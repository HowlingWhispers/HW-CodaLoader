package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.CodaTarget;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/**
 * Builds a Coda-only custom wolf texture pack from the EXACT vanilla wolf UV
 * atlas embedded in Mojang's running client. No generic wolf assets are
 * overwritten. Snowy wolf art is used solely as a UV/alpha template.
 *
 * Initial 64x32 palette treatment; editable handcrafted fur markings can
 * replace these generated sprites later without altering the renderer seam.
 */
public final class CodaWolfResourceInstaller {
    static final String PACK = "HOWL-Coda-Wolf.zip";
    private static final String MOD = "coda-wolf-0.1.0-dev.jar";
    private static final String TEMPLATE = "/assets/minecraft/textures/entity/wolf/";
    private static final String DEST = "assets/codawolf/textures/entity/";

    private CodaWolfResourceInstaller() {}

    /** True means the skin was installed and is safe to enable. */
    static boolean prepare(Path game) throws IOException {
        Path installed = game.resolve("mods").resolve(MOD);
        if (!Files.isRegularFile(installed)) return false;
        try (ZipFile jar = new ZipFile(installed.toFile())) {
            ZipEntry manifest = jar.getEntry("coda.mod.json");
            if (manifest == null || manifest.getSize() > 8192)
                return false;
            String metadata;
            try (InputStream input = jar.getInputStream(manifest)) {
                metadata = new String(input.readNBytes(8193), StandardCharsets.UTF_8);
            }
            if (!metadata.matches("(?s).*\\\"id\\\"\\s*:\\s*\\\"coda_wolf\\\".*"))
                return false;
        }
        try (InputStream tame = CodaWolfResourceInstaller.class.getResourceAsStream(
                    TEMPLATE + "wolf_snowy_tame.png");
             InputStream angry = CodaWolfResourceInstaller.class.getResourceAsStream(
                    TEMPLATE + "wolf_snowy_angry.png")) {
            // A bootstrap JVM without the client JAR can prepare this pack on
            // the in-game agent pass, before Minecraft creates its resource manager.
            if (tame == null || angry == null) {
                System.out.println("[H.O.W.L.] Coda fur waiting for real Minecraft texture resources.");
                return false;
            }
            BufferedImage tameImage = recolor(tame, false);
            BufferedImage angryImage = recolor(angry, true);
            Path root = game.resolve("resourcepacks");
            Files.createDirectories(root);
            Path target = root.resolve(PACK);
            Path marker = root.resolve(PACK + ".sha256");
            Path temp = Files.createTempFile(root, ".howl-coda-fur-", ".zip");
            try {
                try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temp))) {
                    write(output, "pack.mcmeta",
                        ("{\"pack\":{\"description\":\"Coda Companion exclusive icy-white wolf fur\","
                            + "\"min_format\":[" + CodaTarget.MINECRAFT_RESOURCE_PACK_FORMAT + ",0],"
                            + "\"max_format\":[" + CodaTarget.MINECRAFT_RESOURCE_PACK_FORMAT + ",0]}}")
                            .getBytes(StandardCharsets.UTF_8));
                    writePng(output, DEST + "coda_tame.png", tameImage);
                    writePng(output, DEST + "coda_angry.png", angryImage);
                }
                if (Files.exists(target)) {
                    if (!Files.isRegularFile(marker) ||
                            !sha256(target).equals(Files.readString(marker).trim())) {
                        System.err.println("[H.O.W.L.] Custom Coda pack edited by player; preserving changes.");
                        return false;
                    }
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(marker, sha256(target), StandardCharsets.UTF_8);
                System.out.println("[H.O.W.L.] Exclusive Coda fur pack prepared: " + target);
                return true;
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    /**
     * Preserve all 64x32 vanilla UV bounds and alpha pixels. Mapping original
     * luminance to icy white/cyan reproduces the wolf's shape and head/body
     * projections without accepting an unaligned image-generation atlas.
     */
    static BufferedImage recolor(InputStream original, boolean angry) throws IOException {
        BufferedImage source = ImageIO.read(original);
        if (source == null || source.getWidth() != 64 || source.getHeight() != 32)
            throw new IOException("Unexpected original Snapshot 3 wolf UV dimensions");
        BufferedImage result = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 255;
                if (alpha == 0) continue;
                int red = (argb >>> 16) & 255, green = (argb >>> 8) & 255, blue = argb & 255;
                int lum = (red * 54 + green * 183 + blue * 19) >> 8;
                int r, g, b;
                if (lum < 65) {
                    // Eyes and nose retain visible dark slate, not black fur.
                    r = angry ? 68 : 28; g = angry ? 46 : 60; b = angry ? 90 : 99;
                } else if (lum < 165) {
                    int t = lum - 65;
                    r = 83 + t / 2;
                    g = 171 + t * 2 / 3;
                    b = 209 + t / 3;
                } else {
                    int t = lum - 165;
                    r = 193 + t * 62 / 90;
                    g = 221 + t * 34 / 90;
                    b = 230 + t * 25 / 90;
                }
                result.setRGB(x, y, (alpha << 24)
                        | (Math.min(255, r) << 16)
                        | (Math.min(255, g) << 8)
                        | Math.min(255, b));
            }
        }
        return result;
    }

    private static void writePng(ZipOutputStream zip, String name, BufferedImage image)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        if (!ImageIO.write(image, "png", zip))
            throw new IOException("No PNG writer in runtime");
        zip.closeEntry();
    }

    private static void write(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] bytes = new byte[8192];
                for (int n; (n = in.read(bytes)) != -1;) digest.update(bytes, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
    }
}
