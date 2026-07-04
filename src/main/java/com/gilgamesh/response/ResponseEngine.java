package com.gilgamesh.response;

import com.gilgamesh.model.Intent;

import java.util.List;
import java.util.Random;

/**
 * ResponseEngine — Selects and returns a response for a matched (or unmatched) intent.
 *
 * NLP Concept: Response Generation / Natural Language Generation (NLG)
 * --------------------------------------------------------------------
 * In a full NLP pipeline, after understanding the user's intent (NLU — Natural
 * Language Understanding), the system must produce a response (NLG — Natural
 * Language Generation). Response strategies range from simple to complex:
 *
 *   1. Template-based: Pick from pre-written responses (what we do here) ★
 *   2. Retrieval-based: Search a corpus for the most relevant existing response
 *   3. Generative: Use a language model to compose a novel response (e.g., GPT)
 *
 * Our approach uses template-based selection with randomization:
 *   - Each intent has multiple pre-written responses in intents.json
 *   - We randomly select one to add conversational variety
 *   - If no intent was matched, we return a fallback message
 *
 * Why randomize? Repeating the same response every time feels robotic.
 * Adding variety (even from a small pool) makes the chatbot feel more
 * natural and engaging. This is a basic but effective UX technique used
 * in production chatbots alongside more sophisticated NLG.
 */
public class ResponseEngine {

    private final Random random = new Random();

    /**
     * Fallback responses used when no intent could be confidently matched.
     *
     * In production systems, unmatched inputs might trigger:
     *   - A clarification question ("Could you rephrase that?")
     *   - A handoff to a human agent
     *   - Logging for future training data collection
     *
     * Here we provide friendly fallback messages that encourage the user
     * to try different phrasing.
     */
    private static final List<String> FALLBACK_RESPONSES = List.of(
            "I'm not sure I understand. Could you rephrase that?",
            "Hmm, I didn't quite catch that. Try asking in a different way!",
            "I'm still learning! I didn't understand that — try asking about something else.",
            "Sorry, I couldn't figure out what you mean. Type 'help' to see what I can do!",
            "That's beyond my understanding right now. Can you try rephrasing?"
    );

    /**
     * Generates a response based on the classified intent.
     *
     * @param intent The matched intent (or null if no match was found)
     * @return A response string to display to the user
     */
    public String generateResponse(Intent intent) {
        if (intent == null) {
            // No intent matched — use fallback
            return pickRandom(FALLBACK_RESPONSES);
        }

        // Pick a random response from the matched intent's response list
        return pickRandom(intent.getResponses());
    }

    /**
     * Selects a random element from a list.
     *
     * Using Random ensures varied responses across conversations.
     * In a more advanced system, you might track recently used responses
     * to avoid immediate repetition (a "response cooldown" mechanism).
     *
     * @param options The list of possible responses
     * @return A randomly selected response
     */
    private String pickRandom(List<String> options) {
        int index = random.nextInt(options.size());
        return options.get(index);
    }
}
