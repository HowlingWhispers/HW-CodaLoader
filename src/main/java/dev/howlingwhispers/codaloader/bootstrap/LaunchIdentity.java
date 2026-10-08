package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.MiniJson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;
import java.util.Map;

/** No anonymous CodaPlayer fallback. Online credentials are verified independently. */
public record LaunchIdentity(String name, String uuid, String token, String clientId, boolean offline, boolean localOnly) {
    public static LaunchIdentity fromEnvironment(Map<String, String> env) {
        String name = env.getOrDefault("CODA_PLAYER_NAME", "");
        String uuid = env.getOrDefault("CODA_PLAYER_UUID", "");
        String mode = env.getOrDefault("CODA_PLAY_MODE", "");
        String token = env.getOrDefault("CODA_ACCESS_TOKEN", "");
        if (!"CodaLauncher".equals(env.get("CODA_LAUNCHED_BY")) ||
                !name.matches("[A-Za-z0-9_]{1,16}") || !uuid.matches("[0-9a-fA-F]{32}") ||
                !(mode.equals("online") || mode.equals("offline") || mode.equals("local")))
            throw new IllegalStateException("Open CodaLauncher Profile and verify your Minecraft account before playing.");
        boolean localOnly = mode.equals("local");
        boolean offline = localOnly || mode.equals("offline");
        if (localOnly) {
            // Match the original CodaPlayer UUID to preserve existing singleplayer saves.
            String legacyUuid = UUID.nameUUIDFromBytes("OfflinePlayer:CodaPlayer"
                    .getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
            if (!"CodaPlayer".equals(name) || !legacyUuid.equalsIgnoreCase(uuid)
                    || (!token.isBlank() && !"0".equals(token))
                    || !env.getOrDefault("CODA_AUTH_CLIENT_ID", "").isBlank())
                throw new IllegalStateException("Invalid local singleplayer identity.");
        }
        if (!offline && (token.isBlank() || token.equals("0")))
            throw new IllegalStateException("Online play requires a valid Minecraft access token.");
        // Offline authorization belongs to the launcher's OS-protected ownership cache.
        // Environment flags are local launch metadata, never proof for a remote service.
        return new LaunchIdentity(name, uuid, offline ? "0" : token,
                env.getOrDefault("CODA_AUTH_CLIENT_ID", ""), offline, localOnly);
    }
    public void verifyOnline() throws Exception {
        if (offline) return;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        Map<?, ?> entitlements = get(client, "/entitlements/mcstore");
        if (!(entitlements.get("items") instanceof List<?> items) || items.stream().noneMatch(item ->
                item instanceof Map<?, ?> entry && ("game_minecraft".equals(entry.get("name")) || "product_minecraft".equals(entry.get("name")))))
            throw new IOException("Minecraft Java ownership was not verified.");
        Map<?, ?> profile = get(client, "/minecraft/profile");
        if (!uuid.equalsIgnoreCase(String.valueOf(profile.get("id"))) || !name.equals(profile.get("name")))
            throw new IOException("Minecraft identity does not match the verified account.");
    }
    private Map<?, ?> get(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com" + path))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Minecraft account verification failed (HTTP " + response.statusCode() + ").");
        Object result = MiniJson.parse(response.body());
        if (!(result instanceof Map<?, ?> map)) throw new IOException("Invalid Minecraft verification response.");
        return map;
    }
    @Override public String toString() { return name + " (" + (localOnly ? "local singleplayer, unverified" : offline ? "verified offline" : "verified online") + ")"; }
}
