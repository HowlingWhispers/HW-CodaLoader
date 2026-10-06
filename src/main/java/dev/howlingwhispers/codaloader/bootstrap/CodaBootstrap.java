package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.CodaLoader;

import java.nio.file.Path;

/** Standalone foundation launcher. Minecraft bootstrap will be added above this layer. */
public final class CodaBootstrap {
    public static void main(String[] args) throws Exception {
        Path gameDirectory = args.length > 0 ? Path.of(args[0]) : Path.of("run");
        try (CodaLoader loader = new CodaLoader(gameDirectory)) {
            loader.loadAndInitialize();
        }
    }
}
