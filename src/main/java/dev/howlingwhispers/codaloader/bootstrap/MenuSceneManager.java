package dev.howlingwhispers.codaloader.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Maintains prepared menu-scene variants and atomically swaps the active panorama textures. */
final class MenuSceneManager {
    private static final String SCENES_DIRECTORY = ".codaloader/menu-scenes";
    private static final String CURRENT_FILE = "current.txt";

    private MenuSceneManager() {}

    static Path scenesRoot(Path root) {
        return root.resolve(SCENES_DIRECTORY);
    }

    static Path activeBackground(Path root) {
        return root.resolve("resourcepacks")
                .resolve("HowlingWhispers-Branding")
                .resolve("assets")
                .resolve("minecraft")
                .resolve("textures")
                .resolve("gui")
                .resolve("title")
                .resolve("background");
    }

    static String activateRandom(Path root, boolean preferDifferent) throws IOException {
        Path scenes = scenesRoot(root);
        if (!Files.isDirectory(scenes)) return null;

        List<Path> available = new ArrayList<>();
        try (var entries = Files.list(scenes)) {
            entries.filter(Files::isDirectory)
                    .filter(MenuSceneManager::isCompleteScene)
                    .sorted()
                    .forEach(available::add);
        }
        if (available.isEmpty()) return null;

        String current = readCurrent(scenes);
        List<Path> choices = available;
        if (preferDifferent && available.size() > 1 && current != null) {
            List<Path> different = available.stream()
                    .filter(path -> !path.getFileName().toString().equals(current))
                    .toList();
            if (!different.isEmpty()) choices = different;
        }

        Path selected = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        copyScene(selected, activeBackground(root));
        String id = selected.getFileName().toString();
        Files.writeString(scenes.resolve(CURRENT_FILE), id + "\n", StandardCharsets.UTF_8);
        return id;
    }

    private static boolean isCompleteScene(Path dir) {
        for (int i = 0; i < 6; i++) {
            if (!Files.isRegularFile(dir.resolve("panorama_" + i + ".png"))) return false;
        }
        return true;
    }

    private static String readCurrent(Path scenes) {
        try {
            Path marker = scenes.resolve(CURRENT_FILE);
            if (!Files.isRegularFile(marker)) return null;
            String value = Files.readString(marker, StandardCharsets.UTF_8).trim();
            return value.isBlank() ? null : value;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void copyScene(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        for (int i = 0; i < 6; i++) {
            Path from = source.resolve("panorama_" + i + ".png");
            Path temp = target.resolve("panorama_" + i + ".png.tmp");
            Path to = target.resolve("panorama_" + i + ".png");
            Files.copy(from, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, to,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnavailable) {
                Files.move(temp, to, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
