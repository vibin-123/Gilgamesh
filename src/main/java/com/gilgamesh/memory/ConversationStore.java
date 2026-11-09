package com.gilgamesh.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ConversationStore — Saves and loads a {@link SessionSnapshot} as JSON on disk.
 *
 * Failure policy: persistence must never break the chat. Save errors are logged and
 * swallowed; a missing, corrupt, outdated, or too-old file simply means "start fresh".
 */
public class ConversationStore {

    private static final Logger LOG = Logger.getLogger(ConversationStore.class.getName());

    /** Sessions older than this are not restored. */
    public static final long DEFAULT_MAX_AGE_MS = 24L * 60 * 60 * 1000;

    private final Path file;
    private final long maxAgeMs;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ConversationStore(Path file) {
        this(file, DEFAULT_MAX_AGE_MS);
    }

    public ConversationStore(Path file, long maxAgeMs) {
        this.file = file;
        this.maxAgeMs = maxAgeMs;
    }

    /** Default location: ~/.gilgamesh/session.json */
    public static Path defaultPath() {
        return Paths.get(System.getProperty("user.home"), ".gilgamesh", "session.json");
    }

    public Path getFile() {
        return file;
    }

    /**
     * Writes the snapshot atomically (temp file + move) so a crash mid-write can't corrupt the saved session.
     */
    public void save(SessionSnapshot snapshot) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(temp, gson.toJson(snapshot), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not save session to " + file, e);
        }
    }

    /**
     * Loads the saved session.
     *
     * @return the snapshot, or empty if there is none, it is unreadable/corrupt, from another
     *         format version, or older than the maximum age
     */
    public Optional<SessionSnapshot> load() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            SessionSnapshot snapshot = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), SessionSnapshot.class);
            if (snapshot == null || snapshot.version != SessionSnapshot.CURRENT_VERSION) {
                LOG.warning("Ignoring saved session with unsupported format: " + file);
                return Optional.empty();
            }
            if (maxAgeMs > 0 && System.currentTimeMillis() - snapshot.savedAtMillis > maxAgeMs) {
                LOG.fine("Ignoring saved session older than the maximum age.");
                return Optional.empty();
            }
            return Optional.of(snapshot);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Ignoring unreadable saved session " + file, e);
            return Optional.empty();
        }
    }

    /** Deletes the saved session, if any. */
    public void delete() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not delete saved session " + file, e);
        }
    }
}
