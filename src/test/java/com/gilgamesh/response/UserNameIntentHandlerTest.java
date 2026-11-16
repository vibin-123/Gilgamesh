package com.gilgamesh.response;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.PendingQuestion;
import com.gilgamesh.model.Intent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserNameIntentHandlerTest {

    private final UserNameIntentHandler handler = new UserNameIntentHandler();
    private final Intent introduction = new Intent(UserNameIntentHandler.INTRODUCTION, List.of("my name is"), List.of("x"));
    private final Intent recall = new Intent(UserNameIntentHandler.RECALL, List.of("what is my name"), List.of("x"));

    @Test
    void extractsNamesFromIntroductions() {
        assertEquals(Optional.of("Sam"), UserNameIntentHandler.extractName("my name is sam"));
        assertEquals(Optional.of("Alex"), UserNameIntentHandler.extractName("Hi, my name is Alex!"));
        assertEquals(Optional.of("Sam Lee"), UserNameIntentHandler.extractName("you can call me Sam Lee"));
        assertEquals(Optional.of("Jordan"), UserNameIntentHandler.extractName("I go by Jordan"));
        assertEquals(Optional.of("Sam"), UserNameIntentHandler.extractName("my name is Sam and I like Java"));
        assertEquals(Optional.of("O'Brien"), UserNameIntentHandler.extractName("call me O'Brien"));
    }

    @Test
    void doesNotInventNames() {
        assertTrue(UserNameIntentHandler.extractName("my name is").isEmpty());
        assertTrue(UserNameIntentHandler.extractName("my name is not important").isEmpty());
        assertTrue(UserNameIntentHandler.extractName("what is your name").isEmpty());
        assertTrue(UserNameIntentHandler.extractName(null).isEmpty());
    }

    @Test
    void remembersTheNameAndRecallsIt() {
        ConversationContext context = new ConversationContext();

        String greeting = handler.handle(introduction, "my name is sam", context);
        assertTrue(greeting.contains("Sam"), greeting);
        assertEquals("Sam", context.getUserName());

        assertEquals("Your name is Sam.", handler.handle(recall, "what is my name", context));
    }

    @Test
    void asksWhenTheNameIsMissing() {
        ConversationContext context = new ConversationContext();

        String reply = handler.handle(recall, "what is my name", context);
        assertTrue(reply.contains(UserNameIntentHandler.ASK_NAME_PROMPT), reply);

        PendingQuestion pending = context.peekPendingQuestion();
        assertNotNull(pending);
        assertEquals(PendingQuestion.SLOT_NAME, pending.slot());

        assertEquals(UserNameIntentHandler.ASK_NAME_PROMPT, handler.handle(introduction, "my name is", context));
    }

    @Test
    void responsesUseTheNameOnlyWhenKnown() {
        assertEquals("Hello, Sam! How are you?", ResponseTemplateProcessor.interpolateUserName("Hello{, user_name}! How are you?", "Sam"));
        assertEquals("Hello! How are you?", ResponseTemplateProcessor.interpolateUserName("Hello{, user_name}! How are you?", null));
        assertEquals("Bye Sam. See you, Sam!", ResponseTemplateProcessor.interpolateUserName("Bye {user_name}. See you{, user_name}!", "Sam"));
        assertEquals("No placeholder {time}", ResponseTemplateProcessor.interpolateUserName("No placeholder {time}", "Sam"));
    }

    @Test
    void theNameOutlivesAnExpiredConversationButNotAReset() throws InterruptedException {
        ConversationContext context = new ConversationContext();
        context.setInactivityTimeoutMs(20);
        handler.handle(introduction, "my name is Sam", context);
        context.update("my name is Sam", "Nice to meet you", introduction);

        Thread.sleep(50);
        assertTrue(context.clearExpiredContext(), "the conversation expires");
        assertEquals("Sam", context.getUserName(), "but the bot still knows who it is talking to");

        context.reset();
        assertNull(context.getUserName());
    }
}
