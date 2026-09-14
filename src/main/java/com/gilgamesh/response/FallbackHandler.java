package com.gilgamesh.response;

import com.gilgamesh.memory.ConversationContext;

import java.util.List;
import java.util.Random;

/**
 * FallbackHandler — Generates contextual, progressive fallback responses.
 *
 * NLP Concept: Progressive Fallback Strategy
 * -------------------------------------------
 * When an input is unrecognized, returning a static "I don't understand" repeatedly leads to
 * poor user experience. Progressive fallbacks offer escalating levels of assistance:
 *   - Level 1: Polite rephrase prompt
 *   - Level 2: Topic & capability suggestions
 *   - Level 3+: Direct help menu directive
 */
public class FallbackHandler {

    private int consecutiveFallbacks = 0;
    private final Random random = new Random();

    private static final List<String> LEVEL_1_FALLBACKS = List.of(
        "I'm not quite sure I understood that. Could you try rephrasing?",
        "I didn't catch that. Could you say it in a different way?",
        "Hmm, I'm not familiar with that query yet. How else can I help?"
    );

    private static final List<String> LEVEL_2_FALLBACKS = List.of(
        "I'm still learning! You can ask me about NLP concepts, the current time, weather forecasts, or who created me.",
        "I couldn't match your input to my known intents. Try asking about weather, time, or what NLP stands for."
    );

    private static final List<String> LEVEL_3_FALLBACKS = List.of(
        "I seem to be having trouble understanding your requests. Type 'help' to see a full list of things I can answer!",
        "Let's get back on track! Try typing 'help' or asking 'What can you do?'"
    );

    public String generateFallbackResponse(ConversationContext context) {
        consecutiveFallbacks++;

        if (consecutiveFallbacks == 1) {
            return LEVEL_1_FALLBACKS.get(random.nextInt(LEVEL_1_FALLBACKS.size()));
        } else if (consecutiveFallbacks == 2) {
            return LEVEL_2_FALLBACKS.get(random.nextInt(LEVEL_2_FALLBACKS.size()));
        } else {
            return LEVEL_3_FALLBACKS.get(random.nextInt(LEVEL_3_FALLBACKS.size()));
        }
    }

    public void reset() {
        consecutiveFallbacks = 0;
    }

    public int getConsecutiveFallbacks() {
        return consecutiveFallbacks;
    }
}
