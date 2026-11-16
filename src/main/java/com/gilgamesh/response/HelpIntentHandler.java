package com.gilgamesh.response;

import com.gilgamesh.data.IntentRepository;
import com.gilgamesh.model.Intent;

import java.util.List;
import java.util.Map;

/**
 * Answers "help" by listing what the bot can do, built from the intent data itself:
 * every intent in intents.json that has an "example" is shown under its "category".
 * Teaching the bot something new and giving it an example makes it appear here
 * automatically, so the help text can never go stale.
 */
public class HelpIntentHandler implements IntentHandler {

    private final IntentRepository repository;

    public HelpIntentHandler(IntentRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean canHandle(Intent intent) {
        return intent != null && "help".equals(intent.getTag());
    }

    @Override
    public String handle(Intent intent) {
        Map<String, List<String>> examples = repository.examplesByCategory();
        if (examples.isEmpty()) {
            return "Just type naturally and I'll do my best to understand.";
        }

        StringBuilder help = new StringBuilder("Here's what you can ask me:");
        for (Map.Entry<String, List<String>> category : examples.entrySet()) {
            help.append("\n  ").append(category.getKey()).append(": ");
            help.append(String.join(", ", category.getValue().stream().map(e -> "\"" + e + "\"").toList()));
        }
        help.append("\nYou can follow up with \"it\" (\"who created it?\"), and say \"back to ...\" to return to an earlier subject.");
        return help.toString();
    }
}
