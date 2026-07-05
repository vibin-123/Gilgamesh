package com.gilgamesh.response;

import com.gilgamesh.model.Intent;

import java.util.List;

/**
 * ResponseEngine — Delegates response generation to a chain of IntentHandlers.
 */
public class ResponseEngine {

    private final List<IntentHandler> handlers;

    public ResponseEngine(List<IntentHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * Generates a response based on the classified intent by finding the first
     * matching handler.
     *
     * @param intent The matched intent (or null if no match was found)
     * @return A response string to display to the user
     */
    public String generateResponse(Intent intent) {
        for (IntentHandler handler : handlers) {
            if (handler.canHandle(intent)) {
                return handler.handle(intent);
            }
        }
        
        // Fallback if no handler matches (though StaticIntentHandler should catch everything)
        return "I'm not sure how to respond to that.";
    }
}
