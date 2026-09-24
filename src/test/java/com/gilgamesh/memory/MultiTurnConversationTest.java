package com.gilgamesh.memory;

import com.gilgamesh.ConversationManager;
import com.gilgamesh.data.IntentRepository;
import com.gilgamesh.nlp.EntityExtractor;
import com.gilgamesh.nlp.IntentClassifier;
import com.gilgamesh.nlp.ReferenceResolver;
import com.gilgamesh.nlp.TextPreprocessor;
import com.gilgamesh.response.DateTimeIntentHandler;
import com.gilgamesh.response.IntentHandler;
import com.gilgamesh.response.ResponseEngine;
import com.gilgamesh.response.StaticIntentHandler;
import com.gilgamesh.response.WeatherIntentHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MultiTurnConversationTest {

    private ConversationManager manager;
    private ConversationContext context;

    @BeforeEach
    public void setUp() {
        IntentRepository repository = new IntentRepository();
        TextPreprocessor preprocessor = new TextPreprocessor();
        ReferenceResolver referenceResolver = new ReferenceResolver();
        IntentClassifier classifier = new IntentClassifier(repository, preprocessor);
        List<IntentHandler> handlers = List.of(
                new DateTimeIntentHandler(),
                new WeatherIntentHandler(),
                new StaticIntentHandler());
        ResponseEngine responseEngine = new ResponseEngine(handlers);
        context = new ConversationContext();
        EntityExtractor entityExtractor = new EntityExtractor();
        TurnAnalyzer turnAnalyzer = new TurnAnalyzer(entityExtractor);

        manager = new ConversationManager(
                entityExtractor,
                turnAnalyzer,
                referenceResolver,
                classifier,
                responseEngine,
                context
        );
    }

    @Test
    public void testStandaloneMessage() {
        String response = manager.processMessage("hello");
        assertNotNull(response);
        assertFalse(response.isBlank());
        assertEquals(1, context.getTurnCount());
    }

    @Test
    public void testBasicTwoTurnConversation() {
        // Turn 1: Ask about Java
        String response1 = manager.processMessage("What is Java?");
        assertNotNull(response1);
        assertFalse(response1.isBlank());
        assertEquals("about_technology", context.getLastDetectedIntent().getTag(), "Turn 1 should match about_technology");

        // Turn 2: Follow-up pronoun "it" -> should resolve to "Who created Java?"
        String response2 = manager.processMessage("Who created it?");
        assertNotNull(response2);
        assertEquals("java_creator", context.getLastDetectedIntent().getTag(), "Turn 2 should match java_creator");
        assertTrue(response2.toLowerCase().contains("gosling") || response2.toLowerCase().contains("sun microsystems"),
                "Response 2 should identify James Gosling as the creator of Java");
    }

    @Test
    public void testThreeDependentTurns() {
        // Turn 1: Java
        String response1 = manager.processMessage("What is Java?");
        assertNotNull(response1);

        // Turn 2: Creator follow-up
        String response2 = manager.processMessage("Who created it?");
        assertNotNull(response2);
        assertTrue(response2.toLowerCase().contains("gosling"), "Turn 2 should identify James Gosling");

        // Turn 3: Advantages follow-up
        String response3 = manager.processMessage("What are its advantages?");
        assertNotNull(response3);
        assertTrue(response3.toLowerCase().contains("java") || response3.toLowerCase().contains("jvm") 
                || response3.toLowerCase().contains("platform") || response3.toLowerCase().contains("advantage"),
                "Turn 3 should describe Java advantages");
    }

    @Test
    public void testFollowUpAfterTopicChange() {
        // Turn 1: Java
        manager.processMessage("What is Java?");

        // Turn 2: Follow-up on Java
        String javaCreator = manager.processMessage("Who created it?");
        assertTrue(javaCreator.toLowerCase().contains("gosling"), "Turn 2 should be Gosling for Java");

        // Turn 3: Topic change to Python
        String pythonInfo = manager.processMessage("Tell me about Python.");
        assertNotNull(pythonInfo);
        assertTrue(pythonInfo.toLowerCase().contains("python"), "Turn 3 should describe Python");

        // Turn 4: Follow-up "its advantages" -> MUST refer to Python, not Java
        String pythonAdvantages = manager.processMessage("What are its advantages?");
        assertNotNull(pythonAdvantages);
        assertTrue(pythonAdvantages.toLowerCase().contains("python") || pythonAdvantages.toLowerCase().contains("readability")
                || pythonAdvantages.toLowerCase().contains("syntax") || pythonAdvantages.toLowerCase().contains("data science"),
                "Turn 4 should describe Python advantages, not Java");
    }

    @Test
    public void testContextExpirationByTurns() {
        // Configure context to expire after 2 turns
        context.setMaxSessionTurns(2);

        manager.processMessage("What is Java?");
        assertEquals(1, context.getTurnCount());

        manager.processMessage("Who created it?");
        // After 2 turns, context should expire upon the next action
        assertTrue(context.clearExpiredContext());
        assertEquals(0, context.getTurnCount());
    }

    @Test
    public void testContextExpirationByInactivity() throws InterruptedException {
        // Set short inactivity timeout: 25 milliseconds
        context.setInactivityTimeoutMs(25);

        manager.processMessage("What is Java?");
        assertEquals(1, context.getTurnCount());

        Thread.sleep(50);

        // After sleeping past timeout, context should expire
        assertTrue(context.clearExpiredContext());
        assertEquals(0, context.getTurnCount());
    }

    @Test
    public void testConversationReset() {
        manager.processMessage("What is Java?");
        manager.processMessage("Who created it?");
        assertEquals(2, context.getTurnCount());

        manager.resetContext();
        assertEquals(0, context.getTurnCount());
        assertNull(context.getActiveEntity());
        assertEquals("unknown", context.getCurrentTopic());
    }

    @Test
    public void testStandaloneQuestionAfterContextualConversation() {
        // Contextual interaction
        manager.processMessage("What is Java?");
        manager.processMessage("Who created it?");

        // Standalone question containing expletive "it": "What time is it?"
        String timeResponse = manager.processMessage("What time is it?");
        assertNotNull(timeResponse);
        // The time handler returns current date/time
        assertFalse(timeResponse.toLowerCase().contains("gosling"), "Time query should not return creator info");

        // Another standalone question: joke
        String jokeResponse = manager.processMessage("Tell me a joke");
        assertNotNull(jokeResponse);
        assertFalse(jokeResponse.isBlank());
    }

    @Test
    public void testGilgameshReferenceFollowUp() {
        // Turn 1: Bot identity
        manager.processMessage("What is Gilgamesh?");

        // Turn 2: Follow-up on Gilgamesh creator
        String response = manager.processMessage("Who developed it?");
        assertNotNull(response);
        assertTrue(response.toLowerCase().contains("developer") || response.toLowerCase().contains("built")
                || response.toLowerCase().contains("java"),
                "Follow-up on Gilgamesh should match creator intent");
    }

    @Test
    public void testSingleTurnIntentsRegression() {
        // Verify weather
        manager.processMessage("How is the weather?");
        assertEquals("weather", context.getLastDetectedIntent().getTag(), "Weather intent should match");

        // Verify thanks
        manager.processMessage("Thank you so much");
        assertEquals("thanks", context.getLastDetectedIntent().getTag(), "Thanks intent should match");

        // Verify help
        manager.processMessage("Can you help me?");
        assertEquals("help", context.getLastDetectedIntent().getTag(), "Help intent should match");
    }
}
