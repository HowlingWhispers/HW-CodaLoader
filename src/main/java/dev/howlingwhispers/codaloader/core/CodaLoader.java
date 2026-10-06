package dev.howlingwhispers.codaloader.core;

import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class CodaLoader implements AutoCloseable {
    private final Path gameDirectory;
    private final Path modsDirectory;
    private final Path configDirectory;
    private final List<ModCandidate> openCandidates = new ArrayList<>();

    public CodaLoader(Path gameDirectory) {
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        this.modsDirectory = this.gameDirectory.resolve("mods");
        this.configDirectory = this.gameDirectory.resolve("config");
    }

    public List<ModMetadata> loadAndInitialize() throws Exception {
        Files.createDirectories(modsDirectory);
        Files.createDirectories(configDirectory);

        System.out.println("[CodaLoader] " + CodaTarget.LOADER_VERSION);
        System.out.println("[CodaLoader] Target Minecraft: " + CodaTarget.MINECRAFT_VERSION);
        System.out.println("[CodaLoader] Target runtime: Java " + CodaTarget.MINECRAFT_MINIMUM_JAVA + "+ when attached to Minecraft");
        System.out.println("[CodaLoader] Scanning: " + modsDirectory);

        MetadataReader reader = new MetadataReader();
        Map<String, ModCandidate> byId = new LinkedHashMap<>();

        try (Stream<Path> files = Files.list(modsDirectory)) {
            for (Path jar : files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                ModMetadata metadata = reader.read(jar);
                if (!CodaTarget.MINECRAFT_VERSION.equals(metadata.minecraft())) {
                    throw new IllegalStateException("Mod '" + metadata.id() + "' targets Minecraft " + metadata.minecraft()
                            + ", but this CodaLoader build targets " + CodaTarget.MINECRAFT_VERSION);
                }
                if (byId.containsKey(metadata.id())) {
                    throw new IllegalStateException("Duplicate mod id '" + metadata.id() + "' in " + jar.getFileName());
                }

                URLClassLoader classLoader = new URLClassLoader(
                        new java.net.URL[]{jar.toUri().toURL()},
                        CodaMod.class.getClassLoader());
                ModCandidate candidate = new ModCandidate(jar, metadata, classLoader);
                openCandidates.add(candidate);
                byId.put(metadata.id(), candidate);
            }
        }

        System.out.println("[CodaLoader] Found " + byId.size() + " mod(s).");
        List<ModCandidate> ordered = new DependencySorter().sort(byId);
        List<String> allIds = ordered.stream().map(c -> c.metadata().id()).toList();

        for (ModCandidate candidate : ordered) {
            initialize(candidate, allIds);
        }

        System.out.println("[CodaLoader] Ready. " + ordered.size() + " mod(s) initialized.");
        return ordered.stream().map(ModCandidate::metadata).toList();
    }

    private void initialize(ModCandidate candidate, List<String> allIds) throws Exception {
        ModMetadata meta = candidate.metadata();
        System.out.println("[CodaLoader] Loading " + meta.id() + " " + meta.version());

        Class<?> entryClass = Class.forName(meta.entrypoint(), true, candidate.classLoader());
        if (!CodaMod.class.isAssignableFrom(entryClass)) {
            throw new IllegalStateException("Entrypoint " + meta.entrypoint() + " does not implement " + CodaMod.class.getName());
        }

        CodaMod mod = (CodaMod) entryClass.getDeclaredConstructor().newInstance();
        CodaContext context = new CodaContext(
                CodaTarget.LOADER_VERSION,
                CodaTarget.MINECRAFT_VERSION,
                gameDirectory,
                configDirectory.resolve(meta.id()),
                meta.id(),
                allIds);
        Files.createDirectories(context.configDirectory());
        mod.onInitialize(context);
    }

    @Override
    public void close() throws Exception {
        Exception first = null;
        for (int i = openCandidates.size() - 1; i >= 0; i--) {
            try {
                openCandidates.get(i).close();
            } catch (Exception ex) {
                if (first == null) first = ex;
            }
        }
        if (first != null) throw first;
    }
}
