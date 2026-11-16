package com.gilgamesh.response;

import com.gilgamesh.data.IntentRepository;
import com.gilgamesh.model.Intent;
import com.gilgamesh.nlp.IntentClassifier;
import com.gilgamesh.nlp.TextPreprocessor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HelpIntentHandlerTest {

    private final IntentRepository repository = new IntentRepository();
    private final HelpIntentHandler handler = new HelpIntentHandler(repository);

    @Test
    void listsEveryCategoryAndExampleFromTheIntentData() {
        Map<String, List<String>> examples = repository.examplesByCategory();
        assertTrue(examples.size() >= 4, "expected several help categories");

        String help = handler.handle(repository.findByTag("help"));

        for (Map.Entry<String, List<String>> category : examples.entrySet()) {
            assertTrue(help.contains(category.getKey()), "missing category " + category.getKey());
            for (String example : category.getValue()) {
                assertTrue(help.contains(example), "missing example " + example);
            }
        }
    }

    @Test
    void everyAdvertisedExampleIsUnderstoodAsItsOwnIntent() {
        IntentClassifier classifier = new IntentClassifier(repository, new TextPreprocessor());
        List<String> broken = new ArrayList<>();
        for (Intent intent : repository.getIntents()) {
            if (intent.getExample() == null) continue;
            Intent result = classifier.classify(intent.getExample());
            if (result == null || !intent.getTag().equals(result.getTag())) {
                broken.add("\"" + intent.getExample() + "\" should be " + intent.getTag()
                        + " but was " + (result == null ? "not understood" : result.getTag()));
            }
        }
        assertTrue(broken.isEmpty(), "Help advertises examples the bot gets wrong:\n" + String.join("\n", broken));
    }

    @Test
    void handlesOnlyHelp() {
        assertTrue(handler.canHandle(repository.findByTag("help")));
        assertFalse(handler.canHandle(repository.findByTag("weather")));
        assertFalse(handler.canHandle(null));
    }
}
