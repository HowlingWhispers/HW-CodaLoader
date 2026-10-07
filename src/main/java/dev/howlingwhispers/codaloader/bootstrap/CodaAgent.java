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
        Path root = agentArgs == null || agentArgs.isBlank()
                ? Path.of("run")
                : Path.of(agentArgs).toAbsolutePath().normalize();

        System.setProperty("codaloader.inGame", "true");
        System.setProperty("codaloader.root", root.toString());

        System.out.println("[CodaLoader] Agent attached inside Minecraft JVM. PID=" + ProcessHandle.current().pid());

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
