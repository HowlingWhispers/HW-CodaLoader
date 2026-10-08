package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.core.CodaLoader;
import dev.howlingwhispers.codaloader.core.ModMetadata;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.List;

/** Java agent entrypoint loaded inside the Minecraft JVM. */
public final class CodaAgent {
    private static volatile CodaLoader activeLoader;

    private CodaAgent() {}

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        CodaMenuTransformer.install(instrumentation);
        CodaServerTickTransformer.install(instrumentation);

        Path root = agentArgs == null || agentArgs.isBlank()
                ? Path.of("run")
                : Path.of(agentArgs).toAbsolutePath().normalize();

        System.setProperty("codaloader.inGame", "true");
        System.setProperty("codaloader.root", root.toString());

        System.out.println("[CodaLoader] Agent attached inside Minecraft JVM. PID=" + ProcessHandle.current().pid());

        if (Boolean.getBoolean("codaloader.officialLauncher")) {
            // The official launcher handles account verification and downloads.
            // The agent still provides our menus, artwork and menu music.
            try {
                String configured = System.getProperty("codaloader.basePack", "");
                Path basePack = configured.isBlank()
                        ? root.resolveSibling("resourcepacks").resolve("cml-base-resources")
                        : Path.of(configured).toAbsolutePath().normalize();
                new MinecraftBootstrap(root, basePack).prepareProfileResources();
                System.out.println("[CodaLoader] Official launcher profile resources prepared.");
            } catch (Exception error) {
                System.err.println("[CodaLoader] Official profile branding warning: " + error);
            }
        }

        int modCount = 0;
        try {
            CodaLoader loader = new CodaLoader(root);
            List<ModMetadata> mods = loader.loadAndInitialize();
            activeLoader = loader;
            modCount = mods.size();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                CodaLoader current = activeLoader;
                if (current == null) return;
                try {
                    current.close();
                } catch (Exception ex) {
                    System.err.println("[CodaLoader] Error closing mod classloaders: " + ex);
                }
            }, "CodaLoader-Shutdown"));
        } catch (Throwable ex) {
            System.err.println("[CodaLoader] In-game mod initialization failed:");
            ex.printStackTrace(System.err);
        }

        ClientHooks.start(modCount);
        ServerCommandHooks.start();
    }
}

