package com.gilgamesh.memory;

import com.gilgamesh.model.Intent;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConversationContextTest {

    private Gson gson;

    @BeforeEach
    public void setUp() {
        gson = new Gson();
    }

    private Intent createMockIntent(String tag) {
        String json = String.format("{\"tag\":\"%s\",\"patterns\":[],\"responses\":[]}", tag);
        return gson.fromJson(json, Intent.class);
    }

    @Test
    public void testUpdateAndBasicState() {
        ConversationContext context = new ConversationContext(5, 10, 60000);
        Intent intent = createMockIntent("test_topic");

        context.update("Hello", "Hi there!", intent);

        assertEquals("Hello", context.getLastUserMessage());
        assertEquals("Hi there!", context.getLastBotResponse());
        assertEquals(intent, context.getLastDetectedIntent());
        assertEquals("test_topic", context.getCurrentTopic());
        assertEquals(1, context.getTurnCount());

        List<ConversationTurn> history = context.getHistory();
        assertEquals(1, history.size());
        assertEquals("Hello", history.get(0).getUserMessage());
        assertEquals("Hi there!", history.get(0).getBotResponse());
        assertEquals(intent, history.get(0).getDetectedIntent());
    }

    @Test
    public void testTopicPersistence() {
        ConversationContext context = new ConversationContext(5, 10, 60000);
        Intent intent = createMockIntent("weather");

        context.update("What is the weather?", "It's sunny.", intent);
        assertEquals("weather", context.getCurrentTopic());

        // Subsequent message with no matched intent should preserve the last topic
        context.update("Tell me more.", "Checking...", null);
        assertEquals("weather", context.getCurrentTopic());
    }

    @Test
    public void testSlidingHistoryWindow() {
        // maxHistorySize = 3
        ConversationContext context = new ConversationContext(3, 10, 60000);

        context.update("M1", "R1", null);
        context.update("M2", "R2", null);
        context.update("M3", "R3", null);

        assertEquals(3, context.getHistory().size());
        assertEquals("M1", context.getHistory().get(0).getUserMessage());

        // Add 4th message - should discard M1
        context.update("M4", "R4", null);
        List<ConversationTurn> history = context.getHistory();
        assertEquals(3, history.size());
        assertEquals("M2", history.get(0).getUserMessage());
        assertEquals("M3", history.get(1).getUserMessage());
        assertEquals("M4", history.get(2).getUserMessage());
    }

    @Test
    public void testTurnLimitExpiration() {
        // maxSessionTurns = 2
        ConversationContext context = new ConversationContext(5, 2, 60000);

        context.update("M1", "R1", null);
        assertEquals(1, context.getTurnCount());
        assertFalse(context.getHistory().isEmpty());

        // Second turn reaches limit. On the start of the next interaction, or getter access,
        // it should detect expiration and clear.
        context.update("M2", "R2", null);
        // Exceeded/reached limit (turnCount is now 2 which equals maxSessionTurns)
        // Accessing any getter should trigger expiration check and reset state.
        assertNull(context.getLastUserMessage());
        assertNull(context.getLastBotResponse());
        assertEquals("unknown", context.getCurrentTopic());
        assertEquals(0, context.getTurnCount());
        assertTrue(context.getHistory().isEmpty());
    }

    @Test
    public void testInactivityTimeoutExpiration() throws InterruptedException {
        // inactivityTimeoutMs = 100ms
        ConversationContext context = new ConversationContext(5, 10, 100);

        context.update("Hello", "Hi", null);
        assertEquals("Hello", context.getLastUserMessage());

        // Sleep to exceed the 100ms timeout
        Thread.sleep(150);

        // Accessing the context now should clear it
        assertNull(context.getLastUserMessage());
        assertEquals(0, context.getTurnCount());
        assertTrue(context.getHistory().isEmpty());
    }

    @Test
    public void testReset() {
        ConversationContext context = new ConversationContext();
        context.update("Hello", "Hi", null);

        context.reset();

        assertNull(context.getLastUserMessage());
        assertNull(context.getLastBotResponse());
        assertNull(context.getLastDetectedIntent());
        assertEquals("unknown", context.getCurrentTopic());
        assertEquals(0, context.getTurnCount());
        assertTrue(context.getHistory().isEmpty());
    }
}
