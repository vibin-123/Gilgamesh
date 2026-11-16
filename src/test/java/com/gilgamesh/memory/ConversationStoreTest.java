package com.gilgamesh.memory;

import com.gilgamesh.model.Intent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConversationStoreTest {

    @TempDir
    Path tempDir;

    private final Intent javaIntent = new Intent("java_info", List.of("java"), List.of("Java is..."));
    private final Intent pythonIntent = new Intent("python_info", List.of("python"), List.of("Python is..."));
    private final Map<String, Intent> intents = Map.of("java_info", javaIntent, "python_info", pythonIntent);

    private ConversationContext populatedContext() {
        ConversationContext context = new ConversationContext();
        context.update("tell me about java", "Java is...", javaIntent,
                List.of(new Entity("Java", Entity.EntityType.LANGUAGE)));
        context.suspendCurrentTopic();
        context.update("tell me about python", "Python is...", pythonIntent,
                List.of(new Entity("Python", Entity.EntityType.LANGUAGE)));
        return context;
    }

    @Test
    void roundTripPreservesConversationState() {
        ConversationStore store = new ConversationStore(tempDir.resolve("session.json"));
        store.save(populatedContext().snapshot());

        Optional<SessionSnapshot> loaded = store.load();
        assertTrue(loaded.isPresent());

        ConversationContext restored = new ConversationContext();
        restored.restore(loaded.get(), intents::get);

        assertEquals("python_info", restored.getCurrentTopic());
        assertSame(pythonIntent, restored.getLastDetectedIntent());
        assertEquals("tell me about python", restored.getLastUserMessage());
        assertEquals("Python", restored.getActiveEntity().getName());
        assertEquals(2, restored.getHistory().size());
        assertSame(javaIntent, restored.getHistory().get(0).getDetectedIntent());
        assertSame(pythonIntent, restored.getHistory().get(1).getDetectedIntent());

        // The suspended Java topic survived and can still be resumed
        assertEquals(1, restored.getSuspendedTopics().size());
        assertTrue(restored.resumeTopic("java_info"));
        assertEquals("Java", restored.getMostRecentSingularNonPersonEntity().getName());
    }

    @Test
    void restoredSessionIsNotImmediatelyExpired() {
        ConversationStore store = new ConversationStore(tempDir.resolve("session.json"));
        SessionSnapshot snapshot = populatedContext().snapshot();
        snapshot.savedAtMillis = System.currentTimeMillis() - 60_000;
        store.save(snapshot);

        ConversationContext restored = new ConversationContext();
        restored.restore(store.load().orElseThrow(), intents::get);

        assertEquals("python_info", restored.getCurrentTopic());
        assertEquals(2, restored.getTurnCount());
    }

    @Test
    void pendingQuestionSurvivesARestart() {
        ConversationStore store = new ConversationStore(tempDir.resolve("session.json"));
        ConversationContext context = populatedContext();
        context.setPendingQuestion(new PendingQuestion("weather", PendingQuestion.SLOT_LOCATION, "weather in %s"));
        store.save(context.snapshot());

        ConversationContext restored = new ConversationContext();
        restored.restore(store.load().orElseThrow(), intents::get);

        PendingQuestion pending = restored.takePendingQuestion();
        assertNotNull(pending);
        assertEquals(PendingQuestion.SLOT_LOCATION, pending.slot());
        assertEquals("weather in %s", pending.queryTemplate());
        assertNull(restored.takePendingQuestion(), "taking it clears it");
    }

    @Test
    void missingFileMeansNoSession() {
        assertTrue(new ConversationStore(tempDir.resolve("nope.json")).load().isEmpty());
    }

    @Test
    void corruptFileIsIgnored() throws Exception {
        Path file = tempDir.resolve("session.json");
        Files.writeString(file, "{ this is not json");
        assertTrue(new ConversationStore(file).load().isEmpty());
    }

    @Test
    void wrongVersionIsIgnored() throws Exception {
        Path file = tempDir.resolve("session.json");
        Files.writeString(file, "{\"version\": 999}");
        assertTrue(new ConversationStore(file).load().isEmpty());
    }

    @Test
    void staleSessionIsIgnored() {
        ConversationStore store = new ConversationStore(tempDir.resolve("session.json"), 1_000);
        SessionSnapshot snapshot = populatedContext().snapshot();
        snapshot.savedAtMillis = System.currentTimeMillis() - 60_000;
        store.save(snapshot);

        assertTrue(store.load().isEmpty());
    }

    @Test
    void unknownIntentTagsRestoreAsNull() {
        ConversationStore store = new ConversationStore(tempDir.resolve("session.json"));
        store.save(populatedContext().snapshot());

        ConversationContext restored = new ConversationContext();
        restored.restore(store.load().orElseThrow(), tag -> null);

        assertNull(restored.getLastDetectedIntent());
        assertEquals("python_info", restored.getCurrentTopic());
    }

    @Test
    void saveCreatesParentDirectoryAndDeleteRemovesFile() {
        Path file = tempDir.resolve("nested").resolve("dir").resolve("session.json");
        ConversationStore store = new ConversationStore(file);

        store.save(populatedContext().snapshot());
        assertTrue(Files.exists(file));

        store.delete();
        assertFalse(Files.exists(file));
        store.delete(); // deleting a missing file is harmless
    }
}
