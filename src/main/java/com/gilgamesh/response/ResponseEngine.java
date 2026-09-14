package com.gilgamesh.response;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.model.Intent;

import java.util.List;

/**
 * ResponseEngine — Generates dynamic, non-repeating, template-processed bot responses.
 */
public class ResponseEngine {

    private final List<IntentHandler> handlers;
    private final ResponseTemplateProcessor templateProcessor;
    private final FallbackHandler fallbackHandler;

    public ResponseEngine(List<IntentHandler> handlers) {
        this.handlers = handlers;
        this.templateProcessor = new ResponseTemplateProcessor();
        this.fallbackHandler = new FallbackHandler();
    }

    /**
     * Generates a response based on classified intent and conversation context.
     *
     * @param intent The matched intent (or null if fallback)
     * @param context Active conversation context
     * @return A response string to display to user
     */
    public String generateResponse(Intent intent, ConversationContext context) {
        if (intent == null) {
            return fallbackHandler.generateFallbackResponse(context);
        }

        // On matched intent, reset consecutive fallback count
        fallbackHandler.reset();

        for (IntentHandler handler : handlers) {
            if (handler.canHandle(intent)) {
                // If handler is static handler, use templateProcessor for variations
                if (handler instanceof StaticIntentHandler) {
                    String selected = templateProcessor.selectResponse(intent, context);
                    if (selected != null) {
                        return selected;
                    }
                }
                String resp = handler.handle(intent);
                return templateProcessor.interpolateVariables(resp, context);
            }
        }
        
        return fallbackHandler.generateFallbackResponse(context);
    }

    public String generateResponse(Intent intent) {
        return generateResponse(intent, null);
    }

    public FallbackHandler getFallbackHandler() {
        return fallbackHandler;
    }
}
