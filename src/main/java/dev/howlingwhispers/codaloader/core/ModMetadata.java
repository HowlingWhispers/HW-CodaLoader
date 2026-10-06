package dev.howlingwhispers.codaloader.core;

import java.util.List;

public record ModMetadata(
        int schema,
        String id,
        String name,
        String version,
        String entrypoint,
        String minecraft,
        List<String> depends) {

    public ModMetadata {
        depends = List.copyOf(depends);
    }
}
