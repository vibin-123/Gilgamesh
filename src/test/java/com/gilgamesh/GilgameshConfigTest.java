package com.gilgamesh;

import com.gilgamesh.data.IntentRepository;
import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.ConversationStore;
import com.gilgamesh.nlp.IntentClassifier;
import com.gilgamesh.nlp.TextPreprocessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GilgameshConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void bundledFileMatchesTheDefaultsInCode() {
        GilgameshConfig config = GilgameshConfig.defaults();

        assertEquals(IntentClassifier.DEFAULT_CONFIDENCE_THRESHOLD, config.threshold());
        assertEquals(IntentClassifier.DEFAULT_CONTEXT_BOOST, config.contextBoost());
        assertEquals(IntentClassifier.DEFAULT_OVERLAP_WEIGHT, config.overlapWeight());
        assertEquals(IntentClassifier.DEFAULT_COSINE_WEIGHT, config.cosineWeight());
        assertEquals(ConversationManager.DEFAULT_AMBIGUITY_MARGIN, config.ambiguityMargin());
        assertEquals(ConversationContext.DEFAULT_MAX_HISTORY_SIZE, config.historySize());
        assertEquals(ConversationContext.DEFAULT_MAX_SESSION_TURNS, config.maxSessionTurns());
        assertEquals(ConversationContext.DEFAULT_INACTIVITY_TIMEOUT_MS, config.inactivityTimeoutMs());
        assertEquals(ConversationStore.DEFAULT_MAX_AGE_MS, config.sessionMaxAgeMs());
        assertEquals(ConversationStore.defaultPath(), config.sessionPath());
    }

    @Test
    void userFileOverridesOnlyWhatItLists() throws Exception {
        Path file = tempDir.resolve("my.properties");
        Files.writeString(file, "classifier.threshold=0.55\nmemory.historySize=25\n");

        GilgameshConfig config = GilgameshConfig.load(file);

        assertEquals(0.55, config.threshold());
        assertEquals(25, config.historySize());
        assertEquals(IntentClassifier.DEFAULT_CONTEXT_BOOST, config.contextBoost(), "unlisted settings keep their default");
        assertEquals(file.toString(), config.getSource());
    }

    @Test
    void badValuesFallBackToDefaultsInsteadOfFailing() {
        GilgameshConfig config = GilgameshConfig.of(Map.of(
                GilgameshConfig.THRESHOLD, "high",          // not a number
                GilgameshConfig.CONTEXT_BOOST, "50",        // out of range
                GilgameshConfig.HISTORY_SIZE, "0",          // below minimum
                "classifier.treshold", "0.9"));             // misspelled key

        assertEquals(IntentClassifier.DEFAULT_CONFIDENCE_THRESHOLD, config.threshold());
        assertEquals(IntentClassifier.DEFAULT_CONTEXT_BOOST, config.contextBoost());
        assertEquals(ConversationContext.DEFAULT_MAX_HISTORY_SIZE, config.historySize());
    }

    @Test
    void namedConfigFileMustExist() {
        Path missing = tempDir.resolve("nope.properties");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> GilgameshConfig.load(missing));
        assertTrue(e.getMessage().contains("nope.properties"));
    }

    @Test
    void sessionPathCanBeSetAndExpandsHome() {
        assertEquals(Path.of("data", "chat.json"),
                GilgameshConfig.of(Map.of(GilgameshConfig.SESSION_PATH, "data/chat.json")).sessionPath());

        Path inHome = GilgameshConfig.of(Map.of(GilgameshConfig.SESSION_PATH, "~/bot/chat.json")).sessionPath();
        assertTrue(inHome.startsWith(System.getProperty("user.home")), inHome.toString());
    }

    @Test
    void settingsReachTheClassifierAndTheContext() {
        GilgameshConfig config = GilgameshConfig.of(Map.of(
                GilgameshConfig.THRESHOLD, "0.99",
                GilgameshConfig.HISTORY_SIZE, "3",
                GilgameshConfig.INACTIVITY_MINUTES, "2"));

        IntentClassifier classifier = new IntentClassifier(new IntentRepository(), new TextPreprocessor());
        assertNotNull(classifier.classify("what is the weather like today"));
        config.applyTo(classifier);
        assertEquals(0.99, classifier.getConfidenceThreshold());
        assertNull(classifier.classify("what is the weather like today"), "a near-impossible threshold rejects a loose match");

        ConversationContext context = config.newContext();
        assertEquals(3, context.getMaxHistorySize());
        assertEquals(120_000, context.getInactivityTimeoutMs());
    }

    @Test
    void zeroWeightsAreReplacedByDefaults() {
        GilgameshConfig config = GilgameshConfig.of(Map.of(
                GilgameshConfig.OVERLAP_WEIGHT, "0", GilgameshConfig.COSINE_WEIGHT, "0"));
        IntentClassifier classifier = new IntentClassifier(new IntentRepository(), new TextPreprocessor());

        assertDoesNotThrow(() -> config.applyTo(classifier));
        assertNotNull(classifier.classify("tell me a joke"));
    }

    @Test
    void readsConfigPathFromCommandLine() {
        assertNull(Main.configFileFrom(new String[]{"--debug"}));
        assertEquals(Path.of("a.properties"), Main.configFileFrom(new String[]{"--config", "a.properties"}));
        assertEquals(Path.of("b.properties"), Main.configFileFrom(new String[]{"--fresh", "--config=b.properties"}));
        assertThrows(IllegalArgumentException.class, () -> Main.configFileFrom(new String[]{"--config"}));
    }
}
