package dev.howlingwhispers.codaloader.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

final class MetadataReader {
    static final String METADATA_PATH = "coda.mod.json";

    ModMetadata read(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getJarEntry(METADATA_PATH);
            if (entry == null) throw new IOException("Missing " + METADATA_PATH + " in " + jarPath.getFileName());
            try (InputStream in = jar.getInputStream(entry)) {
                Object parsed = MiniJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                if (!(parsed instanceof Map<?, ?> raw)) throw new IOException(METADATA_PATH + " must contain a JSON object");
                return convert(raw, jarPath);
            }
        }
    }

    private ModMetadata convert(Map<?, ?> map, Path source) throws IOException {
        int schema = integer(map, "schema");
        if (schema != 1) throw fail(source, "Unsupported metadata schema " + schema);

        String id = string(map, "id");
        if (!id.matches("[a-z][a-z0-9_]{1,63}")) {
            throw fail(source, "Invalid mod id '" + id + "'. Use lowercase letters, numbers, underscore; start with a letter.");
        }

        String name = string(map, "name");
        String version = string(map, "version");
        String entrypoint = string(map, "entrypoint");
        String minecraft = string(map, "minecraft");

        List<String> depends = new ArrayList<>();
        Object rawDepends = map.containsKey("depends") ? map.get("depends") : List.of();
        if (!(rawDepends instanceof List<?> list)) throw fail(source, "'depends' must be an array");
        for (Object item : list) {
            if (!(item instanceof String s) || s.isBlank()) throw fail(source, "Every dependency must be a non-empty mod id");
            depends.add(s);
        }

        return new ModMetadata(schema, id, name, version, entrypoint, minecraft, depends);
    }

    private int integer(Map<?, ?> map, String key) throws IOException {
        Object value = map.get(key);
        if (!(value instanceof Number n)) throw new IOException("Missing/integer metadata field: " + key);
        return n.intValue();
    }

    private String string(Map<?, ?> map, String key) throws IOException {
        Object value = map.get(key);
        if (!(value instanceof String s) || s.isBlank()) throw new IOException("Missing/non-empty metadata field: " + key);
        return s;
    }

    private IOException fail(Path source, String message) {
        return new IOException(source.getFileName() + ": " + message);
    }
}
