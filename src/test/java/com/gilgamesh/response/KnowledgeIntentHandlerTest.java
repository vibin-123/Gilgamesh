package com.gilgamesh.response;

import com.gilgamesh.data.KnowledgeBase;
import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.Entity;
import com.gilgamesh.memory.PendingQuestion;
import com.gilgamesh.model.Intent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeIntentHandlerTest {

    private final KnowledgeIntentHandler handler = new KnowledgeIntentHandler(KnowledgeBase.loadDefault());
    private final Intent about = intent(KnowledgeIntentHandler.ABOUT);
    private final Intent creator = intent(KnowledgeIntentHandler.CREATOR);
    private final Intent advantages = intent(KnowledgeIntentHandler.ADVANTAGES);

    private static Intent intent(String tag) {
        return new Intent(tag, List.of("x"), List.of("x"));
    }

    @Test
    void handlesOnlyEntityIntents() {
        assertTrue(handler.canHandle(about));
        assertTrue(handler.canHandle(creator));
        assertTrue(handler.canHandle(advantages));
        assertFalse(handler.canHandle(intent("weather")));
        assertFalse(handler.canHandle(null));
    }

    @Test
    void answersFromTheSubjectInTheMessage() {
        assertTrue(handler.handle(creator, "who created Rust?", null).contains("Graydon Hoare"));
        assertTrue(handler.handle(creator, "who created Java?", null).contains("Gosling"));
        assertTrue(handler.handle(about, "what is Kotlin", null).contains("Kotlin"));
        assertTrue(handler.handle(advantages, "advantages of Go", null).contains("goroutines"));
    }

    @Test
    void fallsBackToTheSubjectOfTheConversation() {
        ConversationContext context = new ConversationContext();
        context.update("tell me about python", "Python is...", about,
                List.of(new Entity("Python", Entity.EntityType.LANGUAGE)));

        String reply = handler.handle(creator, "who is the creator", context);

        assertTrue(reply.contains("Guido van Rossum"), reply);
    }

    @Test
    void answersForEverySubjectNamed() {
        String reply = handler.handle(creator, "who created Java and Python", null);
        assertTrue(reply.contains("Gosling") && reply.contains("Guido"), reply);
    }

    @Test
    void asksWhichSubjectAndRemembersTheQuestion() {
        ConversationContext context = new ConversationContext();

        String reply = handler.handle(advantages, "what are the advantages", context);

        assertEquals(KnowledgeIntentHandler.ASK_ENTITY_PROMPT, reply);
        PendingQuestion pending = context.peekPendingQuestion();
        assertNotNull(pending);
        assertEquals(PendingQuestion.SLOT_ENTITY, pending.slot());
        assertEquals("advantages of Swift", String.format(pending.queryTemplate(), "Swift"));
    }
}
