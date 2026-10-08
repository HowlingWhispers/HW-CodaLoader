package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.instrument.Instrumentation;

/** Only fixture testing; do not confuse with a live Minecraft integration test. */
public final class ServerTickTestAgent {
    public static void premain(String args, Instrumentation instrumentation) {
        CodaServerTickTransformer.install(instrumentation);
    }
}
