package dev.howlingwhispers.codawolf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.UUID;

/** World-scoped, player-scoped data, independent of wolf entity chunk storage. */
public final class CompanionSave {
    public UUID wolfId;
    public boolean pendingRespawn;
    public boolean created;
    /** Locally scanned surroundings; disabled by owner command if requested. */
    public boolean awarenessEnabled = true;
    private final Path file;

    private CompanionSave(Path file) { this.file = file; }
    public static CompanionSave load(Path worldDirectory, UUID owner) throws IOException {
        Path base = worldDirectory.toAbsolutePath().normalize();
        Path file = base.resolve("data").resolve("howl-coda").resolve(owner.toString() + ".properties");
        CompanionSave saved = new CompanionSave(file);
        if (!Files.exists(file)) return saved;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) { props.load(in); }
        saved.created = Boolean.parseBoolean(props.getProperty("created", "false"));
        saved.awarenessEnabled = Boolean.parseBoolean(
                props.getProperty("awarenessEnabled", "true"));
        saved.pendingRespawn = Boolean.parseBoolean(props.getProperty("pendingRespawn", "false"));
        String uuid = props.getProperty("wolfId", "");
        if (!uuid.isBlank()) {
            try { saved.wolfId = UUID.fromString(uuid); }
            catch (IllegalArgumentException ex) { throw new IOException("Corrupt Coda wolf UUID: " + file, ex); }
        }
        if (saved.pendingRespawn && saved.wolfId != null)
            throw new IOException("Invalid saved Coda state (alive and pending): " + file);
        return saved;
    }
    public void persist() throws IOException {
        Files.createDirectories(file.getParent());
        Properties props = new Properties();
        props.setProperty("created", Boolean.toString(created));
        props.setProperty("awarenessEnabled", Boolean.toString(awarenessEnabled));
        props.setProperty("pendingRespawn", Boolean.toString(pendingRespawn));
        props.setProperty("wolfId", wolfId == null ? "" : wolfId.toString());
        Path tmp = Files.createTempFile(file.getParent(), "coda-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tmp)) { props.store(out, "H.O.W.L. Coda companion"); }
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(tmp); }
    }
}
