package com.gilgamesh;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.Entity;
import com.gilgamesh.memory.MessageType;
import com.gilgamesh.memory.TurnAnalyzer;
import com.gilgamesh.model.Intent;
import com.gilgamesh.nlp.EntityExtractor;
import com.gilgamesh.nlp.IntentClassifier;
import com.gilgamesh.nlp.ReferenceResolver;
import com.gilgamesh.response.ResponseEngine;

import java.util.List;

/**
 * ConversationManager — Orchestrates the multi-turn conversation pipeline.
 *
 * This class encapsulates the full turn lifecycle:
 *   1. Entity extraction from user input
 *   2. Message type analysis (standalone / follow-up / context-dependent / topic-change)
 *   3. Reference resolution (pronouns, noun phrases, possessives)
 *   4. Intent classification (hybrid Jaccard + TF-IDF + context boost)
 *   5. Response generation (template processing + fallback handling)
 *   6. Context state update (entities, topic, history)
 *
 * Decouples Main.java from internal pipeline coordination to allow independent
 * testing and future extensibility.
 */
public class ConversationManager {

    private final EntityExtractor entityExtractor;
    private final TurnAnalyzer turnAnalyzer;
    private final ReferenceResolver referenceResolver;
    private final IntentClassifier classifier;
    private final ResponseEngine responseEngine;
    private final ConversationContext context;

    public ConversationManager(EntityExtractor entityExtractor,
                                TurnAnalyzer turnAnalyzer,
                                ReferenceResolver referenceResolver,
                                IntentClassifier classifier,
                                ResponseEngine responseEngine,
                                ConversationContext context) {
        this.entityExtractor = entityExtractor;
        this.turnAnalyzer = turnAnalyzer;
        this.referenceResolver = referenceResolver;
        this.classifier = classifier;
        this.responseEngine = responseEngine;
        this.context = context;
    }

    /**
     * Processes a single user message through the full multi-turn pipeline.
     *
     * @param userInput Raw input from the user
     * @return The bot's response string
     */
    public String processMessage(String userInput) {
        // Step 1: Extract entities from the raw user input
        List<Entity> extractedEntities = entityExtractor.extractEntities(userInput);

        // Step 2: Analyze the message type (standalone, follow-up, context-dependent, topic-change)
        MessageType messageType = turnAnalyzer.analyze(userInput, context);
        System.out.println("[ConversationManager] Message type: " + messageType);

        // Step 3: Resolve references using conversation context
        // Only perform reference resolution for follow-up and context-dependent messages
        String resolvedInput = userInput;
        if (messageType == MessageType.FOLLOW_UP || messageType == MessageType.CONTEXT_DEPENDENT) {
            resolvedInput = referenceResolver.resolveReferences(userInput, context);
        }

        // Step 4: Intent classification on the resolved input
        // Only apply contextual topic boost for follow-up and context-dependent messages;
        // standalone and topic-changing messages are evaluated cleanly without prior topic bias.
        Intent matchedIntent;
        if (messageType == MessageType.FOLLOW_UP || messageType == MessageType.CONTEXT_DEPENDENT) {
            matchedIntent = classifier.classify(resolvedInput, context);
        } else {
            matchedIntent = classifier.classify(resolvedInput, null);
        }

        // Step 5: Response generation
        String response = responseEngine.generateResponse(matchedIntent, context);

        // Step 6: Update conversation context with entities and turn info
        context.update(userInput, response, matchedIntent, extractedEntities);

        return response;
    }

    /**
     * Returns the active conversation context (for testing and inspection).
     */
    public ConversationContext getContext() {
        return context;
    }

    /**
     * Resets the conversation context.
     */
    public void resetContext() {
        context.reset();
    }
}
