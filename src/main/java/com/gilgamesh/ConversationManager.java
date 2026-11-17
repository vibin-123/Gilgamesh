package com.gilgamesh;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.gilgamesh.data.KnowledgeBase;
import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.ConversationStore;
import com.gilgamesh.memory.Entity;
import com.gilgamesh.memory.MessageType;
import com.gilgamesh.memory.PendingQuestion;
import com.gilgamesh.memory.TopicState;
import com.gilgamesh.memory.TurnAnalyzer;
import com.gilgamesh.response.HandlerReply;
import com.gilgamesh.model.Intent;
import com.gilgamesh.nlp.Classification;
import com.gilgamesh.nlp.EntityExtractor;
import com.gilgamesh.nlp.IntentClassifier;
import com.gilgamesh.nlp.ReferenceResolver;
import com.gilgamesh.response.ResponseEngine;
import com.gilgamesh.response.UserNameIntentHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * ConversationManager — Orchestrates the multi-turn conversation pipeline.
 *
 * This class encapsulates the full turn lifecycle:
 *   1. Entity extraction from user input
 *   2. Slot filling: is this message the answer to a question the bot just asked?
 *   3. Message type analysis (standalone / follow-up / context-dependent / topic-change / topic-resume)
 *   4. Reference resolution (pronouns, noun phrases, possessives)
 *   5. Intent classification (hybrid weighted-Jaccard + TF-IDF + context boost)
 *   6. Clarification ("did you mean X or Y?") when two intents are nearly tied
 *   7. Response generation (handlers + fallback)
 *   8. Context state update (entities, topic, history)
 *
 * Decouples Main.java from internal pipeline coordination to allow independent
 * testing and future extensibility.
 */
public class ConversationManager {

    private static final Logger LOG = Logger.getLogger(ConversationManager.class.getName());
    static final String ERROR_RESPONSE = "Sorry, something went wrong on my side. Please try again.";

    /**
     * Two matching intents closer than this fraction of the top score count as a tie,
     * and the bot asks instead of guessing. Chosen from the "EVAL ambiguity" lines printed by
     * ClassifierEvaluationTest: at 5% the question fires on 3 answers that would have been
     * wrong and 1 that would have been right; wider margins interrupt correct answers too often.
     */
    public static final double DEFAULT_AMBIGUITY_MARGIN = 0.05;

