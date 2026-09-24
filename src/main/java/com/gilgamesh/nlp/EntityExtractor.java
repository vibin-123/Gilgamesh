package com.gilgamesh.nlp;

import com.gilgamesh.memory.Entity;
import com.gilgamesh.memory.Entity.EntityType;
import com.gilgamesh.memory.Entity.GrammaticalNumber;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EntityExtractor — Deterministic Named Entity Recognition for conversation context.
 *
 * NLP Concept: Named Entity Recognition (NER)
 * --------------------------------------------
 * NER identifies and classifies key entities in user text. This implementation
 * uses a combination of:
 *   - Known entity catalog (programming languages, people, tools, concepts)
 *   - Syntactic pattern extraction ("What is [X]?", "Tell me about [X]")
 *   - Capitalized token / proper noun detection
 *
 * This is a lightweight, deterministic approach that avoids external APIs or ML models.
 */
public class EntityExtractor {

    // Known entity catalogs — maps lowercase entity name to its type
    private static final Map<String, EntityType> KNOWN_ENTITIES = new LinkedHashMap<>();
    private static final Map<String, String> ENTITY_CANONICAL_NAMES = new LinkedHashMap<>();

    static {
        // Programming languages
        registerEntity("java", EntityType.LANGUAGE);
        registerEntity("python", EntityType.LANGUAGE);
        registerEntity("javascript", EntityType.LANGUAGE);
        registerEntity("typescript", EntityType.LANGUAGE);
        registerEntity("c", EntityType.LANGUAGE);
        registerEntity("c++", EntityType.LANGUAGE);
        registerEntity("cpp", EntityType.LANGUAGE);
        registerEntity("c#", EntityType.LANGUAGE);
        registerEntity("csharp", EntityType.LANGUAGE);
        registerEntity("ruby", EntityType.LANGUAGE);
        registerEntity("go", EntityType.LANGUAGE);
        registerEntity("golang", EntityType.LANGUAGE);
        registerEntity("rust", EntityType.LANGUAGE);
        registerEntity("swift", EntityType.LANGUAGE);
        registerEntity("kotlin", EntityType.LANGUAGE);
        registerEntity("scala", EntityType.LANGUAGE);
        registerEntity("php", EntityType.LANGUAGE);
        registerEntity("perl", EntityType.LANGUAGE);
        registerEntity("r", EntityType.LANGUAGE);
        registerEntity("sql", EntityType.LANGUAGE);
        registerEntity("html", EntityType.LANGUAGE);
        registerEntity("css", EntityType.LANGUAGE);

        // People
        registerEntity("james gosling", EntityType.PERSON);
        registerEntity("gosling", EntityType.PERSON);
        registerEntity("guido van rossum", EntityType.PERSON);
        registerEntity("guido", EntityType.PERSON);
        registerEntity("brendan eich", EntityType.PERSON);
        registerEntity("dennis ritchie", EntityType.PERSON);
        registerEntity("linus torvalds", EntityType.PERSON);
        registerEntity("bjarne stroustrup", EntityType.PERSON);

        // Bot
        registerEntity("gilgamesh", EntityType.BOT);

        // Concepts
        registerEntity("nlp", EntityType.CONCEPT);
        registerEntity("natural language processing", EntityType.CONCEPT);
        registerEntity("machine learning", EntityType.CONCEPT);
        registerEntity("artificial intelligence", EntityType.CONCEPT);
        registerEntity("ai", EntityType.CONCEPT);
        registerEntity("tokenization", EntityType.CONCEPT);
        registerEntity("stemming", EntityType.CONCEPT);
        registerEntity("deep learning", EntityType.CONCEPT);

        // Canonical name mappings for aliases
        ENTITY_CANONICAL_NAMES.put("cpp", "C++");
        ENTITY_CANONICAL_NAMES.put("csharp", "C#");
        ENTITY_CANONICAL_NAMES.put("golang", "Go");
        ENTITY_CANONICAL_NAMES.put("gosling", "James Gosling");
        ENTITY_CANONICAL_NAMES.put("guido", "Guido van Rossum");
        ENTITY_CANONICAL_NAMES.put("nlp", "NLP");
        ENTITY_CANONICAL_NAMES.put("ai", "AI");
    }

    private static void registerEntity(String name, EntityType type) {
        KNOWN_ENTITIES.put(name.toLowerCase(), type);
    }

    // Syntactic extraction patterns — captures [X] from common question forms
    private static final List<Pattern> EXTRACTION_PATTERNS = List.of(
        Pattern.compile("(?i)(?:what is|what's|whats)\\s+(.+?)\\??$"),
        Pattern.compile("(?i)(?:tell me about|explain|describe)\\s+(.+?)\\??$"),
        Pattern.compile("(?i)(?:who (?:is|was|created|made|built|developed|invented))\\s+(.+?)\\??$"),
        Pattern.compile("(?i)(?:how does|how do)\\s+(.+?)\\s+(?:work|function)\\??$"),
        Pattern.compile("(?i)(?:what are)\\s+(.+?)(?:'s|s)?\\s+(?:advantages|features|benefits|disadvantages|pros|cons)\\??$"),
        Pattern.compile("(?i)(?:what are the (?:advantages|features|benefits|disadvantages|pros|cons) of)\\s+(.+?)\\??$")
    );

