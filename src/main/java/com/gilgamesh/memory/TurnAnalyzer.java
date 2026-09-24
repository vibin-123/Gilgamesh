package com.gilgamesh.memory;

import com.gilgamesh.nlp.EntityExtractor;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TurnAnalyzer — Classifies user messages into MessageType categories.
 *
 * NLP Concept: Discourse Analysis / Turn Classification
 * -----------------------------------------------------
 * Analyzing the relationship between consecutive utterances in a dialog helps
 * determine how to apply conversational context. This analyzer uses linguistic
 * signals (pronouns, ellipsis, question fragments) and entity presence to
 * classify each turn.
 */
public class TurnAnalyzer {

    private final EntityExtractor entityExtractor;

    // Pronouns and demonstratives that signal a follow-up
    private static final Set<String> FOLLOW_UP_PRONOUNS = Set.of(
        "it", "its", "this", "that", "these", "those",
        "they", "them", "he", "she"
    );

    // Contextual noun phrases that signal follow-up
    private static final Set<String> CONTEXT_PHRASES = Set.of(
        "the language", "the project", "the chatbot", "the bot",
        "the creator", "the author", "the developer",
        "its advantages", "its features", "its benefits",
        "its disadvantages", "its pros", "its cons"
    );

    // Short follow-up fragments
    private static final Pattern SHORT_FRAGMENT_PATTERN = Pattern.compile(
        "(?i)^(and|but|also|what about|how about|tell me more|more|why|how)\\b"
    );

    // Question words that often start follow-ups
    private static final Pattern FOLLOW_UP_QUESTION_PATTERN = Pattern.compile(
        "(?i)^(who|what|when|where|why|how)\\s+(created|made|built|developed|invented|is|are|was|were|does|do|can|could|would|should)\\s+(it|its|this|that|they|them|he|she)\\b"
    );

    // Idiomatic standalone expressions that contain pronouns like "it" but are self-contained
    private static final List<Pattern> IDIOMATIC_STANDALONE_PATTERNS = List.of(
        Pattern.compile("(?i)\\bwhat\\s+(?:time|day|date)\\s+is\\s+it\\b"),
        Pattern.compile("(?i)\\bhow(?:'s|\\s+is)\\s+it\\s+going\\b"),
        Pattern.compile("(?i)\\bis\\s+it\\s+(?:going\\s+to\\s+)?(?:rain|raining|sunny|cloudy|cold|hot|snowing|warm|outside)\\b"),
        Pattern.compile("(?i)\\b(?:that's|that\\s+is)\\s+it\\b")
    );

    public TurnAnalyzer(EntityExtractor entityExtractor) {
        this.entityExtractor = entityExtractor;
    }

    /**
     * Analyzes the user input in the context of the conversation and determines
     * the message type.
     *
     * @param userInput The current user message
     * @param context   The active conversation context
     * @return The classified MessageType
     */
    public MessageType analyze(String userInput, ConversationContext context) {
        if (userInput == null || userInput.isBlank()) {
            return MessageType.STANDALONE;
        }

        // If no prior context exists, it must be standalone
        if (context == null || context.getTurnCount() == 0) {
            return MessageType.STANDALONE;
        }

        String inputLower = userInput.toLowerCase().trim();

        // Check for known idiomatic phrases with expletive "it" that are standalone
        for (Pattern pattern : IDIOMATIC_STANDALONE_PATTERNS) {
            if (pattern.matcher(inputLower).find()) {
                return MessageType.STANDALONE;
            }
        }

        // Check for new entities that differ from current context
        List<Entity> newEntities = entityExtractor.extractEntities(userInput);
        boolean hasNewEntity = hasNewEntityNotInContext(newEntities, context);
        boolean hasContextualSignals = hasContextualSignals(inputLower);

        // If we found a new, different entity AND there are no contextual references,
        // this is a topic change
        if (hasNewEntity && !hasContextualSignals) {
            return MessageType.TOPIC_CHANGE;
        }

        // Check for explicit follow-up patterns
        if (isFollowUpQuestion(inputLower)) {
            return MessageType.FOLLOW_UP;
        }

        // Check for contextual signals (pronouns, noun phrases)
        if (hasContextualSignals) {
            return MessageType.FOLLOW_UP;
        }

        // Check for short/fragmentary input that depends on context
        if (isContextDependentFragment(inputLower)) {
            return MessageType.CONTEXT_DEPENDENT;
        }

        // Default: standalone message
        return MessageType.STANDALONE;
    }

    /**
     * Checks if any extracted entities are new (not already in the context).
     */
    private boolean hasNewEntityNotInContext(List<Entity> newEntities, ConversationContext context) {
        if (newEntities.isEmpty()) return false;

        List<Entity> contextEntities = context.getRecentEntities();
        for (Entity newEntity : newEntities) {
            boolean isNew = true;
            for (Entity existing : contextEntities) {
                if (existing.getName().equalsIgnoreCase(newEntity.getName())) {
                    isNew = false;
                    break;
                }
            }
            if (isNew) return true;
        }
        return false;
    }

    /**
     * Checks if the input contains contextual signals (pronouns or noun phrases).
     */
    private boolean hasContextualSignals(String inputLower) {
        // Check for follow-up pronouns
        for (String pronoun : FOLLOW_UP_PRONOUNS) {
            if (containsWholeWord(inputLower, pronoun)) {
                return true;
            }
        }

        // Check for contextual noun phrases
        for (String phrase : CONTEXT_PHRASES) {
            if (inputLower.contains(phrase)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the input is a follow-up question pattern.
     */
    private boolean isFollowUpQuestion(String inputLower) {
        return FOLLOW_UP_QUESTION_PATTERN.matcher(inputLower).find();
    }

    /**
     * Checks if the input is a short fragment that likely depends on context.
     */
    private boolean isContextDependentFragment(String inputLower) {
        return SHORT_FRAGMENT_PATTERN.matcher(inputLower).find();
    }

    /**
     * Checks if a word exists as a whole word in the input.
     */
    private boolean containsWholeWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }
}
