package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.CodaTarget;
import dev.howlingwhispers.codaloader.core.MiniJson;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Public GitHub Releases updater for the Windows CodaLoader bundle.
 *
 * The running JAR never overwrites itself. It stages a verified release, starts a temporary
 * batch updater, exits with UPDATE_EXIT_CODE, and the batch replaces managed files and relaunches.
 */
public final class UpdateManager {
    public static final int UPDATE_EXIT_CODE = 42;

    private static final URI RELEASES =
            URI.create("https://api.github.com/repos/HowlingWhispers/HW-CodaLoader/releases?per_page=10");
    private static final String MANIFEST_ASSET = "update-manifest.json";
    private static final String[] MANAGED = {
            "CodaLoader.jar",
            "Launch-CodaLoader.bat",
            "run/mods/hello-coda.jar"
    };

    private UpdateManager() {}

    public static boolean checkAndStage(Path runRoot) {
        try {
            if (!isWindows()) {
                return false;
            }

            Path installRoot = installationRoot();
            if (installRoot == null) {
                System.out.println("[CodaLoader] Update check skipped outside packaged JAR.");
                return false;
            }

            String mode = updateMode(runRoot);
            if ("off".equals(mode)) {
                return false;
            }

            HttpClient http = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            Release release = latestRelease(http);
            if (release == null) {
                return false;
            }

            Asset manifestAsset = release.asset(MANIFEST_ASSET);
            if (manifestAsset == null) {
                System.out.println("[CodaLoader] Latest release has no update manifest yet.");
                return false;
            }

            Manifest manifest = readManifest(http, manifestAsset.url());
            boolean newer = compareVersions(manifest.version(), CodaTarget.LOADER_VERSION) > 0;
            boolean repair = !managedFilesMatch(installRoot, manifest.files());

            if (!newer && !repair) {
                System.out.println("[CodaLoader] Update check: current (" + CodaTarget.LOADER_VERSION + ").");
                return false;
            }

            String reason = newer
                    ? CodaTarget.LOADER_VERSION + " -> " + manifest.version()
                    : "managed files differ from release";
            System.out.println("[CodaLoader] Update available: " + reason);

            if ("notify".equals(mode)) {
                System.out.println("[CodaLoader] Update mode is notify. Download it from: " + release.htmlUrl());
                return false;
            }

            Asset bundle = release.asset(manifest.bundle());
            if (bundle == null) {
                throw new IOException("Release is missing bundle asset " + manifest.bundle());
            }

            stageAndRelaunch(http, installRoot, runRoot, bundle, manifest);
            return true;
        } catch (Exception ex) {
            System.err.println("[CodaLoader] Update check failed; continuing with installed build: " + ex.getMessage());
            return false;
        }
    }

    private static void stageAndRelaunch(
            HttpClient http,
            Path installRoot,
            Path runRoot,
            Asset bundle,
            Manifest manifest) throws Exception {

        Path update = runRoot.toAbsolutePath().normalize().resolve("update");
        Path staging = update.resolve("staging");
        deleteTree(staging);
        Files.createDirectories(staging);

        Path zip = update.resolve(manifest.bundle());
        Files.createDirectories(update);
        download(http, bundle.url(), zip);

        String actual = sha256(zip);
        if (!actual.equalsIgnoreCase(manifest.sha256())) {
            Files.deleteIfExists(zip);
            throw new IOException("Bundle SHA-256 mismatch");
        }

        unzip(zip, staging);

        for (String managed : MANAGED) {
            Path staged = staging.resolve(managed).normalize();
            if (!staged.startsWith(staging) || !Files.isRegularFile(staged)) {
                throw new IOException("Update bundle is missing " + managed);
            }
            String expected = manifest.files().get(managed);
            if (expected == null || !sha256(staged).equalsIgnoreCase(expected)) {
                throw new IOException("Managed file failed SHA-256 verification: " + managed);
            }
        }

        Path script = update.resolve("apply-update.bat");
        Files.writeString(script, updaterScript(
                ProcessHandle.current().pid(),
                installRoot,
                staging,
                update), StandardCharsets.UTF_8);

        System.out.println("[CodaLoader] Update verified and staged.");
        System.out.println("[CodaLoader] Restarting into " + manifest.version() + "...");

        new ProcessBuilder(
                "cmd.exe",
                "/c",
                "start \"\" \"" + script.toAbsolutePath() + "\"")
                .directory(installRoot.toFile())
                .start();
    }