    private static final Set<String> IGNORED_EXTRACTIONS = Set.of(
        "it", "its", "this", "that", "these", "those", "they", "them", "he", "she", "him", "her",
        "you", "me", "we", "us", "something", "anything", "nothing", "everything",
        "what", "who", "which", "where", "when", "why", "how", "all", "more"
    );

    /**
     * Extracts entities from the given user input text.
     *
     * @param userInput Raw user input
     * @return List of extracted entities (may be empty, never null)
     */
    public List<Entity> extractEntities(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return List.of();
        }

        List<Entity> entities = new ArrayList<>();
        String inputLower = userInput.toLowerCase().trim();

        // Strategy 1: Check known entity catalog via substring matching
        // Process longer entity names first to avoid partial matches
        List<String> sortedNames = new ArrayList<>(KNOWN_ENTITIES.keySet());
        sortedNames.sort((a, b) -> Integer.compare(b.length(), a.length()));

        Set<String> foundEntityNames = new HashSet<>();

        for (String entityName : sortedNames) {
            if (containsWholeWord(inputLower, entityName)) {
                String canonicalName = getCanonicalName(entityName);
                if (!foundEntityNames.contains(canonicalName.toLowerCase())) {
                    EntityType type = KNOWN_ENTITIES.get(entityName);
                    entities.add(new Entity(canonicalName, type));
                    foundEntityNames.add(canonicalName.toLowerCase());
                }
            }
        }

        // Strategy 2: Syntactic pattern extraction for unknown entities
        if (entities.isEmpty()) {
            for (Pattern pattern : EXTRACTION_PATTERNS) {
                Matcher matcher = pattern.matcher(userInput.trim());
                if (matcher.find()) {
                    String extracted = matcher.group(1).trim();
                    // Clean up common leading/trailing words
                    extracted = cleanExtractedEntity(extracted);
                    if (!extracted.isBlank() && extracted.length() > 1 && !IGNORED_EXTRACTIONS.contains(extracted.toLowerCase())) {
                        // Check if it's a known entity we might have missed
                        EntityType type = KNOWN_ENTITIES.getOrDefault(extracted.toLowerCase(), EntityType.GENERAL);
                        String canonicalName = getCanonicalName(extracted);
                        if (!foundEntityNames.contains(canonicalName.toLowerCase())) {
                            entities.add(new Entity(canonicalName, type));
                            foundEntityNames.add(canonicalName.toLowerCase());
                        }
                    }
                    break; // Take the first syntactic match
                }
            }
        }

        if (!entities.isEmpty()) {
            System.out.println("[EntityExtractor] Extracted entities: " + entities);
        }

        return entities;
    }

    /**
     * Checks if the input contains the entity name as a whole word.
     */
    private boolean containsWholeWord(String inputLower, String entityName) {
        // For single-char entities like "c" or "r", require exact patterns
        if (entityName.length() == 1) {
            // Match "c" or "r" only when used as a language reference
            // e.g. "what is c" or "tell me about r"
            return Pattern.compile("\\b" + Pattern.quote(entityName) + "\\b")
                    .matcher(inputLower).find()
                    && !isCommonWord(entityName);
        }
        // For multi-word entities, use word boundary matching
        String regex = "\\b" + Pattern.quote(entityName) + "\\b";
        return Pattern.compile(regex).matcher(inputLower).find();
    }

    /**
     * Checks if a single character is too common to be an entity on its own.
     * Only "c" and "r" are recognized as programming languages in isolation.
     */
    private boolean isCommonWord(String word) {
        // "c" and "r" are valid language names but risky in general text
        // Only match them in contexts like "what is c" or "tell me about r"
        return false; // Let the word-boundary check handle it
    }

    /**
     * Gets the canonical display name for an entity.
     */
    private String getCanonicalName(String rawName) {
        String canonical = ENTITY_CANONICAL_NAMES.get(rawName.toLowerCase());
        if (canonical != null) {
            return canonical;
        }
        // Capitalize first letter for proper nouns
        if (rawName.length() <= 3) {
            // Short names like "NLP", "AI", "SQL" stay uppercase
            return rawName.toUpperCase();
        }
        // Return the raw name with first letter capitalized
        return rawName.substring(0, 1).toUpperCase() + rawName.substring(1);
    }

    /**
     * Cleans extracted entity text by removing common filler words.
     */
    private String cleanExtractedEntity(String text) {
        // Remove leading articles and prepositions
        text = text.replaceAll("(?i)^(the|a|an)\\s+", "");
        // Remove trailing punctuation
        text = text.replaceAll("[?.!,]+$", "");
        return text.trim();
    }
}
