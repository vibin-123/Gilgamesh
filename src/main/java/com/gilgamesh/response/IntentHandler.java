package com.gilgamesh.response;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.model.Intent;

/**
 * Interface for handling different types of intents dynamically.
 */
public interface IntentHandler {
    /**
     * Determines if this handler can process the given intent.
     * @param intent The matched intent (can be null for fallbacks)
     * @return true if this handler should handle the intent, false otherwise
     */
    boolean canHandle(Intent intent);

    /**
     * Generates a response for the given intent.
     * @param intent The matched intent
     * @return The response string
     */
    String handle(Intent intent);

    /**
     * Generates a response with access to the user's message and conversation context.
     * Handlers that need the user's text (e.g. a city for weather) override this;
     * by default it delegates to {@link #handle(Intent)}.
     *
     * @param intent    The matched intent
     * @param userInput The (reference-resolved) user message
     * @param context   Active conversation context (may be null)
     */
    default String handle(Intent intent, String userInput, ConversationContext context) {
        return handle(intent);
    }

    /**
     * Generates a response that may carry structured data for a UI (see {@link HandlerReply}).
     * Handlers with data to offer override this; by default it wraps the text response.
     */
    default HandlerReply respond(Intent intent, String userInput, ConversationContext context) {
        return HandlerReply.text(handle(intent, userInput, context));
    }
}
