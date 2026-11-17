package com.gilgamesh.response;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.model.Intent;

import java.util.List;

/**
 * ResponseEngine — Generates dynamic, non-repeating, template-processed bot responses.
 *
 * The engine itself keeps no per-conversation state when a {@link ConversationContext} is
 * passed in (fallback escalation and "don't repeat the last response" are stored there),
 * so one engine can safely serve many conversations.
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
        return generateResponse(intent, null, context);
    }

    /**
     * Generates a response, passing the user's message through to handlers that need it.
     *
     * @param intent    The matched intent (or null if fallback)
     * @param userInput The (reference-resolved) user message, may be null
     * @param context   Active conversation context
     */
    public String generateResponse(Intent intent, String userInput, ConversationContext context) {
        return respond(intent, userInput, context).text();
    }

    /**
     * Generates a response together with any structured data the handler attached.
     *
     * @param intent    The matched intent (or null if fallback)
     * @param userInput The (reference-resolved) user message, may be null
     * @param context   Active conversation context
     */
    public HandlerReply respond(Intent intent, String userInput, ConversationContext context) {
        if (intent == null) {
            return HandlerReply.text(fallbackHandler.generateFallbackResponse(context));
        }

        // On matched intent, reset consecutive fallback count
        if (context != null) {
            context.resetFallbacks();
        } else {
            fallbackHandler.reset();
        }

        for (IntentHandler handler : handlers) {
            if (handler.canHandle(intent)) {
                // If handler is static handler, use templateProcessor for variations
                if (handler instanceof StaticIntentHandler) {
                    String selected = templateProcessor.selectResponse(intent, context);
                    if (selected != null) {
                        return HandlerReply.text(selected);
                    }
                }
                HandlerReply reply = handler.respond(intent, userInput, context);
                return new HandlerReply(templateProcessor.interpolateVariables(reply.text(), context),
                        reply.dataType(), reply.data());
            }
        }

        return HandlerReply.text(fallbackHandler.generateFallbackResponse(context));
    }

    public String generateResponse(Intent intent) {
        return generateResponse(intent, null, null);
    }

    public FallbackHandler getFallbackHandler() {
        return fallbackHandler;
    }
}
