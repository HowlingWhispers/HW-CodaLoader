package dev.howlingwhispers.codaloader.core;

import java.net.URLClassLoader;
import java.nio.file.Path;

record ModCandidate(Path jar, ModMetadata metadata, URLClassLoader classLoader) implements AutoCloseable {
    @Override
    public void close() throws Exception {
        classLoader.close();
    }
}
