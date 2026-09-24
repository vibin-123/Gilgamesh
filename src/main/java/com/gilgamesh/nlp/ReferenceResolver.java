package com.gilgamesh.nlp;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.Entity;
import com.gilgamesh.memory.Entity.EntityType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReferenceResolver — Performs Anaphora / Reference Resolution using conversation memory.
 *
 * NLP Concept: Coreference & Anaphora Resolution
 * -----------------------------------------------
 * Anaphora resolution identifies what a pronoun or demonstrative refers back to in context.
 * 
 * Supported reference types:
 *   1. Singular pronouns: "it", "this", "that" → most recent singular non-person entity
 *   2. Possessives: "its" → possessive form of most recent singular non-person entity
 *   3. Person pronouns: "he", "she" → most recent person entity
 *   4. Plural pronouns: "they", "them", "these", "those" → most recent plural entity
 *   5. Contextual noun phrases: "the language" → entity of type LANGUAGE
 *   6. Bot references: "the project", "the chatbot", "the bot" → Gilgamesh
 *   7. Creator references: "the creator", "the author", "the developer" → recent person
 *   8. Possessive phrases: "its advantages" → "[Entity]'s advantages"
 *
 * Resolution Rules:
 *   - Prefer the most recent compatible entity/topic
 *   - Detect ambiguity (multiple entities of same type) and avoid false bindings
 *   - If confidence is insufficient, leave the message unchanged
 *   - Resolution is independent from intent classification
 */
public class ReferenceResolver {

