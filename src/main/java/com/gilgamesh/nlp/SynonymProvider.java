package com.gilgamesh.nlp;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * SynonymProvider — Provides synonym mapping and canonical word resolution.
 *
 * NLP Concept: Synonym Expansion & Normalization
 * ----------------------------------------------
 * Users express the same intent using different synonyms (e.g., "author", "creator", "developer").
 * Synonym mapping converts words to canonical representative forms, allowing intent patterns
 * to match input variations without needing every possible phrasing explicitly defined.
 */
public class SynonymProvider {

    private final Map<String, String> synonymToCanonicalMap = new HashMap<>();

    public SynonymProvider() {
        initDefaultSynonyms();
    }

    private void initDefaultSynonyms() {
        // Greetings
        addSynonyms("hello", "hi", "hey", "greetings", "howdy", "sup");
        
        // Creator / Developer / Origin
        addSynonyms("creator", "developer", "author", "maker", "builder", "inventor", "designer");
        addSynonyms("create", "build", "develop", "make", "invent", "design");
        
        // Time & Date
        addSynonyms("time", "clock", "hour");
        addSynonyms("date", "day", "calendar");
        
        // Weather
        addSynonyms("weather", "forecast", "climate", "temperature");
        
        // Farewells
        addSynonyms("goodbye", "bye", "farewell", "exit", "quit", "see ya");
        
        // Help & Support
        addSynonyms("help", "assist", "assistance", "support", "guidance");
        
        // Jokes & Humor
        addSynonyms("joke", "funny", "humor", "gag", "pun");
        
        // Capabilities / Features
        addSynonyms("feature", "capability", "functionality", "ability");
    }

    private void addSynonyms(String canonical, String... synonyms) {
        synonymToCanonicalMap.put(canonical.toLowerCase(), canonical.toLowerCase());
        for (String syn : synonyms) {
            synonymToCanonicalMap.put(syn.toLowerCase(), canonical.toLowerCase());
        }
    }

    /**
     * Maps a token to its canonical synonym if available, otherwise returns the original token.
     */
    public String getCanonicalForm(String token) {
        if (token == null) return "";
        String lower = token.toLowerCase();
        return synonymToCanonicalMap.getOrDefault(lower, lower);
    }
}
