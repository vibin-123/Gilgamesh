package com.gilgamesh.response;

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
}
