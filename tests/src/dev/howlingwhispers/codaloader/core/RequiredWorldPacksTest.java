package dev.howlingwhispers.codaloader.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Pure filesystem fixtures, not a live Minecraft new-world test. */
public final class RequiredWorldPacksTest {
    private static int checks;

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    private static void rejected(Throwing action, String reason) throws Exception {
        try { action.run(); } catch (IOException expected) { checks++; return; }
        throw new AssertionError(reason);
    }

    @FunctionalInterface private interface Throwing { void run() throws Exception; }

    private static Path archive(Path root, String name, String metadata, boolean omitDefinition) throws IOException {
        Path zip = root.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            List<String> entries = List.of(
                    "pack.mcmeta",
                    "data/minecraft/worldgen/carver/canyon.json",
                    "data/minecraft/worldgen/carver/cave.json",
                    "data/minecraft/worldgen/carver/cave_extra_underground.json",
                    "data/minecraft/worldgen/density_function/overworld/final_density.json");
            for (String entry : entries) {
                if (omitDefinition && entry.endsWith("final_density.json")) continue;
                out.putNextEntry(new ZipEntry(entry));
                out.write((entry.equals("pack.mcmeta") ? metadata : "{}").getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }

    private static String sha(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("howl-required-worlds-");
        Path zip = archive(root, "quiet.zip",
                "{\"pack\":{\"min_format\":123,\"max_format\":123}}", false);
        String hash = sha(zip);

        Path world = Files.createDirectory(root.resolve("fresh-world"));
        check(RequiredWorldPacks.prepareNewWorld(world, zip, hash)
                == RequiredWorldPacks.Result.INSTALLED, "Required pack installs on ungenerated world");
        Path installed = world.resolve("datapacks/hw-quiet-underground.zip");
        check(Files.isRegularFile(installed), "Pack installed at Minecraft datapacks location");
        check(Files.readAllBytes(zip).length == Files.readAllBytes(installed).length,
                "Entire bundled pack preserved");
        check(RequiredWorldPacks.prepareNewWorld(world, zip, hash)
                == RequiredWorldPacks.Result.ALREADY_PRESENT, "Install is idempotent");

        Path old = Files.createDirectory(root.resolve("old-world"));
        byte[] previous = "player-save".getBytes(StandardCharsets.UTF_8);
        Files.write(old.resolve("level.dat"), previous);
        check(RequiredWorldPacks.prepareNewWorld(old, zip, hash)
                == RequiredWorldPacks.Result.EXISTING_WORLD, "Existing world is skipped");
        check(java.util.Arrays.equals(previous, Files.readAllBytes(old.resolve("level.dat"))),
                "Existing world bytes unchanged");
        check(!Files.exists(old.resolve("datapacks")), "No old-world datapack directory created");

        Path chunks = Files.createDirectory(root.resolve("partial-world"));
        Files.createDirectory(chunks.resolve("region"));
        rejected(() -> RequiredWorldPacks.prepareNewWorld(chunks, zip, hash),
                "Generated chunks must never be modified");
        check(!Files.exists(chunks.resolve("datapacks")), "No partial-world mutations");

        Path community = Files.createDirectory(root.resolve("community-world"));
        Path data = Files.createDirectory(community.resolve("datapacks"));
        Files.writeString(data.resolve("other-mod.zip"), "KEEP");
        check(RequiredWorldPacks.prepareNewWorld(community, zip, hash)
                == RequiredWorldPacks.Result.INSTALLED, "Existing community packs coexist");
        check(Files.readString(data.resolve("other-mod.zip")).equals("KEEP"), "Community pack untouched");

        Path changed = Files.createDirectory(root.resolve("conflicting-world"));
        Path changedPacks = Files.createDirectory(changed.resolve("datapacks"));
        Path existing = changedPacks.resolve("hw-quiet-underground.zip");
        Files.writeString(existing, "different player content");
        rejected(() -> RequiredWorldPacks.prepareNewWorld(changed, zip, hash),
                "Conflicting pack must not be overwritten");
        check(Files.readString(existing).equals("different player content"),
                "Conflicting bytes preserved");

        Path wrongHash = Files.createDirectory(root.resolve("wrong-checksum"));
        rejected(() -> RequiredWorldPacks.prepareNewWorld(wrongHash, zip, "0".repeat(64)),
                "Checksum mismatch must fail");
        check(!Files.exists(wrongHash.resolve("datapacks")), "Checksum fails before world writes");

        Path wrongFormat = archive(root, "wrongformat.zip",
                "{\"pack\":{\"min_format\":122,\"max_format\":122}}", false);
        Path formatWorld = Files.createDirectory(root.resolve("wrong-format-world"));
        rejected(() -> RequiredWorldPacks.prepareNewWorld(formatWorld, wrongFormat, sha(wrongFormat)),
                "Wrong worldgen format must fail");

        Path incomplete = archive(root, "incomplete.zip",
                "{\"pack\":{\"min_format\":123,\"max_format\":123}}", true);
        Path incompleteWorld = Files.createDirectory(root.resolve("incomplete-world"));
        rejected(() -> RequiredWorldPacks.prepareNewWorld(incompleteWorld, incomplete, sha(incomplete)),
                "Incomplete pack must fail");

        Path unrecognized = Files.createDirectory(root.resolve("unrecognized"));
        Files.writeString(unrecognized.resolve("new-world-worldgen.json"), "{}");
        rejected(() -> RequiredWorldPacks.prepareNewWorld(unrecognized, zip, hash),
                "Unexpected existing data must fail without overwrite");

        Path symlink = root.resolve("linked-world");
        try {
            Files.createSymbolicLink(symlink, old);
            rejected(() -> RequiredWorldPacks.prepareNewWorld(symlink, zip, hash),
                    "Symlink world path refused");
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException denied) {
            // Filesystems without symlink support still exercise all regular cases.
        }

        check(Files.isRegularFile(installed), "Valid installation survives rejected operations");
        System.out.println("PASS: " + checks + " guarded Quiet Underground Java provisioning checks; live integration still required.");
    }
}