    // Pronoun patterns
    private static final Pattern POSSESSIVE_PHRASE_PATTERN = Pattern.compile(
        "\\bits\\s+(\\w+(?:\\s+\\w+)?)\\b", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SINGULAR_PRONOUN_PATTERN = Pattern.compile(
        "\\b(it|this|that)\\b", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern POSSESSIVE_PRONOUN_PATTERN = Pattern.compile(
        "\\bits\\b", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PERSON_PRONOUN_PATTERN = Pattern.compile(
        "\\b(he|she)\\b", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PLURAL_PRONOUN_PATTERN = Pattern.compile(
        "\\b(they|them|these|those)\\b", Pattern.CASE_INSENSITIVE
    );

    // Contextual noun phrase mappings: pattern → entity type to look up
    private static final Map<Pattern, EntityType> NOUN_PHRASE_TYPE_MAPPINGS = new LinkedHashMap<>();
    private static final Map<Pattern, String> NOUN_PHRASE_STATIC_MAPPINGS = new LinkedHashMap<>();

    static {
        // Type-based noun phrases
        NOUN_PHRASE_TYPE_MAPPINGS.put(
            Pattern.compile("\\bthe\\s+language\\b", Pattern.CASE_INSENSITIVE),
            EntityType.LANGUAGE
        );
        NOUN_PHRASE_TYPE_MAPPINGS.put(
            Pattern.compile("\\bthe\\s+concept\\b", Pattern.CASE_INSENSITIVE),
            EntityType.CONCEPT
        );

        // Static noun phrases
        NOUN_PHRASE_STATIC_MAPPINGS.put(
            Pattern.compile("\\bthe\\s+(?:project|chatbot|bot)\\b", Pattern.CASE_INSENSITIVE),
            "Gilgamesh"
        );
        NOUN_PHRASE_STATIC_MAPPINGS.put(
            Pattern.compile("\\bthe\\s+(?:creator|author|developer|maker)\\b", Pattern.CASE_INSENSITIVE),
            null // Will be resolved dynamically to most recent person entity
        );
    }

    // Idiomatic expressions where "it" is expletive/pleonastic and should not be replaced
    private static final List<Pattern> IDIOMATIC_EXPLETIVE_PATTERNS = List.of(
        Pattern.compile("(?i)\\bwhat\\s+(?:time|day|date)\\s+is\\s+it\\b"),
        Pattern.compile("(?i)\\bhow(?:'s|\\s+is)\\s+it\\s+going\\b"),
        Pattern.compile("(?i)\\bis\\s+it\\s+(?:going\\s+to\\s+)?(?:rain|raining|sunny|cloudy|cold|hot|snowing|warm|outside)\\b"),
        Pattern.compile("(?i)\\b(?:that's|that\\s+is)\\s+it\\b")
    );

    /**
     * Resolves references in user text using the active conversation context.
     *
     * @param userInput Raw input from user
     * @param context   Active conversation context
     * @return Resolved input string (unchanged if no resolution possible)
     */
    public String resolveReferences(String userInput, ConversationContext context) {
        if (userInput == null || context == null) {
            return userInput;
        }

        // Skip resolution if input contains expletive/pleonastic "it" (e.g. "what time is it", "how's it going")
        for (Pattern pattern : IDIOMATIC_EXPLETIVE_PATTERNS) {
            if (pattern.matcher(userInput).find()) {
                return userInput;
            }
        }

        // Skip resolution if context has no entities and no topic
        if (context.getRecentEntities().isEmpty() && "unknown".equals(context.getCurrentTopic())) {
            return userInput;
        }

        String resolved = userInput;

        // Step 1: Resolve contextual noun phrases (most specific first)
        resolved = resolveNounPhrases(resolved, context);

        // Step 2: Resolve possessive phrases ("its advantages" → "Java's advantages")
        resolved = resolvePossessivePhrases(resolved, context);

        // Step 3: Resolve person pronouns ("he", "she")
        resolved = resolvePersonPronouns(resolved, context);

        // Step 4: Resolve singular pronouns ("it", "this", "that")
        resolved = resolveSingularPronouns(resolved, context);

        // Step 5: Resolve plural pronouns ("they", "them", "these", "those")
        resolved = resolvePluralPronouns(resolved, context);

        if (!resolved.equals(userInput)) {
            System.out.println("[ReferenceResolver] Resolved anaphora: '" + userInput + "' -> '" + resolved + "'");
        }

        return resolved;
    }

    /**
     * Resolves contextual noun phrases like "the language", "the project", "the creator".
     */
    private String resolveNounPhrases(String input, ConversationContext context) {
        String result = input;

        // Type-based noun phrases
        for (Map.Entry<Pattern, EntityType> entry : NOUN_PHRASE_TYPE_MAPPINGS.entrySet()) {
            Matcher matcher = entry.getKey().matcher(result);
            if (matcher.find()) {
                EntityType type = entry.getValue();
                // Check for ambiguity
                if (context.hasAmbiguousEntities(type)) {
                    continue; // Leave ambiguous references unchanged
                }
                Entity entity = context.getMostRecentEntityByType(type);
                if (entity != null) {
                    result = matcher.replaceAll(Matcher.quoteReplacement(entity.getName()));
                }
            }
        }

        // Static noun phrases
        for (Map.Entry<Pattern, String> entry : NOUN_PHRASE_STATIC_MAPPINGS.entrySet()) {
            Matcher matcher = entry.getKey().matcher(result);
            if (matcher.find()) {
                String replacement = entry.getValue();
                if (replacement == null) {
                    // Dynamic resolution for "the creator", "the author", etc.
                    Entity person = context.getMostRecentPersonEntity();
                    if (person != null) {
                        replacement = person.getName();
                    }
                }
                if (replacement != null) {
                    result = matcher.replaceAll(Matcher.quoteReplacement(replacement));
                }
            }
        }

        return result;
    }

    /**
     * Resolves possessive phrases like "its advantages" → "Java's advantages"
     * or standalone "its".
     */
    private String resolvePossessivePhrases(String input, ConversationContext context) {
        Matcher matcher = POSSESSIVE_PHRASE_PATTERN.matcher(input);
        if (matcher.find()) {
            Entity entity = context.getMostRecentSingularNonPersonEntity();
            String entityName = entity != null ? entity.getName() : null;
            if (entityName == null) {
                entityName = determineFallbackReferent(context);
            }
            if (entityName != null) {
                // Check ambiguity for the entity's type
                if (entity != null && context.hasAmbiguousEntities(entity.getType())) {
                    return input; // Leave ambiguous possessives unchanged
                }
                String suffix = matcher.group(1);
                String replacement = entityName + "'s " + suffix;
                return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
            }
        }

        // Standalone "its"
        Matcher standaloneMatcher = POSSESSIVE_PRONOUN_PATTERN.matcher(input);
        if (standaloneMatcher.find()) {
            Entity entity = context.getMostRecentSingularNonPersonEntity();
            String entityName = entity != null ? entity.getName() : null;
            if (entityName == null) {
                entityName = determineFallbackReferent(context);
            }
            if (entityName != null) {
                if (entity != null && context.hasAmbiguousEntities(entity.getType())) {
                    return input;
                }
                return standaloneMatcher.replaceAll(Matcher.quoteReplacement(entityName + "'s"));
            }
        }
        return input;
    }

    /**
     * Resolves person pronouns like "he", "she" → most recent person entity.
     */
    private String resolvePersonPronouns(String input, ConversationContext context) {
        Matcher matcher = PERSON_PRONOUN_PATTERN.matcher(input);
        if (matcher.find()) {
            Entity person = context.getMostRecentPersonEntity();
            if (person != null) {
                return matcher.replaceAll(Matcher.quoteReplacement(person.getName()));
            }
        }
        return input;
    }

    /**
     * Resolves singular pronouns like "it", "this", "that" → most recent singular non-person entity.
     */
    private String resolveSingularPronouns(String input, ConversationContext context) {
        Matcher matcher = SINGULAR_PRONOUN_PATTERN.matcher(input);
        if (matcher.find()) {
            Entity entity = context.getMostRecentSingularNonPersonEntity();
            String entityName = entity != null ? entity.getName() : null;
            if (entityName == null) {
                entityName = determineFallbackReferent(context);
            }
            if (entityName != null) {
                // Check ambiguity for the entity's type
                if (entity != null && context.hasAmbiguousEntities(entity.getType())) {
                    return input; // Leave ambiguous references unchanged
                }
                return matcher.replaceAll(Matcher.quoteReplacement(entityName));
            }
        }
        return input;
    }

    /**
     * Resolves plural pronouns like "they", "them", "these", "those".
     */
    private String resolvePluralPronouns(String input, ConversationContext context) {
        Matcher matcher = PLURAL_PRONOUN_PATTERN.matcher(input);
        if (matcher.find()) {
            // 1. Look for an explicitly plural entity
            Entity entity = context.getActiveEntity();
            if (entity != null && entity.isPlural()) {
                return matcher.replaceAll(Matcher.quoteReplacement(entity.getName()));
            }

            // 2. Check if multiple recent entities form a plural group (e.g. Java and Python)
            List<Entity> recent = context.getRecentEntities();
            if (recent.size() >= 2) {
                EntityType lastType = recent.get(recent.size() - 1).getType();
                List<String> matchingNames = new java.util.ArrayList<>();
                for (Entity e : recent) {
                    if (e.matchesType(lastType) && !matchingNames.contains(e.getName())) {
                        matchingNames.add(e.getName());
                    }
                }
                if (matchingNames.size() >= 2) {
                    String combined = String.join(" and ", matchingNames);
                    return matcher.replaceAll(Matcher.quoteReplacement(combined));
                }
            }
        }
        return input;
    }

    /**
     * Determines a fallback referent entity name using the last detected intent tag
     * or current conversation topic when no entities are explicitly recorded in context.
     */
    private String determineFallbackReferent(ConversationContext context) {
        com.gilgamesh.model.Intent lastIntent = context.getLastDetectedIntent();
        if (lastIntent != null && lastIntent.getTag() != null) {
            String tag = lastIntent.getTag().toLowerCase();
            switch (tag) {
                case "bot_info":
                case "creator_info":
                case "identity":
                case "bot_name":
                case "purpose":
                case "creator":
                    return "Gilgamesh";
                case "nlp_definition":
                case "nlp_explanation":
                    return "NLP";
                case "weather":
                    return "weather";
                case "datetime":
                    return "time";
                case "about_technology":
                case "about_java":
                case "java_info":
                    return "Java";
                case "about_python":
                case "python_info":
                    return "Python";
                default:
                    if (tag.contains("python")) return "Python";
                    if (tag.contains("java")) return "Java";
                    if (tag.contains("nlp")) return "NLP";
                    if (tag.contains("bot")) return "Gilgamesh";
            }
        }
        String topic = context.getCurrentTopic();
        if (topic != null && !topic.isBlank() && !topic.equalsIgnoreCase("unknown")) {
            String lowerTopic = topic.toLowerCase();
            if (lowerTopic.contains("python")) return "Python";
            if (lowerTopic.contains("java")) return "Java";
            if (lowerTopic.contains("nlp")) return "NLP";
            if (lowerTopic.contains("bot")) return "Gilgamesh";
            return topic;
        }
        return null;
    }
}

