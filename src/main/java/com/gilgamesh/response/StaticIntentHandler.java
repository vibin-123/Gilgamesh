package com.gilgamesh.response;

import com.gilgamesh.model.Intent;

import java.util.List;
import java.util.Random;

/**
 * Handles all intents that rely on static responses defined in intents.json,
 * as well as unmatched fallback inputs.
 */
public class StaticIntentHandler implements IntentHandler {

    private final Random random = new Random();

    private static final List<String> FALLBACK_RESPONSES = List.of(
            "I'm not sure I understand. Could you rephrase that?",
            "Hmm, I didn't quite catch that. Try asking in a different way!",
            "I'm still learning! I didn't understand that — try asking about something else.",
            "Sorry, I couldn't figure out what you mean. Type 'help' to see what I can do!",
            "That's beyond my understanding right now. Can you try rephrasing?"
    );

    @Override
    public boolean canHandle(Intent intent) {
        // This is the fallback handler; it handles everything that wasn't 
        // intercepted by previous handlers.
        return true;
    }

    @Override
    public String handle(Intent intent) {
        if (intent == null) {
            return pickRandom(FALLBACK_RESPONSES);
        }
        return pickRandom(intent.getResponses());
    }

    private String pickRandom(List<String> options) {
        if (options == null || options.isEmpty()) {
            return "I don't have a response for that right now.";
        }
        int index = random.nextInt(options.size());
        return options.get(index);
    }
}
