package dev.howlingwhispers.codaloader.bootstrap;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Instrumented fixture test, not an actual 26.4 Snapshot 3 game. */
public final class QuietUndergroundCreationTest {
    private static int count;
    private static void check(boolean condition, String message) {
        count++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] arguments) throws Exception {
        Path game = Files.createTempDirectory("howl-new-world-test-");
        System.setProperty("codaloader.root", game.toString());
        Path packDirectory = game.resolve("config/codaloader/worldgen");
        Files.createDirectories(packDirectory);
        Path source = packDirectory.resolve("hw-quiet-underground.zip");
        // The lifecycle only needs bytes + sha for its staging test, not a
        // fully valid worldgen ZIP. The real pack is checked by HW-Mods tests.
        byte[] bytes = "valid-bound-quiet-datapack-fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(source, bytes);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Files.writeString(packDirectory.resolve("hw-quiet-underground.zip.sha256"),
                digest + "  hw-quiet-underground.zip\n");

        Path staging = game.resolve("create-world-temp");
        CreateWorldScreen screen = new CreateWorldScreen(staging);
        screen.init();
        check(Files.exists(staging.resolve("hw-quiet-underground.zip")),
                "Quiet pack staged into Create World temporary repository");
        check(screen.repository().getSelectedIds().contains("file/hw-quiet-underground.zip"),
                "Quiet Underground selected for the first world data load");
        check(screen.applyCount() == 1, "World datapacks actually reloaded once");
        screen.init();
        check(screen.applyCount() == 1, "Repeated screen initialization does not loop");

        Path badStaging = game.resolve("bad-temp");
        Files.writeString(packDirectory.resolve("hw-quiet-underground.zip.sha256"),
                "0".repeat(64));
        CreateWorldScreen bad = new CreateWorldScreen(badStaging);
        bad.init();
        check(!Files.exists(badStaging.resolve("hw-quiet-underground.zip")),
                "Hash mismatch prevents installing worldgen pack");
        check(bad.applyCount() == 0, "Bad pack cannot reload world settings");

        Files.delete(source);
        Files.delete(packDirectory.resolve("hw-quiet-underground.zip.sha256"));
        CreateWorldScreen vanilla = new CreateWorldScreen(game.resolve("stable-world-temp"));
        vanilla.init();
        check(vanilla.applyCount() == 0, "Stable/no experimental pack leaves vanilla untouched");
        System.out.println("PASS: " + count + " Quiet Underground new-world staging assertions");
    }
}
