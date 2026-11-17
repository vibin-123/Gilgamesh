package com.gilgamesh.web;

import com.gilgamesh.ConversationManager;
import com.gilgamesh.GilgameshBot;
import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.ConversationStore;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * SessionManager — Gives every web user their own conversation.
 *
 * A session is identified by a random id that the browser keeps and sends with each
 * message. Each session has its own {@link ConversationManager} and memory, and is saved
 * to its own file, so users never see each other's topics, names or history.
 *
 * Only a limited number of sessions are kept in memory. When the limit is reached the
 * one used longest ago is dropped from memory; it stays on disk and is reloaded if its
 * user returns.
 */
public class SessionManager {

    private static final Logger LOG = Logger.getLogger(SessionManager.class.getName());

    /**
     * Session ids become file names, so only the exact shape of a random UUID is accepted.
     * Anything else (for example "../../secret") is rejected before it gets near the disk.
     */
    private static final Pattern SESSION_ID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** One user's conversation. */
    public record Session(String id, ConversationManager conversation) {}

    private final GilgameshBot bot;
    private final Path directory;
    private final int maxSessions;
    private final long maxAgeMs;

    /** Sessions in memory, least recently used first. */
    private final LinkedHashMap<String, Session> live = new LinkedHashMap<>(16, 0.75f, true);

    /**
     * @param directory   folder for the saved conversations, one file per session
     * @param maxSessions how many sessions to keep in memory at once
     * @param maxAgeMs    saved conversations older than this are not restored (0 = always)
     */
    public SessionManager(GilgameshBot bot, Path directory, int maxSessions, long maxAgeMs) {
        this.bot = bot;
        this.directory = directory;
        this.maxSessions = Math.max(1, maxSessions);
        this.maxAgeMs = maxAgeMs;
        deleteExpiredFiles();
    }

    /** True if the text has the exact shape of a session id. */
    public static boolean isValidId(String id) {
        return id != null && SESSION_ID.matcher(id).matches();
    }

    /**
     * Returns the session with this id, creating it (or reloading it from disk) if needed.
     *
     * @param requestedId the id the client sent, or null/blank to start a new session
     * @throws IllegalArgumentException if the id is not a valid session id
     */
    public synchronized Session open(String requestedId) {
        String id;
        if (requestedId == null || requestedId.isBlank()) {
            id = UUID.randomUUID().toString();
        } else {
            id = requestedId.trim().toLowerCase();
            if (!isValidId(id)) {
                throw new IllegalArgumentException("Invalid session id");
            }
        }

        Session session = live.get(id);
        if (session == null) {
            session = load(id);
            live.put(id, session);
            evictOldest();
        }
        return session;
    }

    /**
     * Finds an existing session without creating one.
     *
     * @return the session, or empty if there is no conversation with this id in memory or on disk
     * @throws IllegalArgumentException if the id is not a valid session id
     */
    public synchronized Optional<Session> find(String requestedId) {
        String id = requestedId == null ? "" : requestedId.trim().toLowerCase();
        if (!isValidId(id)) {
            throw new IllegalArgumentException("Invalid session id");
        }
        if (live.containsKey(id) || Files.isRegularFile(fileFor(id))) {
            return Optional.of(open(id));
        }
        return Optional.empty();
    }

    /**
     * Forgets a session: clears its memory and deletes its saved file.
     *
     * @return true if there was such a session
     * @throws IllegalArgumentException if the id is not a valid session id
     */
    public synchronized boolean delete(String requestedId) {
        Optional<Session> session = find(requestedId);
        if (session.isEmpty()) {
            return false;
        }
        session.get().conversation().resetContext(); // also deletes the saved file
        live.remove(session.get().id());
        return true;
    }

    /** How many sessions are currently held in memory. */
    public synchronized int liveCount() {
        return live.size();
    }

    private Path fileFor(String id) {
        return directory.resolve(id + ".json");
    }

    private Session load(String id) {
        ConversationContext context = bot.getConfig().newContext();
        ConversationStore store = new ConversationStore(fileFor(id), maxAgeMs);
        store.load().ifPresent(snapshot -> context.restore(snapshot, bot.getRepository()::findByTag));

        ConversationManager conversation = bot.newConversation(context);
        conversation.setStore(store);
        return new Session(id, conversation);
    }

    /** Drops the least recently used sessions from memory; their files stay on disk. */
    private void evictOldest() {
        Iterator<Map.Entry<String, Session>> oldestFirst = live.entrySet().iterator();
        while (live.size() > maxSessions && oldestFirst.hasNext()) {
            oldestFirst.next();
            oldestFirst.remove();
        }
    }

    /** Removes saved conversations that are too old to be restored, so the folder cannot grow forever. */
    private void deleteExpiredFiles() {
        if (maxAgeMs <= 0 || !Files.isDirectory(directory)) {
            return;
        }
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                if (Files.getLastModifiedTime(file).toMillis() < cutoff) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not clean up old sessions in " + directory, e);
        }
    }
}
