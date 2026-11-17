package com.gilgamesh.web;

import com.gilgamesh.GilgameshBot;
import com.gilgamesh.GilgameshConfig;
import com.gilgamesh.response.WeatherClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @TempDir
    Path tempDir;

    private GilgameshBot bot;

    @BeforeEach
    void setUp() {
        WeatherClient weather = city -> new WeatherClient.Report(city, "Testland", 20.0, 5.0, 0);
        bot = new GilgameshBot(GilgameshConfig.defaults(), weather, name -> Optional.empty());
    }

    private SessionManager manager(int maxSessions) {
        return new SessionManager(bot, tempDir, maxSessions, 3_600_000);
    }

    @Test
    void acceptsOnlyUuidShapedIds() {
        assertTrue(SessionManager.isValidId("123e4567-e89b-12d3-a456-426614174000"));
        assertFalse(SessionManager.isValidId("123E4567-E89B-12D3-A456-426614174000"), "ids are normalised to lower case first");
        assertFalse(SessionManager.isValidId("../etc/passwd"));
        assertFalse(SessionManager.isValidId("123e4567-e89b-12d3-a456-426614174000.json"));
        assertFalse(SessionManager.isValidId(""));
        assertFalse(SessionManager.isValidId(null));

        SessionManager sessions = manager(10);
        assertThrows(IllegalArgumentException.class, () -> sessions.open("../../x"));
        assertThrows(IllegalArgumentException.class, () -> sessions.find("nope"));
        assertEquals("123e4567-e89b-12d3-a456-426614174000",
                sessions.open("123E4567-E89B-12D3-A456-426614174000").id());
    }

    @Test
    void newSessionsGetDistinctIdsAndSeparateMemory() {
        SessionManager sessions = manager(10);
        SessionManager.Session first = sessions.open(null);
        SessionManager.Session second = sessions.open("  ");
        assertNotEquals(first.id(), second.id());

        first.conversation().processMessage("my name is Sam");
        assertEquals("Sam", first.conversation().getContext().getUserName());
        assertNull(second.conversation().getContext().getUserName());

        assertSame(first, sessions.open(first.id()), "the same id returns the same conversation");
    }

    @Test
    void sessionsDroppedFromMemoryComeBackFromDisk() {
        SessionManager sessions = manager(2);
        SessionManager.Session first = sessions.open(null);
        first.conversation().processMessage("my name is Sam");
        first.conversation().processMessage("tell me about Rust");

        sessions.open(null).conversation().processMessage("hello");
        sessions.open(null).conversation().processMessage("hello");
        assertEquals(2, sessions.liveCount(), "only the limit is kept in memory");

        SessionManager.Session restored = sessions.open(first.id());
        assertNotSame(first, restored, "it was dropped from memory and reloaded");
        assertEquals("Sam", restored.conversation().getContext().getUserName());
        assertTrue(restored.conversation().processMessage("who created it?").contains("Graydon Hoare"));
    }

    @Test
    void conversationsSurviveAServerRestart() {
        SessionManager before = manager(10);
        SessionManager.Session session = before.open(null);
        session.conversation().processMessage("tell me about Kotlin");

        SessionManager afterRestart = manager(10);
        Optional<SessionManager.Session> found = afterRestart.find(session.id());
        assertTrue(found.isPresent());
        assertEquals("Kotlin", found.get().conversation().getContext().getCurrentTopic());
    }

    @Test
    void findDoesNotCreateAndDeleteRemovesTheFile() {
        SessionManager sessions = manager(10);
        String unknown = "123e4567-e89b-12d3-a456-426614174000";
        assertTrue(sessions.find(unknown).isEmpty());
        assertEquals(0, sessions.liveCount());
        assertFalse(sessions.delete(unknown));

        SessionManager.Session session = sessions.open(null);
        session.conversation().processMessage("hello");
        Path file = tempDir.resolve(session.id() + ".json");
        assertTrue(Files.exists(file));

        assertTrue(sessions.delete(session.id()));
        assertFalse(Files.exists(file));
        assertTrue(sessions.find(session.id()).isEmpty());
    }

    @Test
    void oldSavedConversationsAreCleanedUpAtStartup() throws Exception {
        SessionManager sessions = manager(10);
        SessionManager.Session session = sessions.open(null);
        session.conversation().processMessage("hello");
        Path file = tempDir.resolve(session.id() + ".json");
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - 7_200_000));

        manager(10); // "restart" with a one-hour limit
        assertFalse(Files.exists(file));
    }

    @Test
    void manyConversationsAtOnceStayCorrect() throws Exception {
        SessionManager sessions = manager(50);
        String[] languages = {"Rust", "Kotlin", "Python", "Java", "Swift", "Ruby", "Scala", "Perl"};
        String[] creators = {"Graydon Hoare", "JetBrains", "Guido van Rossum", "James Gosling",
                "Chris Lattner", "Matsumoto", "Martin Odersky", "Larry Wall"};

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<String>> results = new ArrayList<>();
        try {
            for (int round = 0; round < 5; round++) {
                for (int i = 0; i < languages.length; i++) {
                    final int n = i;
                    results.add(pool.submit(() -> {
                        SessionManager.Session session = sessions.open(null);
                        session.conversation().processMessage("tell me about " + languages[n]);
                        String answer = session.conversation().processMessage("who created it?");
                        return answer.contains(creators[n]) ? "ok" : languages[n] + " -> " + answer;
                    }));
                }
            }
            List<String> wrong = new ArrayList<>();
            for (Future<String> result : results) {
                if (!"ok".equals(result.get())) wrong.add(result.get());
            }
            assertEquals(Collections.emptyList(), wrong, "answers got mixed up between conversations");
        } finally {
            pool.shutdownNow();
        }
    }
}
