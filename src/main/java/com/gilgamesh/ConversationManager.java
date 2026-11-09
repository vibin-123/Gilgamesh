package com.gilgamesh;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.ConversationStore;
import com.gilgamesh.memory.Entity;
import com.gilgamesh.memory.MessageType;
import com.gilgamesh.memory.TurnAnalyzer;
import com.gilgamesh.model.Intent;
import com.gilgamesh.nlp.EntityExtractor;
import com.gilgamesh.nlp.IntentClassifier;
import com.gilgamesh.nlp.ReferenceResolver;
import com.gilgamesh.response.ResponseEngine;
import com.gilgamesh.response.WeatherIntentHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

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

    private static final Logger LOG = Logger.getLogger(ConversationManager.class.getName());
    static final String ERROR_RESPONSE = "Sorry, something went wrong on my side. Please try again.";

    private final EntityExtractor entityExtractor;
    private final TurnAnalyzer turnAnalyzer;
    private final ReferenceResolver referenceResolver;
    private final IntentClassifier classifier;
    private final ResponseEngine responseEngine;
    private final ConversationContext context;
    private ConversationStore store;

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
        String response;
        try {
            response = runPipeline(userInput);
        } catch (RuntimeException e) {
            // A failure in one turn must never take down the whole session
            LOG.log(Level.WARNING, "Error while processing message", e);
            return ERROR_RESPONSE;
        }
        persist();
        return response;
    }

    /**
     * Enables saving the conversation after every turn (and clearing it on reset).
     * Optional: without a store the conversation lives in memory only.
     */
    public void setStore(ConversationStore store) {
        this.store = store;
    }

    private void persist() {
        if (store == null) return;
        try {
            store.save(context.snapshot());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not persist conversation", e);
        }
    }

    private String runPipeline(String userInput) {
        // Remember state from before this turn (topic suspension below clears it)
        String topicBefore = context.getCurrentTopic();
        String lastBotResponse = context.getLastBotResponse();

        // Step 1: Extract entities from the raw user input
        List<Entity> extractedEntities = new ArrayList<>(entityExtractor.extractEntities(userInput));

        // Step 2: Analyze the message type (standalone, follow-up, context-dependent, topic-change, topic-resume)
        MessageType messageType = turnAnalyzer.analyze(userInput, context);
        LOG.fine("[ConversationManager] Message type: " + messageType);

        // Step 3: Handle topic lifecycle events
        if (messageType == MessageType.TOPIC_CHANGE) {
            // Auto-suspend the current topic before switching
            context.suspendCurrentTopic();
            // Also evict any expired topics from the stack
            context.clearExpiredTopics();
        } else if (messageType == MessageType.TOPIC_RESUME) {
            // Identify which suspended topic the user wants to resume
            String resumeTarget = turnAnalyzer.extractResumeTarget(userInput.toLowerCase().trim());
            String topicId = turnAnalyzer.findMatchingSuspendedTopicId(resumeTarget, context);
            if (topicId != null) {
                context.resumeTopic(topicId);
                LOG.fine("[ConversationManager] Resumed topic: " + topicId);
            } else {
                LOG.fine("[ConversationManager] Resume target not found, treating as standalone.");
                messageType = MessageType.STANDALONE;
            }
        }

        // Step 4: Resolve references using conversation context
        // Only perform reference resolution for follow-up, context-dependent, and resumed messages
        String resolvedInput = userInput;
        if (messageType == MessageType.FOLLOW_UP || messageType == MessageType.CONTEXT_DEPENDENT
                || messageType == MessageType.TOPIC_RESUME) {
            resolvedInput = referenceResolver.resolveReferences(userInput, context);
        }

        // Step 4b: Weather follow-ups ("and in London?", or a bare city after "Which city?")
        String weatherQuery = completeWeatherFollowUp(userInput, topicBefore, lastBotResponse, extractedEntities);
        if (weatherQuery != null) {
            resolvedInput = weatherQuery;
        }

        // Step 5: Intent classification on the resolved input
        // Only apply contextual topic boost for follow-up, context-dependent, and resumed messages;
        // standalone and topic-changing messages are evaluated cleanly without prior topic bias.
        Intent matchedIntent;
        if (messageType == MessageType.FOLLOW_UP || messageType == MessageType.CONTEXT_DEPENDENT
                || messageType == MessageType.TOPIC_RESUME) {
            matchedIntent = classifier.classify(resolvedInput, context);
        } else {
            matchedIntent = classifier.classify(resolvedInput, null);
        }

        // Step 6: Response generation
        String response = responseEngine.generateResponse(matchedIntent, resolvedInput, context);

        // Step 7: Update conversation context with entities and turn info
        context.update(userInput, response, matchedIntent, extractedEntities);

        return response;
    }

    private static final Pattern BARE_CITY = Pattern.compile("[A-Za-z][A-Za-z .'-]*");
    private static final Pattern WEATHER_WORDS = Pattern.compile("(?i)\\b(weather|forecast|temperature)\\b");

    /**
     * If the conversation was about weather, rewrites a short follow-up into a full
     * weather query so the classifier routes it to the weather intent:
     * a bare city after the bot asked "Which city...?", or "and in London?".
     *
     * @param extractedEntities mutable list; a LOCATION is added when a bare city is recognized
     * @return the rewritten query, or null if this isn't a weather follow-up
     */
    private String completeWeatherFollowUp(String userInput, String topicBefore, String lastBotResponse,
                                           List<Entity> extractedEntities) {
        if (!"weather".equals(topicBefore) || WEATHER_WORDS.matcher(userInput).find()) {
            return null;
        }

        // Answer to "Which city would you like the weather for?"
        String trimmed = userInput.trim();
        if (lastBotResponse != null && lastBotResponse.contains(WeatherIntentHandler.ASK_CITY_PROMPT)
                && BARE_CITY.matcher(trimmed).matches() && trimmed.split("\\s+").length <= 3) {
            Optional<Entity> city = entityExtractor.toLocation(trimmed);
            if (city.isPresent()) {
                extractedEntities.add(city.get());
                return "weather in " + city.get().getName();
            }
        }

        // "and in London?"
        for (Entity entity : extractedEntities) {
            if (entity.matchesType(Entity.EntityType.LOCATION)) {
                return "weather in " + entity.getName();
            }
        }
        return null;
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
        if (store != null) {
            store.delete();
        }
    }
}