    private static String updaterScript(long pid, Path installRoot, Path staging, Path update) {
        String root = batPath(installRoot);
        String stage = batPath(staging);
        String updateDir = batPath(update);

        return "@echo off\r\n"
                + "setlocal EnableExtensions\r\n"
                + "title CodaLoader Update\r\n"
                + "set \"CODA_PID=" + pid + "\"\r\n"
                + "set \"CODA_ROOT=" + root + "\"\r\n"
                + "set \"CODA_STAGE=" + stage + "\"\r\n"
                + ":wait_for_loader\r\n"
                + "tasklist /FI \"PID eq %CODA_PID%\" 2>NUL | find \"%CODA_PID%\" >NUL\r\n"
                + "if not errorlevel 1 (\r\n"
                + "  timeout /t 1 /nobreak >NUL\r\n"
                + "  goto wait_for_loader\r\n"
                + ")\r\n"
                + "if not exist \"%CODA_ROOT%\\run\\mods\" mkdir \"%CODA_ROOT%\\run\\mods\"\r\n"
                + "copy /Y \"%CODA_STAGE%\\CodaLoader.jar\" \"%CODA_ROOT%\\CodaLoader.jar\" >NUL || goto failed\r\n"
                + "copy /Y \"%CODA_STAGE%\\Launch-CodaLoader.bat\" \"%CODA_ROOT%\\Launch-CodaLoader.bat\" >NUL || goto failed\r\n"
                + "copy /Y \"%CODA_STAGE%\\run\\mods\\hello-coda.jar\" \"%CODA_ROOT%\\run\\mods\\hello-coda.jar\" >NUL || goto failed\r\n"
                + "start \"\" \"%CODA_ROOT%\\Launch-CodaLoader.bat\"\r\n"
                + "exit /b 0\r\n"
                + ":failed\r\n"
                + "echo.\r\n"
                + "echo [CodaLoader] Update failed while replacing files.\r\n"
                + "pause\r\n"
                + "exit /b 1\r\n";
    }

