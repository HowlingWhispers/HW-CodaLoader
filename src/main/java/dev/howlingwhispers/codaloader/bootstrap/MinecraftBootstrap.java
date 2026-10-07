package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.CodaTarget;
import dev.howlingwhispers.codaloader.core.MiniJson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Minimal vanilla Minecraft bootstrapper.
 *
 * It resolves Mojang's official version manifest, materializes the exact target client,
 * libraries, natives and assets, then starts vanilla Minecraft. Mod injection comes after
 * this vanilla bootstrap is proven reliable.
 */
public final class MinecraftBootstrap {
    private static final URI VERSION_MANIFEST =
            URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final String ASSET_OBJECT_BASE = "https://resources.download.minecraft.net/";

    private final Path root;
    private final Path runtime;
    private final Path libraries;
    private final Path assets;
    private final Path versions;
    private final Path natives;
    private final Path game;
    private final Path officialMinecraft;
    private final Path basePack;
    private final HttpClient http;

    public MinecraftBootstrap(Path root, Path basePack) {
        this.root = root.toAbsolutePath().normalize();
        this.basePack = basePack.toAbsolutePath().normalize();
        this.runtime = this.root.resolve("runtime");
        this.libraries = runtime.resolve("libraries");
        this.assets = runtime.resolve("assets");
        this.versions = runtime.resolve("versions");
        this.natives = runtime.resolve("natives").resolve(CodaTarget.MINECRAFT_VERSION);
        this.game = this.root;
        this.officialMinecraft = findOfficialMinecraftDirectory();
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public int launch() throws Exception {
        int java = Runtime.version().feature();
        if (java < CodaTarget.MINECRAFT_MINIMUM_JAVA) {
            throw new IllegalStateException(
                    CodaTarget.MINECRAFT_DISPLAY_NAME + " requires Java "
                            + CodaTarget.MINECRAFT_MINIMUM_JAVA + "+; CodaLoader is running on Java " + java);
        }

        Files.createDirectories(libraries);
        Files.createDirectories(assets);
        Files.createDirectories(versions);
        Files.createDirectories(natives);
        Files.createDirectories(game);
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("config"));
        prepareCodaUiPack();
        prepareBrandingPack();
        prepareCustomMusicPack();

        System.out.println("[CodaLoader] Bootstrap target: " + CodaTarget.MINECRAFT_DISPLAY_NAME);
        System.out.println("[CodaLoader] Runtime Java: " + java);
        if (officialMinecraft != null) {
            System.out.println("[CodaLoader] Reuse source: " + officialMinecraft);
        }

        Map<String, Object> manifest = object(readJson(VERSION_MANIFEST));
        Map<String, Object> versionRef = findVersion(manifest, CodaTarget.MINECRAFT_VERSION);
        String versionUrl = string(versionRef, "url");
        String versionSha1 = optionalString(versionRef, "sha1");

        Path versionDir = versions.resolve(CodaTarget.MINECRAFT_VERSION);
        Files.createDirectories(versionDir);
        Path versionJson = versionDir.resolve(CodaTarget.MINECRAFT_VERSION + ".json");
        Path officialVersionJson = officialPath("versions", CodaTarget.MINECRAFT_VERSION,
                CodaTarget.MINECRAFT_VERSION + ".json");
        ensureFile(versionJson, officialVersionJson, URI.create(versionUrl), versionSha1, -1);

        Map<String, Object> version = object(MiniJson.parse(Files.readString(versionJson, StandardCharsets.UTF_8)));
        validateJavaRequirement(version);

        Map<String, Object> downloads = childObject(version, "downloads");
        Map<String, Object> client = childObject(downloads, "client");
        Path clientJar = versionDir.resolve(CodaTarget.MINECRAFT_VERSION + ".jar");
        Path officialClient = officialPath("versions", CodaTarget.MINECRAFT_VERSION,
                CodaTarget.MINECRAFT_VERSION + ".jar");
        ensureFile(clientJar, officialClient, URI.create(string(client, "url")),
                optionalString(client, "sha1"), optionalLong(client, "size", -1));

        prepareAssets(version);
        List<Path> classpath = prepareLibraries(version);
        classpath.add(clientJar);

        Map<String, String> vars = launchVariables(version, classpath);
        List<String> command = new ArrayList<>();
        command.add(currentJavaExecutable().toString());

        Path agentJar = currentCodaLoaderJar();
        command.add("-javaagent:" + agentJar + "=" + root);

        Map<String, Object> arguments = childObject(version, "arguments");
        List<String> jvm = expandArguments(arguments.get("jvm"), vars);
        if (jvm.isEmpty()) {
            command.add("-Djava.library.path=" + natives);
            command.add("-cp");
            command.add(vars.get("classpath"));
        } else {
            command.addAll(jvm);
        }

        appendLoggingConfiguration(version, command);

        command.add(string(version, "mainClass"));
        command.addAll(expandArguments(arguments.get("game"), vars));

        System.out.println("[CodaLoader] Runtime ready.");
        System.out.println("[CodaLoader] Agent: " + agentJar.getFileName());
        System.out.println("[CodaLoader] Launching Minecraft with CodaLoader hooks...");
        System.out.println("[CodaLoader] Game directory: " + game);
        System.out.println("[CodaLoader] Identity: CodaPlayer (offline bootstrap test)");

        Process process = new ProcessBuilder(command)
                .directory(game.toFile())
                .inheritIO()
                .start();
        return process.waitFor();
    }

