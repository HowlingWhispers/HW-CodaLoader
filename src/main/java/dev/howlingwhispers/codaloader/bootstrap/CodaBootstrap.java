package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.CodaLoader;
import dev.howlingwhispers.codaloader.core.CodaTarget;

import java.nio.file.Path;

/**
 * CodaLoader entrypoint.
 *
 * Normal launch checks for a public release update, then bootstraps Minecraft.
 * --loader-only keeps the small loader smoke-test path used by CI.
 */
public final class CodaBootstrap {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--version".equals(args[0])) {
            System.out.println(CodaTarget.LOADER_VERSION);
            return;
        }
        if (args.length > 0 && "--version-json".equals(args[0])) {
            System.out.println("{"
                    + "\"loader\":\"" + CodaTarget.LOADER_VERSION + "\","
                    + "\"displayName\":\"" + CodaTarget.LOADER_DISPLAY_NAME + "\","
                    + "\"fullName\":\"" + CodaTarget.LOADER_FULL_NAME + "\","
                    + "\"minecraft\":\"" + CodaTarget.MINECRAFT_VERSION + "\","
                    + "\"minecraftDisplay\":\"" + CodaTarget.MINECRAFT_DISPLAY_NAME + "\","
                    + "\"minimumJava\":" + CodaTarget.MINECRAFT_MINIMUM_JAVA
                    + "}");
            return;
        }

        boolean loaderOnly = args.length > 0 && "--loader-only".equals(args[0]);
        Path root = loaderOnly && args.length > 1
                ? Path.of(args[1])
                : argumentPath(args, "--root", Path.of("minecraft"));
        Path basePack = argumentPath(args, "--base-pack",
                root.toAbsolutePath().normalize().resolveSibling("cml-base"));

        if (loaderOnly) {
            try (CodaLoader loader = new CodaLoader(root)) {
                loader.loadAndInitialize();
            }
            return;
        }

        boolean launcherManaged = "CodaLauncher".equals(System.getenv("CODA_LAUNCHED_BY"));
        if (!launcherManaged && UpdateManager.checkAndStage(root)) {
            System.exit(UpdateManager.UPDATE_EXIT_CODE);
            return;
        }
        if (launcherManaged) {
            System.out.println("[CodaLoader] H.O.W.L. updates were checked by CodaLauncher before launch.");
        }

        MinecraftBootstrap minecraft = new MinecraftBootstrap(root, basePack);
        int exit = minecraft.launch();
        if (exit != 0) {
            throw new IllegalStateException("Minecraft exited with code " + exit);
        }
    }

    private static Path argumentPath(String[] args, String name, Path fallback) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (name.equals(args[i])) return Path.of(args[i + 1]);
        }
        return fallback;
    }
}
