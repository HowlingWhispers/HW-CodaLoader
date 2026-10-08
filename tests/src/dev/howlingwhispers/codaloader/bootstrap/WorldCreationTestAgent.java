package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.instrument.Instrumentation;

/** Instrument a named JVM fixture with the real H.O.W.L. creation hook. */
public final class WorldCreationTestAgent {
    public static void premain(String options, Instrumentation instrumentation) {
        CodaWorldCreationTransformer.install(instrumentation);
    }
}
