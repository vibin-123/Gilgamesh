package com.gilgamesh.response;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.model.Intent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepeatIntentHandlerTest {

    private final Intent repeat = new Intent("repeat", List.of("say that again"), List.of("x"));
    private final RepeatIntentHandler handler = new RepeatIntentHandler();

    @Test
    void repeatsThePreviousReply() {
        ConversationContext context = new ConversationContext();
        context.update("tell me a joke", "Why do programmers prefer dark mode?", null);

        String reply = handler.handle(repeat, "say that again", context);

        assertTrue(reply.contains("Why do programmers prefer dark mode?"), reply);
    }

    @Test
    void saysSoWhenThereIsNothingToRepeat() {
        String reply = handler.handle(repeat, "say that again", new ConversationContext());
        assertTrue(reply.contains("haven't said anything"), reply);
        assertTrue(handler.canHandle(repeat));
        assertFalse(handler.canHandle(null));
    }
}
