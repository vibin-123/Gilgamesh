package com.gilgamesh.nlp;

import com.gilgamesh.data.IntentRepository;
import com.gilgamesh.model.Intent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class IntentClassifierTest {

    private static IntentRepository repository;
    private static IntentClassifier classifier;

    @BeforeAll
    static void setUp() {
        repository = new IntentRepository();
        classifier = new IntentClassifier(repository, new TextPreprocessor());
    }

    /**
     * Patterns that currently classify to a different intent, mostly because they
     * overlap with another intent or reduce to very few tokens after stopword removal.
     * This is a ratchet: new misses fail the test, and so does fixing one without
     * removing it from this set. Shrink it by tuning intents.json / the pipeline.
     */
    private static final Set<String> KNOWN_MISSES = Set.of(
            "what can you do", "what do you do", "what are you for",
            "you're right", "you are bad", "my name is",
            "what should I do", "joke", "humor me", "funny");

    @Test
    void everyPatternClassifiesToItsOwnIntentExceptKnownMisses() {
        Set<String> missedPatterns = new TreeSet<>();
        List<String> details = new ArrayList<>();
        for (Intent intent : repository.getIntents()) {
            for (String pattern : intent.getPatterns()) {
                Intent result = classifier.classify(pattern);
                String got = result == null ? "null" : result.getTag();
                if (!intent.getTag().equals(got)) {
                    missedPatterns.add(pattern);
                    details.add("'" + pattern + "' expected " + intent.getTag() + " but got " + got);
                }
            }
        }

        Set<String> newMisses = new TreeSet<>(missedPatterns);
        newMisses.removeAll(KNOWN_MISSES);
        assertTrue(newMisses.isEmpty(), "New misclassified patterns: " + newMisses + "\n" + String.join("\n", details));

        Set<String> nowFixed = new TreeSet<>(KNOWN_MISSES);
        nowFixed.removeAll(missedPatterns);
        assertTrue(nowFixed.isEmpty(), "These known misses now pass; remove from KNOWN_MISSES: " + nowFixed);
    }

    @Test
    void gibberishFallsBack() {
        assertNull(classifier.classify("zxqv blorp wibble"));
    }

    @Test
    void emptyAndBlankInputFallBack() {
        assertNull(classifier.classify(""));
        assertNull(classifier.classify("   "));
    }

    @Test
    void classificationIsDeterministic() {
        Intent first = classifier.classify("what time is it");
        Intent second = classifier.classify("what time is it");
        assertNotNull(first);
        assertSame(first, second);
    }
}
