package com.gilgamesh;

import com.gilgamesh.data.IntentRepository;
import com.gilgamesh.data.KnowledgeBase;
import com.gilgamesh.response.DateTimeIntentHandler;
import com.gilgamesh.response.HelpIntentHandler;
import com.gilgamesh.response.KnowledgeIntentHandler;
import com.gilgamesh.response.PlaceFinder;
import com.gilgamesh.response.UserNameIntentHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.TurnAnalyzer;
import com.gilgamesh.model.Intent;
import com.gilgamesh.nlp.EntityExtractor;
import com.gilgamesh.nlp.IntentClassifier;
import com.gilgamesh.nlp.ReferenceResolver;
import com.gilgamesh.nlp.TextPreprocessor;
import com.gilgamesh.response.IntentHandler;
import com.gilgamesh.response.ResponseEngine;
import com.gilgamesh.response.StaticIntentHandler;
import com.gilgamesh.response.WeatherClient;
import com.gilgamesh.response.WeatherIntentHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationManagerTest {

    /** Tuesday 17 November 2026, 12:00 UTC. */
    private static final Clock NOON_UTC = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);

    /** Every place is "found"; London is on UTC, everything else on Tokyo time (UTC+9). */
    private final PlaceFinder fakePlaces = name -> Optional.of(new PlaceFinder.Place(
            name, "Testland", 0, 0, name.equals("London") ? "UTC" : "Asia/Tokyo"));

    private final List<String> requestedCities = new ArrayList<>();
    private ConversationContext context;
    private boolean explode;

    private ConversationManager newManager() {
        IntentRepository repository = new IntentRepository();
        TextPreprocessor preprocessor = new TextPreprocessor();
        EntityExtractor extractor = new EntityExtractor();

        WeatherClient fakeClient = city -> {
            requestedCities.add(city);
            return new WeatherClient.Report(city, "Testland", 20.0, 10.0, 0);
        };
        // Handler that fails on demand, placed first to simulate a bug in a handler
        IntentHandler flaky = new IntentHandler() {
            @Override public boolean canHandle(Intent intent) { return explode && intent != null; }
            @Override public String handle(Intent intent) { throw new IllegalStateException("boom"); }
        };

        context = new ConversationContext();
        return new ConversationManager(extractor, new TurnAnalyzer(extractor), new ReferenceResolver(),
                new IntentClassifier(repository, preprocessor),
                new ResponseEngine(List.of(flaky, new WeatherIntentHandler(fakeClient),
                        new DateTimeIntentHandler(fakePlaces, NOON_UTC),
                        new UserNameIntentHandler(), new HelpIntentHandler(repository),
                        new KnowledgeIntentHandler(KnowledgeBase.loadDefault()), new StaticIntentHandler())),
                context);
    }

    private ConversationManager manager;

    @BeforeEach
    void setUp() {
        explode = false;
        requestedCities.clear();
        manager = newManager();
    }

    @Test
    void exceptionInOneTurnDoesNotBreakTheNext() {
        explode = true;
        assertEquals(ConversationManager.ERROR_RESPONSE, manager.processMessage("hello"));

        explode = false;
        String reply = manager.processMessage("hello");
        assertNotEquals(ConversationManager.ERROR_RESPONSE, reply);
        assertFalse(reply.isBlank());
    }

    @Test
    void conversationIsSavedAfterEachTurnAndClearedOnReset(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        com.gilgamesh.memory.ConversationStore store =
                new com.gilgamesh.memory.ConversationStore(dir.resolve("session.json"));
        manager.setStore(store);

        manager.processMessage("tell me about Java");
        var saved = store.load();
        assertTrue(saved.isPresent());
        assertEquals(1, saved.get().history.size());

        manager.resetContext();
        assertTrue(store.load().isEmpty());
    }

    // --- Knowledge base, subject topics, slot filling, clarification ---

    @Test
    void languageAddedOnlyAsDataIsAnsweredWithFollowUps() {
        String about = manager.processMessage("tell me about Rust");
        assertTrue(about.contains("Rust"), about);
        assertEquals("Rust", context.getCurrentTopic());

        String creator = manager.processMessage("who created it?");
        assertTrue(creator.contains("Graydon Hoare"), creator);
        assertEquals("Rust", context.getCurrentTopic(), "follow-up stays on the subject");

        String advantages = manager.processMessage("what are its advantages?");
        assertTrue(advantages.contains("Cargo"), advantages);
    }

    @Test
    void topicCanBeLeftAndResumedBySubjectName() {
        manager.processMessage("tell me about Rust");
        manager.processMessage("who created it?");

        manager.processMessage("what is the weather in Paris?");
        assertEquals("weather", context.getCurrentTopic());
        assertNotNull(context.findSuspendedTopic("Rust"), "Rust should be suspended, both turns under one topic");
        assertEquals(2, context.findSuspendedTopic("Rust").getTurns().size());

        String back = manager.processMessage("back to Rust");
        assertTrue(back.contains("back to Rust"), back);
        assertEquals("Rust", context.getCurrentTopic());

        String advantages = manager.processMessage("what are its advantages?");
        assertTrue(advantages.contains("Cargo"), advantages);
    }

    @Test
    void askingWithoutASubjectPromptsAndTheAnswerFillsTheSlot() {
        String ask = manager.processMessage("what are the advantages");
        assertEquals(KnowledgeIntentHandler.ASK_ENTITY_PROMPT, ask);

        String reply = manager.processMessage("Kotlin");
        assertTrue(reply.contains("null safety"), reply);
        assertNull(context.peekPendingQuestion(), "the question is answered");
    }

    @Test
    void pendingQuestionOnlyAppliesToTheNextMessage() {
        manager.processMessage("what is the weather like");
        String other = manager.processMessage("thanks");
        assertFalse(other.contains("couldn't find a place"), "'thanks' is not a city: " + other);
        assertTrue(requestedCities.isEmpty());

        // The question has lapsed, so a bare city is no longer read as its answer
        manager.processMessage("Berlin");
        assertTrue(requestedCities.isEmpty());
    }

    // --- Structured replies ---

    @Test
    void replyReportsHowTheMessageWasUnderstood() {
        manager.reply("tell me about Rust");
        BotReply reply = manager.reply("who created it?");

        assertTrue(reply.text().contains("Graydon Hoare"));
        assertEquals(BotReply.Kind.ANSWER, reply.kind());
        assertEquals("entity_creator", reply.intent());
        assertTrue(reply.confidence() > 0.4 && reply.confidence() <= 1.0);
        assertEquals("FOLLOW_UP", reply.messageType());
        assertEquals("who created Rust?", reply.resolvedInput());
        assertEquals("Rust", reply.topic());
        assertEquals(List.of(new BotReply.EntityInfo("Rust", "LANGUAGE")), reply.entities());
        assertNull(reply.pendingQuestion());
    }

    @Test
    void replyKindsDistinguishAnswersQuestionsFallbacksAndErrors() {
        BotReply weather = manager.reply("what's the weather in Paris?");
        assertEquals(BotReply.Kind.ANSWER, weather.kind());
        assertEquals(WeatherIntentHandler.DATA_TYPE, weather.dataType());
        assertEquals("Paris", ((WeatherClient.Report) weather.data()).city());

        BotReply fallback = manager.reply("zxqv blorp wibble");
        assertEquals(BotReply.Kind.FALLBACK, fallback.kind());
        assertNull(fallback.intent());

        BotReply ask = manager.reply("what is the weather like");
        assertEquals(BotReply.Kind.QUESTION, ask.kind());
        assertEquals("location", ask.pendingQuestion().slot());
        assertNull(ask.data());
        manager.reply("Berlin");

        manager.reply("tell me about Rust");
        manager.reply("what is the weather in Paris?");
        assertEquals(List.of("Rust"), manager.reply("hello").suspendedTopics().stream()
                .filter("Rust"::equals).toList(), "earlier topics are listed so a UI can offer to resume them");

        explode = true;
        BotReply error = manager.reply("hello");
        assertEquals(BotReply.Kind.ERROR, error.kind());
        assertEquals(ConversationManager.ERROR_RESPONSE, error.text());
    }

    // --- Date/time, user name, help ---

    @Test
    void timeQuestionsAreAnsweredForThePlaceAsked() {
        assertEquals("It's 12:00 PM.", manager.processMessage("what time is it?"));
        assertEquals("In Tokyo, Testland it's currently 9:00 PM.", manager.processMessage("what time is it in Tokyo?"));
        assertEquals("datetime", context.getCurrentTopic());

        // A bare follow-up continues the time question, not a weather one
        assertEquals("In London, Testland it's currently 12:00 PM.", manager.processMessage("and in London?"));
        assertTrue(requestedCities.isEmpty(), "no weather lookup for a time question");
    }

    @Test
    void unusualCityNamesStillReachTheRightIntent() {
        String weather = manager.processMessage("what's the weather in Ouagadougou?");
        assertEquals(List.of("Ouagadougou"), requestedCities);
        assertTrue(weather.contains("Ouagadougou"), weather);

        String time = manager.processMessage("what time is it in Ulaanbaatar");
        assertTrue(time.contains("Ulaanbaatar") && time.contains("9:00 PM"), time);
    }

    @Test
    void botLearnsAndRecallsTheUsersName() {
        String reply = manager.processMessage("my name is sam");
        assertTrue(reply.contains("Nice to meet you, Sam"), reply);

        manager.processMessage("tell me about Rust");
        assertEquals("Your name is Sam.", manager.processMessage("what is my name?"));

        // "my" vs "your" decides whose name is meant
        assertEquals("Your name is Sam.", manager.processMessage("tell me my name"));
        assertTrue(manager.processMessage("tell me your name").contains("Gilgamesh"));
    }

    @Test
    void askingForTheNameReadsTheNextMessageAsTheAnswer() {
        String ask = manager.processMessage("what is my name?");
        assertTrue(ask.contains(UserNameIntentHandler.ASK_NAME_PROMPT), ask);

        String reply = manager.processMessage("Priya");
        assertTrue(reply.contains("Nice to meet you, Priya"), reply);
        assertEquals("Priya", context.getUserName());
    }

    @Test
    void helpListsRealCapabilities() {
        String help = manager.processMessage("help");
        assertTrue(help.contains("weather in Paris"), help);
        assertTrue(help.contains("tell me about Rust"), help);
        assertTrue(help.contains("what is my name"), help);
    }

    @Test
    void nearTieAsksDidYouMeanAndAcceptsFirstOrSecond() {
        EntityExtractor extractor = new EntityExtractor();
        ConversationContext ctx = new ConversationContext();
        ConversationManager ambiguous = new ConversationManager(extractor, new TurnAnalyzer(extractor),
                new ReferenceResolver(),
                new IntentClassifier(new IntentRepository("/intents-ambiguous.json"), new TextPreprocessor()),
                new ResponseEngine(List.of(new StaticIntentHandler())), ctx);

        String question = ambiguous.processMessage("apple");
        assertTrue(question.contains("red apple") && question.contains("green apple"), question);
        assertNull(ctx.getLastDetectedIntent(), "nothing is guessed while asking");

        assertEquals("You picked green.", ambiguous.processMessage("second"));

        // A clear message is answered directly
        assertEquals("You picked red.", ambiguous.processMessage("red apple"));
    }

    @Test
    void weatherQueryUsesCityFromMessage() {
        String reply = manager.processMessage("what is the weather in Paris?");

        assertEquals(List.of("Paris"), requestedCities);
        assertTrue(reply.contains("Paris"), reply);
        assertTrue(reply.contains("20.0°C"), reply);
    }

    @Test
    void weatherFollowUpWithNewCity() {
        manager.processMessage("what is the weather in Paris?");
        String reply = manager.processMessage("and in London?");

        assertEquals(List.of("Paris", "London"), requestedCities);
        assertTrue(reply.contains("London"), reply);
    }

    @Test
    void bareCityAnswersTheAskCityPrompt() {
        String ask = manager.processMessage("what is the weather like");
        assertEquals(WeatherIntentHandler.ASK_CITY_PROMPT, ask);

        String reply = manager.processMessage("Berlin");

        assertEquals(List.of("Berlin"), requestedCities);
        assertTrue(reply.contains("Berlin"), reply);
    }
}
