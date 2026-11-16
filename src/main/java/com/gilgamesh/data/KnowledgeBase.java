package com.gilgamesh.data;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KnowledgeBase — Facts about the things the bot can talk about, loaded from knowledge.json.
 *
 * NLP Concept: Separating intents from entities
 * ----------------------------------------------
 * "Who created Java?" and "Who created Rust?" are the same question (intent) about
 * different subjects (entities). Instead of one intent per language, the bot has one
 * generic intent ("who created {entity}") and looks the answer up here. Teaching the
 * bot a new language is then a data edit in knowledge.json, with no Java code changes.
 */
public class KnowledgeBase {

    /** Placeholder that stands for "any known entity" in intent patterns and masked input. */
    public static final String ENTITY_PLACEHOLDER = "{entity}";

    private static final String DEFAULT_RESOURCE = "/knowledge.json";
    private static KnowledgeBase defaultInstance;

    /** One subject and what the bot knows about it. */
    public static class Entry {
        private String name;
        private String type;
        private boolean caseSensitive;
        private List<String> aliases;
        private List<String> about;
        private List<String> creatorFact;
        private List<String> advantages;

        public String getName() { return name; }
        public String getType() { return type; }
        public List<String> getAliases() { return aliases != null ? aliases : List.of(); }
        public List<String> getAbout() { return about != null ? about : List.of(); }
        public List<String> getCreatorFact() { return creatorFact != null ? creatorFact : List.of(); }
        public List<String> getAdvantages() { return advantages != null ? advantages : List.of(); }
    }

    /** A place in a text where a known entity is named. */
    public record Mention(int start, int end, Entry entry) {}

    private static class Data {
        List<Entry> entities;
    }

    private record NamePattern(Pattern pattern, Entry entry, boolean notAtStart) {}

    private final List<Entry> entries;
    private final List<NamePattern> namePatterns = new ArrayList<>();

    /** Loads the bundled knowledge.json (cached after the first call). */
    public static synchronized KnowledgeBase loadDefault() {
        if (defaultInstance == null) {
            defaultInstance = new KnowledgeBase(DEFAULT_RESOURCE);
        }
        return defaultInstance;
    }

    /**
     * Loads a knowledge base from a classpath resource.
     *
     * @throws RuntimeException if the resource is missing or not valid JSON
     */
    public KnowledgeBase(String resourcePath) {
        InputStream stream = getClass().getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new RuntimeException("Could not find " + resourcePath + " on the classpath.");
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Data data = new Gson().fromJson(reader, Data.class);
            this.entries = data != null && data.entities != null ? data.entities : List.of();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + resourcePath + ": " + e.getMessage(), e);
        }
        for (Entry entry : entries) {
            if (entry.name == null || entry.name.isBlank()) {
                throw new RuntimeException(resourcePath + " contains an entity without a name.");
            }
            // Names that are also everyday words ("Go", "C") only match with their exact
            // capitalisation, and not as the first word ("Go away").
            namePatterns.add(new NamePattern(compile(entry.name, !entry.caseSensitive), entry, entry.caseSensitive));
            for (String alias : entry.getAliases()) {
                namePatterns.add(new NamePattern(compile(alias, true), entry, false));
            }
        }
    }

    private static Pattern compile(String name, boolean ignoreCase) {
        // Not preceded by a letter/digit, and not followed by one (or by + / #, so "C" doesn't match inside "C++")
        String regex = "(?<![A-Za-z0-9])" + Pattern.quote(name) + "(?![A-Za-z0-9+#])";
        return Pattern.compile(regex, ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
    }

    public List<Entry> getEntries() {
        return entries;
    }

    /**
     * Finds an entry by its name or one of its aliases (case-insensitive).
     *
     * @return the entry, or null if unknown
     */
    public Entry find(String name) {
        if (name == null) return null;
        String wanted = name.trim();
        for (Entry entry : entries) {
            if (entry.name.equalsIgnoreCase(wanted)) return entry;
            for (String alias : entry.getAliases()) {
                if (alias.equalsIgnoreCase(wanted)) return entry;
            }
        }
        return null;
    }

    /**
     * Finds every place a known entity is named in the text, in reading order,
     * with overlapping matches removed (longest wins).
     */
    public List<Mention> findMentions(String text) {
        List<Mention> found = new ArrayList<>();
        if (text == null || text.isBlank()) return found;

        for (NamePattern namePattern : namePatterns) {
            Matcher matcher = namePattern.pattern().matcher(text);
            while (matcher.find()) {
                if (namePattern.notAtStart() && text.substring(0, matcher.start()).isBlank()) {
                    continue;
                }
                found.add(new Mention(matcher.start(), matcher.end(), namePattern.entry()));
            }
        }

        // Longest first so "Go language" beats "Go", then drop anything overlapping a kept mention
        found.sort(Comparator.comparingInt((Mention m) -> m.end() - m.start()).reversed());
        List<Mention> kept = new ArrayList<>();
        for (Mention candidate : found) {
            boolean overlaps = false;
            for (Mention existing : kept) {
                if (candidate.start() < existing.end() && existing.start() < candidate.end()) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) kept.add(candidate);
        }
        kept.sort(Comparator.comparingInt(Mention::start));
        return kept;
    }

    /** The distinct entries named in the text, in reading order. */
    public List<Entry> findEntries(String text) {
        List<Entry> result = new ArrayList<>();
        for (Mention mention : findMentions(text)) {
            if (!result.contains(mention.entry())) {
                result.add(mention.entry());
            }
        }
        return result;
    }

    /**
     * Replaces every known entity name with {@link #ENTITY_PLACEHOLDER}, so that
     * "who created Rust" and "who created Java" both become "who created {entity}"
     * and match the same generic intent pattern.
     */
    public String maskEntities(String text) {
        List<Mention> mentions = findMentions(text);
        if (mentions.isEmpty()) return text;

        StringBuilder masked = new StringBuilder();
        int position = 0;
        for (Mention mention : mentions) {
            masked.append(text, position, mention.start()).append(ENTITY_PLACEHOLDER);
            position = mention.end();
        }
        return masked.append(text.substring(position)).toString();
    }
}
