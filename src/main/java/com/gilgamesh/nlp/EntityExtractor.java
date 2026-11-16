package com.gilgamesh.nlp;

import java.util.logging.Logger;

import com.gilgamesh.data.KnowledgeBase;
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

    private static final Logger LOG = Logger.getLogger(EntityExtractor.class.getName());

    // Known entity catalogs — maps lowercase entity name to its type
    private static final Map<String, EntityType> KNOWN_ENTITIES = new LinkedHashMap<>();
    private static final Map<String, String> ENTITY_CANONICAL_NAMES = new LinkedHashMap<>();

    static {
        // Programming languages are not listed here: they come from knowledge.json
        // (see KnowledgeBase), so adding a language needs no code change.

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
        ENTITY_CANONICAL_NAMES.put("gosling", "James Gosling");
        ENTITY_CANONICAL_NAMES.put("guido", "Guido van Rossum");
        ENTITY_CANONICAL_NAMES.put("nlp", "NLP");
        ENTITY_CANONICAL_NAMES.put("ai", "AI");
    }

    private static void registerEntity(String name, EntityType type) {
        KNOWN_ENTITIES.put(name.toLowerCase(), type);
    }

    private final KnowledgeBase knowledgeBase;

    /** Creates an extractor backed by the bundled knowledge.json. */
    public EntityExtractor() {
        this(KnowledgeBase.loadDefault());
    }

    public EntityExtractor(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    private static EntityType typeOf(KnowledgeBase.Entry entry) {
        try {
            return EntityType.valueOf(entry.getType());
        } catch (RuntimeException e) {
            return EntityType.GENERAL;
        }
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

        // Strategy 0: Location in a weather-style query ("weather in Paris").
        // Added first so Strategy 2 below doesn't swallow the whole phrase as a GENERAL entity.
        extractLocation(userInput).ifPresent(location -> {
            entities.add(location);
            foundEntityNames.add(location.getName().toLowerCase());
        });

        // Strategy 1a: subjects from the knowledge base (programming languages), in reading order
        for (KnowledgeBase.Entry entry : knowledgeBase.findEntries(userInput)) {
            if (foundEntityNames.add(entry.getName().toLowerCase())) {
                entities.add(new Entity(entry.getName(), typeOf(entry)));
            }
        }

        // Strategy 1b: the built-in catalog (people, the bot, concepts)
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
                        // (e.g. lowercase "go", which only counts as the language in "what is go")
                        KnowledgeBase.Entry known = knowledgeBase.find(extracted);
                        EntityType type = known != null ? typeOf(known)
                                : KNOWN_ENTITIES.getOrDefault(extracted.toLowerCase(), EntityType.GENERAL);
                        String canonicalName = known != null ? known.getName() : getCanonicalName(extracted);
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
            LOG.fine("[EntityExtractor] Extracted entities: " + entities);
        }

        return entities;
    }

    // "... weather/forecast/temperature ... in|for|at <City> [today|?|...]"
    private static final Pattern WEATHER_LOCATION_PATTERN = Pattern.compile(
            "(?i)\\b(?:weather|forecast|temperature|raining|snowing)\\b.*?\\b(?:in|for|at)\\s+"
                    + "([A-Za-z][A-Za-z .'-]*?)(?=\\s+(?:today|tonight|tomorrow|now|right now|currently|please)\\b|[?.!,]|$)");
    // Bare follow-up: "and in London?", "in Tokyo"
    private static final Pattern FOLLOW_UP_LOCATION_PATTERN = Pattern.compile(
            "(?i)^(?:and\\s+|what about\\s+|how about\\s+)?(?:in|for|at)\\s+([A-Za-z][A-Za-z .'-]*?)\\s*[?.!]*$");
    private static final Set<String> NOT_A_PLACE = Set.of(
            "the", "a", "an", "it", "this", "that", "here", "there", "general", "my area", "my city",
            "my location", "the world", "me", "us", "you", "java", "python");

    /**
     * Extracts a city/place from a weather-style query or a bare "in London?" follow-up.
     *
     * @return the LOCATION entity, or empty if none found
     */
    public Optional<Entity> extractLocation(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return Optional.empty();
        }
        String text = userInput.trim();
        Matcher matcher = WEATHER_LOCATION_PATTERN.matcher(text);
        if (!matcher.find()) {
            matcher = FOLLOW_UP_LOCATION_PATTERN.matcher(text);
            if (!matcher.find()) {
                return Optional.empty();
            }
        }
        return toLocation(matcher.group(1));
    }

    /**
     * Normalizes raw text as a LOCATION entity ("new york" -> "New York"),
     * or empty if the text is blank or not plausibly a place.
     */
    public Optional<Entity> toLocation(String rawPlace) {
        if (rawPlace == null) {
            return Optional.empty();
        }
        String place = rawPlace.trim().replaceAll("[?.!,]+$", "").trim();
        if (place.length() < 2 || NOT_A_PLACE.contains(place.toLowerCase())) {
            return Optional.empty();
        }
        StringBuilder name = new StringBuilder();
        for (String word : place.split("\\s+")) {
            if (name.length() > 0) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
        }
        return Optional.of(new Entity(name.toString(), EntityType.LOCATION));
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
        // Capitalize the first letter of every word ("james gosling" -> "James Gosling")
        StringBuilder name = new StringBuilder();
        for (String word : rawName.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (name.length() > 0) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
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
