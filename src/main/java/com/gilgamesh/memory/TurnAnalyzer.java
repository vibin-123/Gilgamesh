package com.gilgamesh.memory;

import com.gilgamesh.nlp.EntityExtractor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
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

    // Topic resume signal patterns — capture the target topic name
    private static final List<Pattern> RESUME_SIGNAL_PATTERNS = List.of(
        Pattern.compile("(?i)(?:going|go|get|let'?s?\\s+go)\\s+back\\s+to\\s+(.+)"),
        Pattern.compile("(?i)back\\s+to\\s+(.+)"),
        Pattern.compile("(?i)(?:return|returning)\\s+to\\s+(.+)"),
        Pattern.compile("(?i)(?:resume|revisit|continue\\s+with)\\s+(.+)"),
        Pattern.compile("(?i)(?:about|regarding)\\s+(.+?)\\s+again"),
        Pattern.compile("(?i)(?:what|where|how)\\s+(?:were|was)\\s+(?:we|i)\\s+(?:saying|talking|discussing)\\s+about\\s+(.+)")
    );

    // Known topic keywords mapped to their intent-tag prefixes for matching
    // This allows "back to Java" to match suspended topic "java_info"
    private static final Map<String, String> TOPIC_KEYWORD_TO_PREFIX = Map.ofEntries(
        Map.entry("java", "java"),
        Map.entry("python", "python"),
        Map.entry("gilgamesh", "gilgamesh"),
        Map.entry("weather", "weather"),
        Map.entry("time", "time"),
        Map.entry("date", "date"),
        Map.entry("nlp", "nlp"),
        Map.entry("chatbot", "gilgamesh"),
        Map.entry("creator", "tech_creator"),
        Map.entry("advantages", "tech_advantages")
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

        // Check for topic resumption signals BEFORE topic-change detection
        // "back to Java" should be TOPIC_RESUME if Java is on the stack,
        // not TOPIC_CHANGE
        String resumeTarget = extractResumeTarget(inputLower);
        if (resumeTarget != null && hasSuspendedTopicMatch(resumeTarget, context)) {
            return MessageType.TOPIC_RESUME;
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

    /**
     * Extracts the target topic name from a resume signal in the user input.
     * For example, "going back to Java" extracts "java".
     *
     * @param inputLower The lowercase user input
     * @return The extracted topic keyword (lowercase, trimmed), or null if no resume signal found
     */
    public String extractResumeTarget(String inputLower) {
        for (Pattern pattern : RESUME_SIGNAL_PATTERNS) {
            Matcher matcher = pattern.matcher(inputLower);
            if (matcher.find()) {
                String target = matcher.group(1).trim();
                // Strip trailing punctuation and filler words
                target = target.replaceAll("[?.!,]+$", "").trim();
                // Strip articles and filler
                target = target.replaceAll("^(the|a|an|our|my)\\s+", "").trim();
                if (!target.isEmpty()) {
                    return target.toLowerCase();
                }
            }
        }
        return null;
    }

    /**
     * Checks whether a resume target keyword matches any topic on the suspended stack.
     * Uses keyword-to-prefix mapping for fuzzy matching (e.g. "java" matches "java_info").
     *
     * @param resumeTarget The extracted topic keyword
     * @param context      The conversation context with the topic stack
     * @return true if a matching suspended topic is found
     */
    private boolean hasSuspendedTopicMatch(String resumeTarget, ConversationContext context) {
        List<TopicState> suspended = context.getSuspendedTopics();
        if (suspended.isEmpty()) return false;

        for (TopicState ts : suspended) {
            // Direct match: target equals the topic ID
            if (ts.getTopicId().equalsIgnoreCase(resumeTarget)) {
                return true;
            }
            // Prefix match: target is a keyword that maps to a topic prefix
            String prefix = TOPIC_KEYWORD_TO_PREFIX.get(resumeTarget);
            if (prefix != null && ts.getTopicId().toLowerCase().startsWith(prefix)) {
                return true;
            }
            // Containment match: topic ID contains the target keyword
            if (ts.getTopicId().toLowerCase().contains(resumeTarget)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the topic ID from the suspended stack that best matches a resume target.
     * Used by ConversationManager to determine which topic to resume.
     *
     * @param resumeTarget The extracted topic keyword
     * @param context      The conversation context with the topic stack
     * @return The matching topic ID, or null if not found
     */
    public String findMatchingSuspendedTopicId(String resumeTarget, ConversationContext context) {
        if (resumeTarget == null) return null;
        List<TopicState> suspended = context.getSuspendedTopics();

        // Priority 1: Direct match
        for (TopicState ts : suspended) {
            if (ts.getTopicId().equalsIgnoreCase(resumeTarget)) {
                return ts.getTopicId();
            }
        }
        // Priority 2: Prefix match via keyword map
        String prefix = TOPIC_KEYWORD_TO_PREFIX.get(resumeTarget);
        if (prefix != null) {
            for (TopicState ts : suspended) {
                if (ts.getTopicId().toLowerCase().startsWith(prefix)) {
                    return ts.getTopicId();
                }
            }
        }
        // Priority 3: Containment match
        for (TopicState ts : suspended) {
            if (ts.getTopicId().toLowerCase().contains(resumeTarget)) {
                return ts.getTopicId();
            }
        }
        return null;
    }
}
