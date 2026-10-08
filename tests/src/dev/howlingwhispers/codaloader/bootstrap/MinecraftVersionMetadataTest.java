package dev.howlingwhispers.codaloader.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;

/** Regression: a valid cached Snapshot 3 version must not need Mojang's manifest. */
public final class MinecraftVersionMetadataTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static String json(String id) {
        return "{\"id\":\"" + id + "\",\"mainClass\":\"net.minecraft.client.main.Main\","
                + "\"libraries\":[],\"arguments\":{\"game\":[],\"jvm\":[]},"
                + "\"downloads\":{\"client\":{\"url\":\"https://piston-data.mojang.com/v1/client.jar\","
                + "\"sha1\":\"0123456789abcdef0123456789abcdef01234567\"}},"
                + "\"assetIndex\":{\"id\":\"test\","
                + "\"url\":\"https://piston-meta.mojang.com/v1/assets.json\","
                + "\"sha1\":\"0123456789abcdef0123456789abcdef01234567\"}}";
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("howl-minecraft-metadata-");
        String target = "26.4-snapshot-3";
        Path local = dir.resolve("nightly/version.json");
        Path official = dir.resolve("official/version.json");
        try {
            Files.createDirectories(local.getParent());
            Files.createDirectories(official.getParent());
            check(!MinecraftBootstrap.reuseVersionMetadata(local, official, target),
                    "Missing cache with no official install still requires download");
            Files.writeString(local, json(target));
            check(MinecraftBootstrap.usableVersionMetadata(local, target),
                    "Valid cached metadata for the selected Snapshot 3 is accepted");
            check(MinecraftBootstrap.reuseVersionMetadata(local, official, target),
                    "Valid cache skips Mojang network manifest completely");
            check(Files.readString(local).equals(json(target)), "Existing metadata not rewritten");

            Files.writeString(local, json("26.4-snapshot-2"));
            check(!MinecraftBootstrap.reuseVersionMetadata(local, official, target),
                    "Different Minecraft version cannot use stale metadata");

            Files.writeString(official, json(target));
            check(MinecraftBootstrap.reuseVersionMetadata(local, official, target),
                    "Reuse metadata from user's official Minecraft installation");
            check(Files.readString(local).equals(json(target)), "Official metadata copied safely");

            Files.writeString(local, "{oops");
            Files.writeString(official, json(target).replace("0123456789abcdef0123456789abcdef01234567", "deadbeef"));
            check(!MinecraftBootstrap.reuseVersionMetadata(local, official, target),
                    "Corrupt cache or invalid official SHA metadata rejected");
            check(Files.readString(local).equals("{oops"),
                    "Rejected local cache not overwritten by an invalid official file");

            Files.writeString(local, json(target).replace("https://piston-data", "http://piston-data"));
            check(!MinecraftBootstrap.usableVersionMetadata(local, target),
                    "Unencrypted client download URL is not accepted");
            Files.writeString(local, json(target).replace("\"libraries\":[]", "\"libraries\":42"));
            check(!MinecraftBootstrap.usableVersionMetadata(local, target),
                    "Malformed library metadata rejected");
            System.out.println("PASS: " + assertions
                    + " Mojang manifest-free local test boot / cache safety assertions");
        } finally {
            try (var files = Files.walk(dir)) {
                for (var path : files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList())
                    Files.deleteIfExists(path);
            }
        }
    }
}