    private Path currentCodaLoaderJar() throws Exception {
        URI location = MinecraftBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path path = Path.of(location).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".jar")) {
            throw new IllegalStateException("CodaLoader must be launched from its JAR to attach the in-game agent: " + path);
        }
        return path;
    }


    private void prepareCodaUiPack() throws IOException {
        Path pack = game.resolve("resourcepacks").resolve("CodaLoader-UI");
        resetDirectory(pack);

        String packMeta = "{\n"
                + "  \"pack\": {\n"
                + "    \"description\": \"CodaLoader built-in UI sprites\",\n"
                + "    \"min_format\": [" + CodaTarget.MINECRAFT_RESOURCE_PACK_FORMAT + ", 0],\n"
                + "    \"max_format\": [" + CodaTarget.MINECRAFT_RESOURCE_PACK_FORMAT + ", 0]\n"
                + "  }\n"
                + "}\n";
        Files.writeString(pack.resolve("pack.mcmeta"), packMeta, StandardCharsets.UTF_8);

        Path social = pack.resolve("assets").resolve("codaloader").resolve("textures")
                .resolve("gui").resolve("sprites").resolve("social");
        Files.createDirectories(social);
        copyBundledResource("/codaloader/ui/discord.png", social.resolve("discord.png"));
        copyBundledResource("/codaloader/ui/youtube.png", social.resolve("youtube.png"));

        enableGeneratedPack("file/CodaLoader-UI");
        System.out.println("[CodaLoader] CodaLoader UI sprite pack enabled: Discord + YouTube.");
    }

    private void copyBundledResource(String resource, Path target) throws IOException {
        try (InputStream in = MinecraftBootstrap.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Bundled resource missing: " + resource);
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void prepareBrandingPack() throws IOException {
        Path branding = root.resolve("branding");
        Path baseBranding = basePack.resolve("branding");
        Files.createDirectories(branding);

        Path readme = branding.resolve("README.txt");
        if (!Files.exists(readme)) {
            Files.writeString(readme,
                    "Howling Whispers menu branding for CodaLoader.\n"
                            + "Required files:\n"
                            + "  title.png\n"
                            + "  panorama_0.png through panorama_3.png (four horizontal Coda scenes)\n"
                            + "Optional:\n"
                            + "  menu_banner.png (very wide scene used by the Banner Sweep menu scene)\n"
                            + "  sky.png (custom cubemap ceiling; otherwise CodaLoader generates one)\n"
                            + "  floor.png (custom cubemap floor; otherwise CodaLoader generates one)\n"
                            + "  splashes.txt (one custom yellow title message per line)\n"
                            + "When menu_banner.png is absent, CodaLoader builds an experimental wide banner from panorama_0..3.\n"
                            + "The current panorama scene remains available and CML can swap scenes on later title-menu visits.\n",
                    StandardCharsets.UTF_8);
        }

        List<String> requiredNames = new ArrayList<>();
        requiredNames.add("title.png");
        for (int i = 0; i < 4; i++) requiredNames.add("panorama_" + i + ".png");

        List<String> missing = requiredNames.stream()
                .filter(name -> brandingAsset(branding, baseBranding, name) == null)
                .toList();
        if (!missing.isEmpty()) {
            System.out.println("[CodaLoader] Branding unavailable; missing "
                    + String.join(", ", missing) + " from user overrides and CML base pack.");
            return;
        }

        Path pack = game.resolve("resourcepacks").resolve("HowlingWhispers-Branding");
        resetDirectory(pack);

        String packMeta = "{\n"
                + "  \"pack\": {\n"
                + "    \"description\": \"Howling Whispers CodaLoader menu branding\",\n"
                + "    \"min_format\": [" + CodaTarget.MINECRAFT_RESOURCE_PACK_FORMAT + ", 0],\n"
                + "    \"max_format\": [" + CodaTarget.MINECRAFT_RESOURCE_PACK_FORMAT + ", 0]\n"
                + "  }\n"
                + "}\n";
        Files.writeString(pack.resolve("pack.mcmeta"), packMeta, StandardCharsets.UTF_8);

        Path titleDir = pack.resolve("assets").resolve("minecraft").resolve("textures")
                .resolve("gui").resolve("title");
        Files.createDirectories(titleDir);

        writeLogoTexture(brandingAsset(branding, baseBranding, "title.png"),
                titleDir.resolve("minecraft.png"));

        BufferedImage blankEdition = new BufferedImage(256, 64, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(blankEdition, "png", titleDir.resolve("edition.png").toFile());

        Path scenesRoot = MenuSceneManager.scenesRoot(root);
        resetDirectory(scenesRoot);
        prepareClassicPanoramaScene(scenesRoot.resolve("classic-panorama"), branding, baseBranding);
        prepareBannerSweepScene(scenesRoot.resolve("banner-sweep"), branding, baseBranding);

        writeSplashTexts(branding, baseBranding, pack);

        String selected = MenuSceneManager.activateRandom(root, false);
        enableGeneratedPack("file/HowlingWhispers-Branding");
        System.out.println("[CodaLoader] Howling Whispers menu branding enabled with 2 scene modes."
                + " Active scene: " + selected);
    }

    private void prepareClassicPanoramaScene(
            Path scene,
            Path branding,
            Path baseBranding) throws IOException {
        resetDirectory(scene);

        for (int i = 0; i < 4; i++) {
            writeSquareTexture(
                    brandingAsset(branding, baseBranding, "panorama_" + i + ".png"),
                    scene.resolve("panorama_" + i + ".png"),
                    512);
        }

        writeSceneSkyFloor(scene, branding, baseBranding);
    }

    private void prepareBannerSweepScene(
            Path scene,
            Path branding,
            Path baseBranding) throws IOException {
        resetDirectory(scene);

        Path bannerFile = brandingAsset(branding, baseBranding, "menu_banner.png");
        BufferedImage banner;
        if (bannerFile != null) {
            banner = readImage(bannerFile);
            System.out.println("[CodaLoader] Banner Sweep scene uses menu_banner.png.");
        } else {
            banner = buildDerivedMenuBanner(branding, baseBranding);
            System.out.println("[CodaLoader] Banner Sweep scene derived from panorama_0..3.");
        }

        int half = Math.max(1, banner.getWidth() / 2);
        writeBannerFace(banner, 0, half, false, scene.resolve("panorama_0.png"), 512);
        writeBannerFace(banner, half, banner.getWidth(), false, scene.resolve("panorama_1.png"), 512);
        writeBannerFace(banner, half, banner.getWidth(), true, scene.resolve("panorama_2.png"), 512);
        writeBannerFace(banner, 0, half, true, scene.resolve("panorama_3.png"), 512);

        writeSceneSkyFloor(scene, branding, baseBranding);
    }

    private BufferedImage buildDerivedMenuBanner(Path branding, Path baseBranding) throws IOException {
        int tile = 512;
        BufferedImage output = new BufferedImage(tile * 4, tile, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            for (int i = 0; i < 4; i++) {
                BufferedImage source = readImage(
                        brandingAsset(branding, baseBranding, "panorama_" + i + ".png"));
                int side = Math.min(source.getWidth(), source.getHeight());
                int sourceX = (source.getWidth() - side) / 2;
                int sourceY = (source.getHeight() - side) / 2;
                graphics.drawImage(source,
                        i * tile, 0, (i + 1) * tile, tile,
                        sourceX, sourceY, sourceX + side, sourceY + side,
                        null);
            }
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private void writeBannerFace(
            BufferedImage source,
            int sourceX0,
            int sourceX1,
            boolean mirror,
            Path target,
            int size) throws IOException {
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            if (mirror) {
                graphics.drawImage(source,
                        size, 0, 0, size,
                        sourceX0, 0, sourceX1, source.getHeight(),
                        null);
            } else {
                graphics.drawImage(source,
                        0, 0, size, size,
                        sourceX0, 0, sourceX1, source.getHeight(),
                        null);
            }
        } finally {
            graphics.dispose();
        }

        Files.createDirectories(target.getParent());
        ImageIO.write(output, "png", target.toFile());
    }

    private void writeSceneSkyFloor(
            Path scene,
            Path branding,
            Path baseBranding) throws IOException {
        Path customSky = brandingAsset(branding, baseBranding, "sky.png");
        if (customSky != null) {
            writeSquareTexture(customSky, scene.resolve("panorama_4.png"), 512);
        } else {
            writeGeneratedSkyTexture(scene.resolve("panorama_4.png"), 512);
        }

        Path customFloor = brandingAsset(branding, baseBranding, "floor.png");
        if (customFloor != null) {
            writeSquareTexture(customFloor, scene.resolve("panorama_5.png"), 512);
        } else {
            writeGeneratedFloorTexture(scene.resolve("panorama_5.png"), 512);
        }
    }

    private Path brandingAsset(Path customRoot, Path baseRoot, String name) {
        Path custom = customRoot.resolve(name);
        if (Files.isRegularFile(custom)) return custom;
        Path base = baseRoot.resolve(name);
        return Files.isRegularFile(base) ? base : null;
    }

    private void writeGeneratedSkyTexture(Path targetFile, int size) throws IOException {
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < size; y++) {
            double t = y / (double) Math.max(1, size - 1);
            int r = (int) Math.round(8 + 10 * t);
            int g = (int) Math.round(20 + 23 * t);
            int b = (int) Math.round(31 + 35 * t);
            int rgb = new Color(r, g, b).getRGB();
            for (int x = 0; x < size; x++) {
                output.setRGB(x, y, rgb);
            }
        }

        Graphics2D graphics = output.createGraphics();
        try {
            for (int i = 0; i < 46; i++) {
                int x = Math.floorMod(i * 97 + 31, size);
                int y = Math.floorMod(i * 53 + 19, size);
                int glow = 145 + Math.floorMod(i * 17, 90);
                graphics.setColor(new Color(150, 220, 255, glow));
                int dot = i % 7 == 0 ? 2 : 1;
                graphics.fillRect(x, y, dot, dot);
            }

            int moon = Math.max(38, size / 8);
            int moonX = size / 2 - moon / 2;
            int moonY = size / 2 - moon / 2;
            graphics.setColor(new Color(150, 225, 245));
            graphics.fillOval(moonX, moonY, moon, moon);
            graphics.setColor(new Color(58, 91, 109));
            graphics.fillOval(moonX + moon / 3, moonY - moon / 12, moon, moon);
        } finally {
            graphics.dispose();
        }

        Files.createDirectories(targetFile.getParent());
        ImageIO.write(output, "png", targetFile.toFile());
    }

    private void writeGeneratedFloorTexture(Path targetFile, int size) throws IOException {
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setColor(new Color(18, 27, 31));
            graphics.fillRect(0, 0, size, size);

            int tile = Math.max(48, size / 8);
            for (int y = 0; y < size; y += tile) {
                for (int x = 0; x < size; x += tile) {
                    boolean alt = ((x / tile) + (y / tile)) % 2 == 0;
                    graphics.setColor(alt
                            ? new Color(31, 43, 47)
                            : new Color(25, 36, 40));
                    graphics.fillRect(x + 2, y + 2, tile - 4, tile - 4);
                }
            }

            graphics.setColor(new Color(37, 91, 104));
            for (int p = 0; p <= size; p += tile) {
                graphics.fillRect(p, 0, 2, size);
                graphics.fillRect(0, p, size, 2);
            }

            int center = size / 2;
            int glow = Math.max(64, size / 5);
            graphics.setColor(new Color(42, 118, 135));
            graphics.drawOval(center - glow / 2, center - glow / 2, glow, glow);
            graphics.setColor(new Color(72, 167, 188));
            graphics.drawOval(center - glow / 3, center - glow / 3, glow * 2 / 3, glow * 2 / 3);
        } finally {
            graphics.dispose();
        }

        Files.createDirectories(targetFile.getParent());
        ImageIO.write(output, "png", targetFile.toFile());
    }

    private void writeSplashTexts(Path branding, Path baseBranding, Path pack) throws IOException {
        Path custom = branding.resolve("splashes.txt");
        Path base = baseBranding.resolve("splashes.txt");
        String splashes;

        if (Files.isRegularFile(custom)) {
            splashes = Files.readString(custom, StandardCharsets.UTF_8);
            System.out.println("[CodaLoader] Using custom Howling Whispers splash messages.");
        } else if (Files.isRegularFile(base)) {
            splashes = Files.readString(base, StandardCharsets.UTF_8);
            System.out.println("[CodaLoader] Using CML base-pack splash messages.");
        } else {
            splashes = String.join("\n", List.of(
                    "Howling Whispers!",
                    "Pawprint confirmed!",
                    "Coda approves this build!",
                    "Clipboard says ship it!",
                    "Powered by CML!",
                    "Tiny Codas at work!",
                    "Whispers in the chunks!",
                    "Cyan crystals included!",
                    "Main branch only!",
                    "Mods, music, and moonlight!",
                    "One more hook...",
                    "API paws ready!",
                    "Build green. Tail wagging.",
                    "Keep your mods tidy!",
                    "Loaded with extra fluff!",
                    "Discord Coda says hi!",
                    "Browser Coda found the docs!",
                    "Resource pack wrangled!",
                    "Do not feed the stacktrace!",
                    "Bacon-powered debugging!",
                    "Another commit escaped!",
                    "CML means business!",
                    "Worlds within worlds!",
                    "Read the logs!",
                    "Whispers beneath the pines!",
                    "Speculus is listening!",
                    "Orbis remembers!",
                    "Praxis keeps the state!",
                    "Coda was here!",
                    "Hooks before breakfast!",
                    "Compiled with pawprints!",
                    "The wolves are shipping!",
                    "No black fur!",
                    "One loader. Many worlds.",
                    "Moonlight on the clipboard!"
            )) + "\n";
        }

        Path splashesFile = pack.resolve("assets").resolve("minecraft")
                .resolve("texts").resolve("splashes.txt");
        Files.createDirectories(splashesFile.getParent());
        Files.writeString(splashesFile, splashes, StandardCharsets.UTF_8);
    }

    private void writeLogoTexture(Path sourceFile, Path targetFile) throws IOException {
        BufferedImage source = readImage(sourceFile);

        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int alpha = (source.getRGB(x, y) >>> 24) & 0xff;
                if (alpha > 16) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            throw new IOException("Branding title image has no visible pixels: " + sourceFile);
        }

        int cropWidth = maxX - minX + 1;
        int cropHeight = maxY - minY + 1;
        BufferedImage output = new BufferedImage(256, 44, BufferedImage.TYPE_INT_ARGB);

        double scale = Math.min(248.0 / cropWidth, 40.0 / cropHeight);
        int drawWidth = Math.max(1, (int) Math.round(cropWidth * scale));
        int drawHeight = Math.max(1, (int) Math.round(cropHeight * scale));
        int drawX = (output.getWidth() - drawWidth) / 2;
        int drawY = (output.getHeight() - drawHeight) / 2;

        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source,
                    drawX, drawY, drawX + drawWidth, drawY + drawHeight,
                    minX, minY, maxX + 1, maxY + 1,
                    null);
        } finally {
            graphics.dispose();
        }

        Files.createDirectories(targetFile.getParent());
        ImageIO.write(output, "png", targetFile.toFile());
    }

    private void writeSquareTexture(Path sourceFile, Path targetFile, int size) throws IOException {
        BufferedImage source = readImage(sourceFile);
        int side = Math.min(source.getWidth(), source.getHeight());
        int sourceX = (source.getWidth() - side) / 2;
        int sourceY = (source.getHeight() - side) / 2;

        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source,
                    0, 0, size, size,
                    sourceX, sourceY, sourceX + side, sourceY + side,
                    null);
        } finally {
            graphics.dispose();
        }

        Files.createDirectories(targetFile.getParent());
        ImageIO.write(output, "png", targetFile.toFile());
    }

    private BufferedImage readImage(Path sourceFile) throws IOException {
        BufferedImage image = ImageIO.read(sourceFile.toFile());
        if (image == null) {
            throw new IOException("Unsupported or unreadable PNG: " + sourceFile);
        }
        return image;
    }

    private void prepareCustomMusicPack() throws IOException {
        Path musicRoot = root.resolve("music");
        Path legacyDefault = musicRoot.resolve("default");
        Path baseDefault = basePack.resolve("music").resolve("default");
        Path menuMusic = musicRoot.resolve("menu");
        Files.createDirectories(legacyDefault);
        Files.createDirectories(menuMusic);
        Files.createDirectories(baseDefault);

        Path readme = menuMusic.resolve("README.txt");
        if (!Files.exists(readme)) {
            Files.writeString(readme,
                    "Drop .ogg files in this folder. CodaLoader will use them as Minecraft menu music.\n",
                    StandardCharsets.UTF_8);
        }

        List<Path> baseTracks = oggTracks(baseDefault);
        List<Path> legacyTracks = oggTracks(legacyDefault);
        List<Path> userTracks = oggTracks(menuMusic);
        List<Path> tracks = new ArrayList<>(baseTracks.size() + legacyTracks.size() + userTracks.size());
        tracks.addAll(baseTracks);
        tracks.addAll(legacyTracks);
        tracks.addAll(userTracks);

        if (tracks.isEmpty()) {
            System.out.println("[CodaLoader] Menu music unavailable: no CML base-pack or user .ogg tracks.");
            return;
        }

        Path pack = game.resolve("resourcepacks").resolve("CodaLoader-Music");
        resetDirectory(pack);
        Path sounds = pack.resolve("assets").resolve("minecraft").resolve("sounds")
                .resolve("codaloader").resolve("menu");
        Files.createDirectories(sounds);

        String packMeta = "{\n"
                + "  \"pack\": {\n"
                + "    \"description\": \"CodaLoader custom menu music\",\n"
                + "    \"min_format\": [" + CodaTarget.MINECRAFT_RESOURCE_PACK_FORMAT + ", 0],\n"
                + "    \"max_format\": [" + CodaTarget.MINECRAFT_RESOURCE_PACK_FORMAT + ", 0]\n"
                + "  }\n"
                + "}\n";
        Files.writeString(pack.resolve("pack.mcmeta"), packMeta, StandardCharsets.UTF_8);

        StringBuilder soundJson = new StringBuilder();
        soundJson.append("{\n  \"music.menu\": {\n    \"replace\": true,\n    \"sounds\": [\n");
        for (int i = 0; i < tracks.size(); i++) {
            String generated = String.format(Locale.ROOT, "track_%03d", i + 1);
            Files.copy(tracks.get(i), sounds.resolve(generated + ".ogg"), StandardCopyOption.REPLACE_EXISTING);
            soundJson.append("      {\"name\":\"codaloader/menu/")
                    .append(generated)
                    .append("\",\"stream\":true}");
            if (i + 1 < tracks.size()) soundJson.append(',');
            soundJson.append('\n');
        }
        soundJson.append("    ]\n  }\n}\n");

        Path soundsJson = pack.resolve("assets").resolve("minecraft").resolve("sounds.json");
        Files.createDirectories(soundsJson.getParent());
        Files.writeString(soundsJson, soundJson.toString(), StandardCharsets.UTF_8);
        enableGeneratedPack("file/CodaLoader-Music");

        System.out.println("[CodaLoader] Menu music enabled: "
                + baseTracks.size() + " base-pack + "
                + legacyTracks.size() + " legacy-default + "
                + userTracks.size() + " user track(s)");
    }

    private List<Path> oggTracks(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                    .sorted()
                    .toList();
        }
    }

    private void enableGeneratedPack(String resourcePackId) throws IOException {
        Path options = game.resolve("options.txt");
        List<String> lines = Files.exists(options)
                ? new ArrayList<>(Files.readAllLines(options, StandardCharsets.UTF_8))
                : new ArrayList<>();

        String packId = "\"" + resourcePackId + "\"";
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith("resourcePacks:")) continue;
            found = true;
            if (!line.contains(packId)) {
                int end = line.lastIndexOf(']');
                if (end >= 0) {
                    String before = line.substring(0, end);
                    if (!before.endsWith("[")) before += ",";
                    lines.set(i, before + packId + "]");
                } else {
                    lines.set(i, "resourcePacks:[\"vanilla\"," + packId + "]");
                }
            }
        }
        if (!found) {
            lines.add("resourcePacks:[\"vanilla\"," + packId + "]");
        }
        Files.write(options, lines, StandardCharsets.UTF_8);
    }

    private Map<String, Object> findVersion(Map<String, Object> manifest, String id) {
        Object raw = manifest.get("versions");
        if (!(raw instanceof List<?> list)) {
            throw new IllegalStateException("Mojang version manifest did not contain a versions list");
        }
        for (Object item : list) {
            Map<String, Object> entry = object(item);
            if (id.equals(optionalString(entry, "id"))) {
                return entry;
            }
        }
        String latest = "?";
        Object latestRaw = manifest.get("latest");
        if (latestRaw instanceof Map<?, ?> latestMap && latestMap.get("snapshot") instanceof String s) {
            latest = s;
        }
        throw new IllegalStateException(
                "Minecraft target '" + id + "' is not present in Mojang's version manifest. "
                        + "Latest snapshot reported by Mojang: " + latest);
    }

    private void validateJavaRequirement(Map<String, Object> version) {
        Object javaRaw = version.get("javaVersion");
        if (!(javaRaw instanceof Map<?, ?> map)) return;
        Object majorRaw = map.get("majorVersion");
        if (!(majorRaw instanceof Number n)) return;
        int required = n.intValue();
        int running = Runtime.version().feature();
        System.out.println("[CodaLoader] Minecraft metadata requires Java " + required + "+");
        if (running < required) {
            throw new IllegalStateException(
                    "Minecraft requires Java " + required + "+ but CodaLoader is running on Java " + running);
        }
    }

    private void prepareAssets(Map<String, Object> version) throws Exception {
        Map<String, Object> assetIndex = childObject(version, "assetIndex");
        String assetId = string(assetIndex, "id");
        Path indexes = assets.resolve("indexes");
        Files.createDirectories(indexes);
        Path indexFile = indexes.resolve(assetId + ".json");
        Path officialIndex = officialPath("assets", "indexes", assetId + ".json");

        ensureFile(indexFile, officialIndex, URI.create(string(assetIndex, "url")),
                optionalString(assetIndex, "sha1"), optionalLong(assetIndex, "size", -1));

        Map<String, Object> index = object(MiniJson.parse(Files.readString(indexFile, StandardCharsets.UTF_8)));
        Map<String, Object> objects = childObject(index, "objects");
        int total = objects.size();
        int done = 0;

        System.out.println("[CodaLoader] Preparing " + total + " asset objects...");
        for (Object raw : objects.values()) {
            Map<String, Object> asset = object(raw);
            String hash = string(asset, "hash");
            String prefix = hash.substring(0, 2);
            Path target = assets.resolve("objects").resolve(prefix).resolve(hash);
            Path source = officialPath("assets", "objects", prefix, hash);
            URI url = URI.create(ASSET_OBJECT_BASE + prefix + "/" + hash);
            ensureFile(target, source, url, hash, optionalLong(asset, "size", -1));
            done++;
            if (done % 250 == 0 || done == total) {
                System.out.println("[CodaLoader] Assets: " + done + "/" + total);
            }
        }
    }

    private List<Path> prepareLibraries(Map<String, Object> version) throws Exception {
        Object rawLibraries = version.get("libraries");
        if (!(rawLibraries instanceof List<?> list)) {
            throw new IllegalStateException("Version metadata did not contain libraries");
        }

        resetDirectory(natives);
        List<Path> classpath = new ArrayList<>();
        int count = 0;

        for (Object raw : list) {
            Map<String, Object> library = object(raw);
            if (!allowed(library.get("rules"))) continue;

            Map<String, Object> downloads = childObject(library, "downloads");
            Object artifactRaw = downloads.get("artifact");
            if (artifactRaw instanceof Map<?, ?>) {
                Map<String, Object> artifact = object(artifactRaw);
                String path = string(artifact, "path");
                Path target = libraries.resolve(path);
                Path source = officialPath("libraries", path.split("/"));
                ensureFile(target, source, URI.create(string(artifact, "url")),
                        optionalString(artifact, "sha1"), optionalLong(artifact, "size", -1));
                classpath.add(target);
                count++;
            }

            Object nativesRaw = library.get("natives");
            if (nativesRaw instanceof Map<?, ?> nativesMap) {
                Object classifierPattern = nativesMap.get(osName());
                if (classifierPattern instanceof String pattern) {
                    String classifier = pattern.replace("${arch}", is64Bit() ? "64" : "32");
                    Map<String, Object> classifiers = childObject(downloads, "classifiers");
                    Object classifierRaw = classifiers.get(classifier);
                    if (classifierRaw instanceof Map<?, ?>) {
                        Map<String, Object> nativeArtifact = object(classifierRaw);
                        String path = string(nativeArtifact, "path");
                        Path target = libraries.resolve(path);
                        Path source = officialPath("libraries", path.split("/"));
                        ensureFile(target, source, URI.create(string(nativeArtifact, "url")),
                                optionalString(nativeArtifact, "sha1"), optionalLong(nativeArtifact, "size", -1));
                        extractNativeJar(target, library);
                    }
                }
            }
        }

        System.out.println("[CodaLoader] Libraries ready: " + count + " classpath entries");
        return classpath;
    }

    private void extractNativeJar(Path jarPath, Map<String, Object> library) throws IOException {
        List<String> excludes = new ArrayList<>();
        Object extractRaw = library.get("extract");
        if (extractRaw instanceof Map<?, ?> extractMap) {
            Object excludeRaw = extractMap.get("exclude");
            if (excludeRaw instanceof List<?> list) {
                for (Object item : list) if (item instanceof String s) excludes.add(s);
            }
        }
        excludes.add("META-INF/");

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                boolean excluded = excludes.stream().anyMatch(name::startsWith);
                if (excluded) continue;

                Path output = natives.resolve(name).normalize();
                if (!output.startsWith(natives)) {
                    throw new IOException("Refusing native path outside extraction directory: " + name);
                }
                Files.createDirectories(output.getParent());
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private Map<String, String> launchVariables(Map<String, Object> version, List<Path> classpath) {
        String player = "CodaPlayer";
        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + player).getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String assetIndexName = string(childObject(version, "assetIndex"), "id");

        Map<String, String> vars = new HashMap<>();
        vars.put("natives_directory", natives.toString());
        vars.put("launcher_name", "CodaLoader");
        vars.put("launcher_version", CodaTarget.LOADER_VERSION);
        vars.put("classpath", String.join(File.pathSeparator,
                classpath.stream().map(Path::toString).toList()));
        vars.put("classpath_separator", File.pathSeparator);
        vars.put("library_directory", libraries.toString());
        vars.put("auth_player_name", player);
        vars.put("version_name", CodaTarget.MINECRAFT_VERSION);
        vars.put("game_directory", game.toString());
        vars.put("assets_root", assets.toString());
        vars.put("assets_index_name", assetIndexName);
        vars.put("game_assets", assets.toString());
        vars.put("auth_uuid", uuid);
        vars.put("auth_access_token", "0");
        vars.put("clientid", "");
        vars.put("auth_xuid", "");
        vars.put("user_type", "legacy");
        vars.put("version_type", optionalString(version, "type") == null ? "snapshot" : optionalString(version, "type"));
        vars.put("user_properties", "{}");
        vars.put("resolution_width", "1280");
        vars.put("resolution_height", "720");
        vars.put("quickPlayPath", "");
        return vars;
    }

    private List<String> expandArguments(Object raw, Map<String, String> vars) {
        List<String> result = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return result;

        for (Object item : list) {
            if (item instanceof String s) {
                addExpanded(result, s, vars);
                continue;
            }
            if (!(item instanceof Map<?, ?>)) continue;
            Map<String, Object> arg = object(item);
            if (!allowed(arg.get("rules"))) continue;
            Object value = arg.get("value");
            if (value instanceof String s) {
                addExpanded(result, s, vars);
            } else if (value instanceof List<?> values) {
                for (Object part : values) {
                    if (part instanceof String s) addExpanded(result, s, vars);
                }
            }
        }
        return result;
    }

    private void addExpanded(List<String> out, String raw, Map<String, String> vars) {
        String value = raw;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            value = value.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        if (value.contains("${")) {
            System.out.println("[CodaLoader] Skipping argument with unresolved placeholder: " + raw);
            return;
        }
        if (!value.isEmpty()) out.add(value);
    }

    private boolean allowed(Object rawRules) {
        if (!(rawRules instanceof List<?> rules)) return true;
        boolean allowed = false;
        for (Object raw : rules) {
            if (!(raw instanceof Map<?, ?>)) continue;
            Map<String, Object> rule = object(raw);
            if (!ruleMatches(rule)) continue;
            allowed = "allow".equals(optionalString(rule, "action"));
        }
        return allowed;
    }

    private boolean ruleMatches(Map<String, Object> rule) {
        Object osRaw = rule.get("os");
        if (osRaw instanceof Map<?, ?>) {
            Map<String, Object> os = object(osRaw);
            String name = optionalString(os, "name");
            if (name != null && !name.equals(osName())) return false;
            String arch = optionalString(os, "arch");
            if (arch != null && !Pattern.compile(arch).matcher(System.getProperty("os.arch", "")).matches()) return false;
            String version = optionalString(os, "version");
            if (version != null && !Pattern.compile(version).matcher(System.getProperty("os.version", "")).matches()) return false;
        }

        Object featuresRaw = rule.get("features");
        if (featuresRaw instanceof Map<?, ?> features) {
            for (Map.Entry<?, ?> entry : features.entrySet()) {
                if (!(entry.getKey() instanceof String)) continue;
                boolean expected = Boolean.TRUE.equals(entry.getValue());
                boolean actual = false;
                if (actual != expected) return false;
            }
        }
        return true;
    }

    private void appendLoggingConfiguration(Map<String, Object> version, List<String> command) throws Exception {
        Object loggingRaw = version.get("logging");
        if (!(loggingRaw instanceof Map<?, ?> loggingMap)) return;
        Object clientRaw = loggingMap.get("client");
        if (!(clientRaw instanceof Map<?, ?>)) return;
        Map<String, Object> client = object(clientRaw);
        Object fileRaw = client.get("file");
        String argument = optionalString(client, "argument");
        if (!(fileRaw instanceof Map<?, ?>) || argument == null) return;

        Map<String, Object> file = object(fileRaw);
        Path logConfigs = runtime.resolve("log_configs");
        Files.createDirectories(logConfigs);
        Path target = logConfigs.resolve(string(file, "id"));
        Path official = officialPath("assets", "log_configs", string(file, "id"));
        ensureFile(target, official, URI.create(string(file, "url")),
                optionalString(file, "sha1"), optionalLong(file, "size", -1));
        command.add(argument.replace("${path}", target.toString()));
    }

    private void ensureFile(Path target, Path officialSource, URI url, String sha1, long size) throws Exception {
        if (valid(target, sha1, size)) return;

        Files.createDirectories(target.getParent());
        if (officialSource != null && valid(officialSource, sha1, size)) {
            try {
                Files.copy(officialSource, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (FileSystemException ex) {
                // Fall through to network download when Store/Xbox files cannot be copied.
            }
        }

        Path temp = target.resolveSibling(target.getFileName() + ".part");
        Files.deleteIfExists(temp);
        System.out.println("[CodaLoader] Downloading " + target.getFileName());
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "CodaLoader/" + CodaTarget.LOADER_VERSION)
                .GET()
                .build();
        HttpResponse<Path> response = http.send(request,
                HttpResponse.BodyHandlers.ofFile(temp));
        if (response.statusCode() / 100 != 2) {
            Files.deleteIfExists(temp);
            throw new IOException("HTTP " + response.statusCode() + " downloading " + url);
        }
        if (!valid(temp, sha1, size)) {
            Files.deleteIfExists(temp);
            throw new IOException("Downloaded file failed size/SHA-1 verification: " + url);
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean valid(Path file, String sha1, long size) throws Exception {
        if (file == null || !Files.isRegularFile(file)) return false;
        if (size >= 0 && Files.size(file) != size) return false;
        if (sha1 == null || sha1.isBlank()) return true;
        return sha1.equalsIgnoreCase(sha1(file));
    }

    private String sha1(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[1024 * 64];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private Object readJson(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "CodaLoader/" + CodaTarget.LOADER_VERSION)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode() + " reading " + uri);
        }
        return MiniJson.parse(response.body());
    }

    private Path currentJavaExecutable() {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        Path windows = bin.resolve("java.exe");
        if (Files.isRegularFile(windows)) return windows;
        return bin.resolve("java");
    }

    private Path findOfficialMinecraftDirectory() {
        String os = osName();
        Path candidate;
        if ("windows".equals(os)) {
            String appData = System.getenv("APPDATA");
            candidate = appData == null ? null : Path.of(appData, ".minecraft");
        } else if ("osx".equals(os)) {
            candidate = Path.of(System.getProperty("user.home"), "Library", "Application Support", "minecraft");
        } else {
            candidate = Path.of(System.getProperty("user.home"), ".minecraft");
        }
        return candidate != null && Files.isDirectory(candidate) ? candidate : null;
    }

    private Path officialPath(String first, String... rest) {
        if (officialMinecraft == null) return null;
        Path path = officialMinecraft.resolve(first);
        for (String part : rest) path = path.resolve(part);
        return path;
    }

    private String osName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }

    private boolean is64Bit() {
        return System.getProperty("os.arch", "").contains("64");
    }

    private void resetDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                for (Path p : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                    if (!p.equals(dir)) Files.deleteIfExists(p);
                }
            }
        }
        Files.createDirectories(dir);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Expected JSON object but got " + (value == null ? "null" : value.getClass()));
        }
        return (Map<String, Object>) map;
    }

    private static Map<String, Object> childObject(Map<String, Object> parent, String key) {
        return object(parent.get(key));
    }

    private static String string(Map<String, Object> map, String key) {
        String value = optionalString(map, key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing JSON string: " + key);
        }
        return value;
    }

    private static String optionalString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof String s ? s : null;
    }

    private static long optionalLong(Map<String, Object> map, String key, long fallback) {
        Object value = map.get(key);
        return value instanceof Number n ? n.longValue() : fallback;
    }
}
