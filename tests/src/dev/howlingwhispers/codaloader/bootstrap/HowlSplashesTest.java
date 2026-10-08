package dev.howlingwhispers.codaloader.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class HowlSplashesTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("howl-splashes-");
        try {
            Path custom = Files.createDirectories(root.resolve("branding"));
            Path base = Files.createDirectories(root.resolve("base/branding"));
            Path pack = root.resolve("pack");
            Path generated = pack.resolve("assets/minecraft/texts/splashes.txt");
            var bootstrap = new MinecraftBootstrap(root, base.getParent());
            var write = MinecraftBootstrap.class.getDeclaredMethod("writeSplashTexts", Path.class, Path.class, Path.class);
            write.setAccessible(true);
            write.invoke(bootstrap, custom, base, pack);
            check(Files.readString(generated).contains(HowlSplashes.CODA_HUFF), "default splash is missing Coda's huff");
            String oldPack = "Powered by CML!\nCodaLoader says hi!\nOther message\n";
            Files.writeString(base.resolve("splashes.txt"), oldPack);
            write.invoke(bootstrap, custom, base, pack);
            String shipped = Files.readString(generated);
            check(shipped.contains(HowlSplashes.CODA_HUFF) && shipped.contains("Powered by H.O.W.L.!"),
                    "existing base pack hides the new splash or keeps old branding");
            check(shipped.contains("Other message") && Files.readString(base.resolve("splashes.txt")).equals(oldPack),
                    "base pack was overwritten or its other messages were lost");
            check(HowlSplashes.shipped(shipped).equals(shipped), "repeated branding duplicates messages");
            String user = "My CML custom splash!\r\n";
            Files.writeString(custom.resolve("splashes.txt"), user);
            write.invoke(bootstrap, custom, base, pack);
            check(Files.readString(generated).equals(user) && Files.readString(custom.resolve("splashes.txt")).equals(user),
                    "custom splash override changed");
            System.out.println("HOWL splash tests passed: default, shipped base migration, idempotence and custom override.");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
