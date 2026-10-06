package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.CodaLoader;

import java.nio.file.Path;

/**
 * CodaLoader entrypoint.
 *
 * Normal launch bootstraps Minecraft after loading Coda mods.
 * --loader-only keeps the small loader smoke-test path used by CI.
 */
public final class CodaBootstrap {
    public static void main(String[] args) throws Exception {
        boolean loaderOnly = args.length > 0 && "--loader-only".equals(args[0]);
        Path root = loaderOnly && args.length > 1 ? Path.of(args[1]) : Path.of("run");

        try (CodaLoader loader = new CodaLoader(root)) {
            loader.loadAndInitialize();
        }

        if (loaderOnly) {
            return;
        }

        MinecraftBootstrap minecraft = new MinecraftBootstrap(root);
        int exit = minecraft.launch();
        if (exit != 0) {
            throw new IllegalStateException("Minecraft exited with code " + exit);
        }
    }
}
