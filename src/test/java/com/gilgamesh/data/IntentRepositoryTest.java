package com.gilgamesh.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntentRepositoryTest {

    @Test
    void loadsDefaultIntents() {
        IntentRepository repository = new IntentRepository();
        assertTrue(repository.getIntents().size() >= 48);
        repository.getIntents().forEach(intent -> {
            assertNotNull(intent.getTag());
            assertFalse(intent.getPatterns().isEmpty(), "No patterns for " + intent.getTag());
            assertFalse(intent.getResponses().isEmpty(), "No responses for " + intent.getTag());
        });
    }

    @Test
    void missingResourceFailsWithClearMessage() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> new IntentRepository("/does-not-exist.json"));
        assertTrue(e.getMessage().contains("does-not-exist.json"));
    }

    @Test
    void malformedJsonFails() {
        assertThrows(RuntimeException.class, () -> new IntentRepository("/intents-malformed.json"));
    }

    @Test
    void emptyIntentListFails() {
        assertThrows(RuntimeException.class, () -> new IntentRepository("/intents-empty.json"));
    }
}
