package com.gilgamesh;

import com.gilgamesh.data.IntentRepository;
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
                new ResponseEngine(List.of(flaky, new WeatherIntentHandler(fakeClient), new StaticIntentHandler())),
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
