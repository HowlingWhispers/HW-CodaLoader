package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.instrument.Instrumentation;

/** Smoke agent for real Mojang client classes. Does not start the renderer. */
public final class NativeGameTestAgent {
    public static void premain(String args, Instrumentation instrumentation) {
        CodaNativeRegistryTransformer.install(instrumentation);
    }
}
