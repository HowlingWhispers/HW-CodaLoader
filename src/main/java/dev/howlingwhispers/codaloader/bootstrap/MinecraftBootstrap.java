package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.CodaTarget;
import dev.howlingwhispers.codaloader.core.MiniJson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
    private final HttpClient http;

    public MinecraftBootstrap(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.runtime = this.root.resolve("runtime");
        this.libraries = runtime.resolve("libraries");
        this.assets = runtime.resolve("assets");
        this.versions = runtime.resolve("versions");
        this.natives = runtime.resolve("natives").resolve(CodaTarget.MINECRAFT_VERSION);
        this.game = this.root.resolve("game");
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

    private void prepareCustomMusicPack() throws IOException {
        Path menuMusic = root.resolve("music").resolve("menu");
        Files.createDirectories(menuMusic);

        Path readme = menuMusic.resolve("README.txt");
        if (!Files.exists(readme)) {
            Files.writeString(readme,
                    "Drop .ogg files in this folder. CodaLoader will use them as Minecraft menu music.\n",
                    StandardCharsets.UTF_8);
        }

        List<Path> tracks;
        try (var stream = Files.list(menuMusic)) {
            tracks = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                    .sorted()
                    .toList();
        }

        if (tracks.isEmpty()) {
            System.out.println("[CodaLoader] Custom music folder: " + menuMusic + " (no .ogg tracks yet)");
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
        enableGeneratedMusicPack();

        System.out.println("[CodaLoader] Custom menu music enabled: " + tracks.size() + " track(s)");
    }

    private void enableGeneratedMusicPack() throws IOException {
        Path options = game.resolve("options.txt");
        List<String> lines = Files.exists(options)
                ? new ArrayList<>(Files.readAllLines(options, StandardCharsets.UTF_8))
                : new ArrayList<>();

        String packId = "\"file/CodaLoader-Music\"";
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
