package com.gilgamesh.response;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.model.Intent;

/**
 * Handles "say that again" by repeating the bot's previous reply from conversation memory.
 */
public class RepeatIntentHandler implements IntentHandler {

    @Override
    public boolean canHandle(Intent intent) {
        return intent != null && "repeat".equals(intent.getTag());
    }

    @Override
    public String handle(Intent intent) {
        return "I haven't said anything yet! Ask me something first.";
    }

    @Override
    public String handle(Intent intent, String userInput, ConversationContext context) {
        String last = context != null ? context.getLastBotResponse() : null;
        if (last == null || last.isBlank()) {
            return handle(intent);
        }
        return "Sure, I said: " + last;
    }
}
