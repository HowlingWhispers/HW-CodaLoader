package dev.howlingwhispers.codaloader.bootstrap;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Nightly-only NEW-WORLD datapack activation.
 *
 * The world creation screen has its OWN temporary pack repository, which must
 * discover and select the override before its initial terrain settings load.
 * Copying into saves/<name>/datapacks after the fact does not accomplish this.
 */
public final class CodaWorldCreationLifecycle {
    private static final String PACK = "hw-quiet-underground.zip";
    private static final String ID = "file/" + PACK;
    private static final Set<Object> ATTEMPTED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private CodaWorldCreationLifecycle() {}

    public static void afterInit(Object screen) {
        if (screen == null) return;
        synchronized (ATTEMPTED) {
            if (!ATTEMPTED.add(screen)) return;
        }

        try {
            String game = System.getProperty("codaloader.root", "");
            if (game.isBlank()) return;
            Path root = Path.of(game).toAbsolutePath().normalize();
            Path source = root.resolve("config").resolve("codaloader")
                    .resolve("worldgen").resolve(PACK);
            Path checksum = source.resolveSibling(PACK + ".sha256");
            // Stable does not ship this Nightly-only experimental datapack.
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)
                    && !Files.exists(checksum, LinkOption.NOFOLLOW_LINKS)) return;
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(checksum, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(source) || Files.isSymbolicLink(checksum))
                throw new IOException("Worldgen datapack or verification file missing/linked");
            byte[] bytes = Files.readAllBytes(source);
            if (bytes.length < 1 || bytes.length > 8 * 1024 * 1024)
                throw new IOException("Worldgen datapack is outside expected size");
            String expected = Files.readString(checksum).trim().split("\\s+")[0];
            if (!expected.matches("(?i)[0-9a-f]{64}")
                    || !MessageDigest.isEqual(MessageDigest.getInstance("SHA-256")
                    .digest(bytes), java.util.HexFormat.of().parseHex(expected)))
                throw new IOException("Worldgen datapack SHA-256 failed");

            Object state = CommandReflection.call(screen, "getUiState");
            Object context = CommandReflection.call(state, "getSettings");
            Object config = CommandReflection.call(context, "dataConfiguration");
            Object pair = invokePrivate(screen, "getDataPackSelectionSettings", config);
            Path temp = (Path) CommandReflection.call(pair, "getFirst");
            Object repository = CommandReflection.call(pair, "getSecond");
            if (!Files.isDirectory(temp, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(temp))
                throw new IOException("Worldgen staging directory is not safe");
            Path dest = temp.resolve(PACK);
            if (Files.isSymbolicLink(dest))
                throw new IOException("Refusing worldgen pack symbolic link");
            if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) {
                if (!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(dest)), MessageDigest.getInstance("SHA-256").digest(bytes)))
                    throw new IOException("Worldgen staging pack conflict, existing pack preserved");
            } else {
                // The staging area is a *temporary Create World UI directory*,
                // not a saved player world. No existing saved worlds are edited.
                Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
            }

            CommandReflection.call(repository, "reload");
            Object available = CommandReflection.call(repository, "getAvailableIds");
            if (!(available instanceof Collection<?> ids) || !ids.contains(ID))
                throw new IOException("Snapshot 3 world pack registry did not discover " + ID);

            Object current = CommandReflection.call(repository, "getSelectedIds");
            if (!(current instanceof Collection<?> existing))
                throw new IOException("Snapshot 3 selected datapack IDs have changed");
            LinkedHashSet<String> selected = new LinkedHashSet<>();
            for (Object id : existing) {
                if (!(id instanceof String value))
                    throw new IOException("Unexpected datapack identifier");
                selected.add(value);
            }
            selected.add(ID);
            CommandReflection.call(repository, "setSelected", selected);
            // Reread this screen's data AFTER pack is selected. Without this
            // reload Minecraft may display the pack but generate vanilla caves.
            invokePrivate(screen, "tryApplyNewDataPacks", repository, false,
                    (Consumer<Object>) ignored -> System.err.println(
                            "[H.O.W.L.] Quiet Underground world preset was refused; check datapack compatibility."));
            System.out.println("[H.O.W.L.] Quiet Underground selected for this NEW world's data load.");
        } catch (Throwable ex) {
            System.err.println("[H.O.W.L.] Quiet Underground was NOT enabled: " + ex);
        }
    }

    private static Object invokePrivate(Object owner, String method, Object... args)
            throws ReflectiveOperationException {
        Method found = null;
        for (Method candidate : owner.getClass().getDeclaredMethods()) {
            if (candidate.getName().equals(method) && candidate.getParameterCount() == args.length) {
                if (found != null) throw new NoSuchMethodException("Ambiguous " + method);
                found = candidate;
            }
        }
        if (found == null || !found.trySetAccessible())
            throw new NoSuchMethodException("Unsupported new-world method " + method);
        try { return found.invoke(owner, args); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof ReflectiveOperationException cause) throw cause;
            throw new IllegalStateException(method + " failed", error.getCause());
        }
    }
}