    private double ambiguityMargin = DEFAULT_AMBIGUITY_MARGIN;

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
        return reply(userInput).text();
    }

    /**
     * Processes a single user message and returns the full reply: the text plus how the
     * message was understood (intent, confidence, entities, topic). One conversation handles
     * one message at a time.
     *
     * @param userInput Raw input from the user
     * @return The reply; never null, and never an exception (failures become an ERROR reply)
     */
    public synchronized BotReply reply(String userInput) {
        BotReply reply;
        try {
            reply = runPipeline(userInput);
        } catch (RuntimeException e) {
            // A failure in one turn must never take down the whole session
            LOG.log(Level.WARNING, "Error while processing message", e);
            return BotReply.error(ERROR_RESPONSE);
        }
        persist();
        return reply;
    }

    /**
     * Enables saving the conversation after every turn (and clearing it on reset).
     * Optional: without a store the conversation lives in memory only.
     */
    public void setStore(ConversationStore store) {
        this.store = store;
    }

    /**
     * Sets how close the top two intents must be before the bot asks "did you mean?".
     * A negative value turns clarification off.
     */
    public void setAmbiguityMargin(double ambiguityMargin) {
        this.ambiguityMargin = ambiguityMargin;
    }

    private void persist() {
        if (store == null) return;
        try {
            store.save(context.snapshot());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not persist conversation", e);
        }
    }

    private BotReply runPipeline(String userInput) {
        // Remember state from before this turn (topic suspension below clears it)
        String topicBefore = context.getCurrentTopic();
        // A pending question only ever applies to this one message
        PendingQuestion pending = context.takePendingQuestion();

        // Step 1: Extract entities from the raw user input
        List<Entity> turnEntities = new ArrayList<>(entityExtractor.extractEntities(userInput));

        // Step 2: Slot filling — is this the answer to what the bot just asked?
        String slotQuery = pending != null ? answerPendingQuestion(pending, userInput, turnEntities) : null;

        // Step 3: Analyze the message type and handle topic lifecycle events
        MessageType messageType = MessageType.STANDALONE;
        if (slotQuery == null) {
            messageType = turnAnalyzer.analyze(userInput, context);
            LOG.fine("[ConversationManager] Message type: " + messageType);

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
        }
        boolean usesContext = messageType == MessageType.FOLLOW_UP
                || messageType == MessageType.CONTEXT_DEPENDENT
                || messageType == MessageType.TOPIC_RESUME;

        // Step 4: Work out the full question being asked
        String resolvedInput = userInput;
        if (slotQuery != null) {
            resolvedInput = slotQuery;
        } else {
            if (usesContext) {
                // "who created it?" -> "who created Java?"
                resolvedInput = referenceResolver.resolveReferences(userInput, context);
            }
            // "and in London?" while talking about weather -> "weather in London"
            String placeQuery = completePlaceFollowUp(userInput, topicBefore, turnEntities);
            if (placeQuery != null) {
                resolvedInput = placeQuery;
            }
        }

        // Entities that only became visible after resolution ("it" -> Java) belong to this turn too
        if (!resolvedInput.equals(userInput)) {
            for (Entity entity : entityExtractor.extractEntities(resolvedInput)) {
                if (!turnEntities.contains(entity)) {
                    turnEntities.add(entity);
                }
            }
        }

        // Step 5: Intent classification on the resolved input.
        // Only follow-up style messages get the topic boost; standalone and topic-changing
        // messages are evaluated cleanly without prior topic bias.
        Classification classification = classifier.classifyDetailed(resolvedInput, usesContext ? context : null);
        Intent matchedIntent = classification.intent();

        // Steps 6-7: Response
        String response;
        HandlerReply handlerReply = null;
        BotReply.Kind kind;
        String clarification = slotQuery == null ? clarificationFor(classification, turnEntities) : null;
        if (clarification != null) {
            // Two intents are nearly tied: ask instead of guessing
            response = clarification;
            matchedIntent = null;
            kind = BotReply.Kind.CLARIFICATION;
        } else if (messageType == MessageType.TOPIC_RESUME) {
            // "back to Java": acknowledge the switch, then answer if the message also asked something
            if (matchedIntent != null) {
                handlerReply = responseEngine.respond(matchedIntent, resolvedInput, context);
            }
            response = "Sure, back to " + context.getCurrentTopic() + ". "
                    + (handlerReply != null ? handlerReply.text() : "What would you like to know?");
            kind = BotReply.Kind.ANSWER;
        } else {
            handlerReply = responseEngine.respond(matchedIntent, resolvedInput, context);
            response = handlerReply.text();
            kind = matchedIntent != null ? BotReply.Kind.ANSWER : BotReply.Kind.FALLBACK;
        }

        // The old pending question was taken at the top of the turn, so one present now was
        // asked by this turn's handler ("Which city?").
        PendingQuestion asked = context.peekPendingQuestion();
        if (asked != null && kind == BotReply.Kind.ANSWER) {
            kind = BotReply.Kind.QUESTION;
        }

        // Step 8: Update conversation context with entities, topic and turn info
        boolean understood = matchedIntent != null || messageType == MessageType.TOPIC_RESUME;
        String topicKey = understood ? subjectOf(turnEntities) : null;
        context.update(userInput, response, matchedIntent, turnEntities, topicKey);

        // Step 9: Report how the message was understood, for UIs that want to show it
        List<BotReply.EntityInfo> entities = new ArrayList<>();
        for (Entity entity : turnEntities) {
            entities.add(new BotReply.EntityInfo(entity.getName(), entity.getType().name()));
        }
        List<String> suspended = new ArrayList<>();
        for (TopicState topic : context.getSuspendedTopics()) {
            suspended.add(topic.getTopicId());
        }
        return new BotReply(
                response,
                kind,
                matchedIntent != null ? matchedIntent.getTag() : null,
                Math.min(1.0, classification.score()),
                classification.runnerUp() != null ? classification.runnerUp().getTag() : null,
                Math.min(1.0, classification.runnerUpScore()),
                entities,
                messageType.name(),
                resolvedInput,
                context.peekCurrentTopic(),
                suspended,
                context.getUserName(),
                asked != null ? new BotReply.PendingInfo(asked.slot(), asked.options()) : null,
                handlerReply != null ? handlerReply.dataType() : null,
                handlerReply != null ? handlerReply.data() : null);
    }

    /**
     * The subject a turn is about: its first entity that is a "thing" (a language, a person,
     * a concept). Places are excluded, because "weather in Paris" is about weather, not Paris.
     *
     * @return the subject's name, or null if the turn names no subject (the intent tag is used then)
     */
    private static String subjectOf(List<Entity> turnEntities) {
        for (Entity entity : turnEntities) {
            if (!entity.matchesType(Entity.EntityType.LOCATION)) {
                return entity.getName();
            }
        }
        return null;
    }

    // --- Slot filling ---

    /** A short reply made only of name-like characters (used for city and person-name answers). */
    private static final Pattern BARE_CITY = Pattern.compile("[A-Za-z][A-Za-z .'-]*");
    private static final Pattern WEATHER_WORDS = Pattern.compile("(?i)\\b(weather|forecast|temperature)\\b");
    private static final Pattern FIRST_CHOICE = Pattern.compile("(?i)^(?:the\\s+)?(?:first|1st|1|former)(?:\\s+one)?[.!]*$");
    private static final Pattern SECOND_CHOICE = Pattern.compile("(?i)^(?:the\\s+)?(?:second|2nd|2|latter)(?:\\s+one)?[.!]*$");

    /**
     * Tries to read the message as the answer to the question the bot just asked.
     *
     * @param turnEntities mutable list; a LOCATION is added when the answer is a city
     * @return the full question to run with the slot filled in (e.g. "weather in Paris"),
     *         or null if the message is not an answer and should be handled normally
     */
    private String answerPendingQuestion(PendingQuestion pending, String userInput, List<Entity> turnEntities) {
        String trimmed = userInput.trim();

        switch (pending.slot()) {
            case PendingQuestion.SLOT_CHOICE -> {
                List<String> options = pending.options();
                if (options.size() >= 1 && FIRST_CHOICE.matcher(trimmed).matches()) return options.get(0);
                if (options.size() >= 2 && SECOND_CHOICE.matcher(trimmed).matches()) return options.get(1);
                return null;
            }
            case PendingQuestion.SLOT_ENTITY -> {
                String subject = subjectOf(turnEntities);
                return subject != null ? String.format(pending.queryTemplate(), subject) : null;
            }
            case PendingQuestion.SLOT_NAME -> {
                // "Sam" or "Sam Lee"; a reply the bot understands as something else ("never mind", "no") is not a name
                if (!BARE_CITY.matcher(trimmed).matches() || trimmed.split("\\s+").length > 2
                        || classifier.classifyDetailed(trimmed, null).isMatch()) {
                    return null;
                }
                return UserNameIntentHandler.cleanName(trimmed)
                        .map(name -> String.format(pending.queryTemplate(), name))
                        .orElse(null);
            }
            case PendingQuestion.SLOT_LOCATION -> {
                // A short reply that is not itself something the bot understands ("no", "thanks") is the city
                if (!BARE_CITY.matcher(trimmed).matches() || trimmed.split("\\s+").length > 3
                        || WEATHER_WORDS.matcher(trimmed).find()
                        || classifier.classifyDetailed(trimmed, null).isMatch()) {
                    return null;
                }
                Optional<Entity> city = entityExtractor.toLocation(trimmed);
                if (city.isEmpty()) return null;
                turnEntities.add(city.get());
                return String.format(pending.queryTemplate(), city.get().getName());
            }
            default -> {
                return null;
            }
        }
    }

    private static final Pattern PLACE_QUESTION_WORDS =
            Pattern.compile("(?i)\\b(weather|forecast|temperature|time|date|day|clock)\\b");

    /**
     * If the conversation was about weather or the time, rewrites a bare "and in London?"
     * into the full question ("weather in London" / "time in London").
     *
     * @return the rewritten query, or null if this isn't such a follow-up
     */
    private String completePlaceFollowUp(String userInput, String topicBefore, List<Entity> turnEntities) {
        String question;
        if ("weather".equals(topicBefore)) {
            question = "weather in ";
        } else if ("datetime".equals(topicBefore)) {
            question = "time in ";
        } else {
            return null;
        }
        if (PLACE_QUESTION_WORDS.matcher(userInput).find()) {
            return null; // already a full question
        }
        for (Entity entity : turnEntities) {
            if (entity.matchesType(Entity.EntityType.LOCATION)) {
                return question + entity.getName();
            }
        }
        return null;
    }

    // --- Clarification ("did you mean") ---

    /**
     * If the top two intents are nearly tied, builds a "did you mean" question and
     * registers it as the pending question so "the first one" works as an answer.
     *
     * @return the question to ask, or null if the classification is clear enough
     */
    private String clarificationFor(Classification classification, List<Entity> turnEntities) {
        if (!classification.isAmbiguous(ambiguityMargin)) {
            return null;
        }
        String first = exampleOf(classification.topIntent(), turnEntities);
        String second = exampleOf(classification.runnerUp(), turnEntities);
        if (first == null || second == null) {
            return null;
        }
        context.setPendingQuestion(PendingQuestion.choice(List.of(first, second)));
        return "I'm not sure which you mean: \"" + first + "\" or \"" + second
                + "\"? You can say 'first' or 'second', or rephrase.";
    }

    /** A representative phrase for an intent: its first pattern, with {entity} filled in from this turn. */
    private static String exampleOf(Intent intent, List<Entity> turnEntities) {
        if (intent == null || intent.getPatterns() == null || intent.getPatterns().isEmpty()) {
            return null;
        }
        String example = intent.getPatterns().get(0);
        if (example.contains(KnowledgeBase.ENTITY_PLACEHOLDER)) {
            String subject = subjectOf(turnEntities);
            if (subject == null) return null;
            example = example.replace(KnowledgeBase.ENTITY_PLACEHOLDER, subject);
        }
        return example;
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
