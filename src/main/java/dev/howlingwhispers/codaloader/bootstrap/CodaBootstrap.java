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
                    + "\"minecraft\":\"" + CodaTarget.MINECRAFT_VERSION + "\","
                    + "\"minecraftDisplay\":\"" + CodaTarget.MINECRAFT_DISPLAY_NAME + "\","
                    + "\"minimumJava\":" + CodaTarget.MINECRAFT_MINIMUM_JAVA
                    + "}");
            return;
        }

        boolean loaderOnly = args.length > 0 && "--loader-only".equals(args[0]);
        Path root = loaderOnly && args.length > 1 ? Path.of(args[1]) : Path.of("run");

        if (loaderOnly) {
            try (CodaLoader loader = new CodaLoader(root)) {
                loader.loadAndInitialize();
            }
            return;
        }

        if (UpdateManager.checkAndStage(root)) {
            System.exit(UpdateManager.UPDATE_EXIT_CODE);
            return;
        }

        MinecraftBootstrap minecraft = new MinecraftBootstrap(root);
        int exit = minecraft.launch();
        if (exit != 0) {
            throw new IllegalStateException("Minecraft exited with code " + exit);
        }
    }
}