    private static String batPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("%", "%%");
    }

    private static boolean managedFilesMatch(Path installRoot, Map<String, String> hashes) throws Exception {
        for (String managed : MANAGED) {
            String expected = hashes.get(managed);
            if (expected == null || expected.isBlank()) return false;
            Path file = installRoot.resolve(managed).normalize();
            if (!file.startsWith(installRoot) || !Files.isRegularFile(file)) return false;
            if (!sha256(file).equalsIgnoreCase(expected)) return false;
        }
        return true;
    }

    private static String updateMode(Path runRoot) throws IOException {
        String env = System.getenv("CODALOADER_UPDATE_MODE");
        if (env != null && !env.isBlank()) {
            return normalizeMode(env);
        }

        Path configDir = runRoot.toAbsolutePath().normalize().resolve("config");
        Files.createDirectories(configDir);
        Path config = configDir.resolve("codaloader.properties");

        Properties properties = new Properties();
        if (Files.isRegularFile(config)) {
            try (InputStream in = Files.newInputStream(config)) {
                properties.load(in);
            }
        } else {
            properties.setProperty("updates", "auto");
            try (var out = Files.newOutputStream(config)) {
                properties.store(out, "CodaLoader settings: updates=auto, notify, or off");
            }
        }
        return normalizeMode(properties.getProperty("updates", "auto"));
    }

    private static String normalizeMode(String raw) {
        String mode = raw.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "auto", "notify", "off" -> mode;
            default -> "auto";
        };
    }

    private static Release latestRelease(HttpClient http) throws Exception {
        Object raw = readJson(http, RELEASES);
        if (!(raw instanceof List<?> releases)) {
            throw new IOException("GitHub releases API returned unexpected JSON");
        }

        for (Object item : releases) {
            Map<String, Object> release = object(item);
            if (Boolean.TRUE.equals(release.get("draft"))) continue;
            String tag = string(release, "tag_name");
            String html = string(release, "html_url");
            Object assetsRaw = release.get("assets");
            if (!(assetsRaw instanceof List<?> assetList)) continue;

            List<Asset> assets = new ArrayList<>();
            for (Object assetRaw : assetList) {
                Map<String, Object> asset = object(assetRaw);
                String name = optionalString(asset, "name");
                String url = optionalString(asset, "browser_download_url");
                if (name != null && url != null) assets.add(new Asset(name, URI.create(url)));
            }
            return new Release(tag, html, assets);
        }
        return null;
    }

    private static Manifest readManifest(HttpClient http, URI uri) throws Exception {
        Map<String, Object> json = object(readJson(http, uri));
        String version = string(json, "version");
        String bundle = string(json, "bundle");
        String sha = string(json, "sha256");

        Map<String, String> files = new java.util.LinkedHashMap<>();
        Object filesRaw = json.get("files");
        if (!(filesRaw instanceof Map<?, ?> map)) {
            throw new IOException("Update manifest has no files map");
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
                files.put(key, value);
            }
        }
        return new Manifest(version, bundle, sha, files);
    }

    private static Object readJson(HttpClient http, URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
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

    private static void download(HttpClient http, URI uri, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.deleteIfExists(part);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "CodaLoader/" + CodaTarget.LOADER_VERSION)
                .GET()
                .build();
        HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(part));
        if (response.statusCode() / 100 != 2) {
            Files.deleteIfExists(part);
            throw new IOException("HTTP " + response.statusCode() + " downloading update");
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void unzip(Path zip, Path target) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path out = target.resolve(entry.getName()).normalize();
                if (!out.startsWith(target)) {
                    throw new IOException("Refusing ZIP path outside staging directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
                in.closeEntry();
            }
        }
    }

    private static Path installationRoot() throws Exception {
        URI location = UpdateManager.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path code = Path.of(location).toAbsolutePath().normalize();
        if (!Files.isRegularFile(code) || !code.getFileName().toString().endsWith(".jar")) {
            return null;
        }
        return code.getParent();
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static int compareVersions(String left, String right) {
        Version a = Version.parse(left);
        Version b = Version.parse(right);
        int c = Integer.compare(a.major(), b.major());
        if (c != 0) return c;
        c = Integer.compare(a.minor(), b.minor());
        if (c != 0) return c;
        c = Integer.compare(a.patch(), b.patch());
        if (c != 0) return c;

        if (a.suffix().equals(b.suffix())) return 0;
        if (a.suffix().isEmpty()) return 1;
        if (b.suffix().isEmpty()) return -1;
        return a.suffix().compareToIgnoreCase(b.suffix());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) throws IOException {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IOException("Expected JSON object");
        }
        return (Map<String, Object>) map;
    }

    private static String string(Map<String, Object> map, String key) throws IOException {
        String value = optionalString(map, key);
        if (value == null || value.isBlank()) throw new IOException("Missing JSON string: " + key);
        return value;
    }

    private static String optionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String s ? s : null;
    }

    private record Asset(String name, URI url) {}

    private record Release(String tag, String htmlUrl, List<Asset> assets) {
        Asset asset(String name) {
            for (Asset asset : assets) {
                if (name.equals(asset.name())) return asset;
            }
            return null;
        }
    }

    private record Manifest(String version, String bundle, String sha256, Map<String, String> files) {}

    private record Version(int major, int minor, int patch, String suffix) {
        static Version parse(String raw) {
            String value = raw == null ? "" : raw.trim();
            if (value.startsWith("v")) value = value.substring(1);
            String[] mainAndSuffix = value.split("-", 2);
            String[] numbers = mainAndSuffix[0].split("\\.");
            int major = number(numbers, 0);
            int minor = number(numbers, 1);
            int patch = number(numbers, 2);
            String suffix = mainAndSuffix.length > 1 ? mainAndSuffix[1] : "";
            return new Version(major, minor, patch, suffix);
        }

        private static int number(String[] numbers, int index) {
            if (index >= numbers.length) return 0;
            try {
                return Integer.parseInt(numbers[index]);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
    }
}
