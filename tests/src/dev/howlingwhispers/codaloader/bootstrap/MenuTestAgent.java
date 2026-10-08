package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.instrument.Instrumentation;

/** Exercises the production transformer without starting a Minecraft client. */
public final class MenuTestAgent {
    public static void premain(String args, Instrumentation instrumentation) {
        CodaMenuTransformer.install(instrumentation);
    }
}
