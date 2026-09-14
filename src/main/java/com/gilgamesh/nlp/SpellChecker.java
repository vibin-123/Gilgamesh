package com.gilgamesh.nlp;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * SpellChecker — Performs fuzzy matching and spell correction on user tokens.
 *
 * NLP Concept: Minimum Edit Distance (Levenshtein Distance)
 * ---------------------------------------------------------
 * Users often make typos (e.g. "wether" instead of "weather", "helllo" instead of "hello").
 * Levenshtein distance counts the minimum number of single-character edits (insertions,
 * deletions, substitutions) required to transform one string into another.
 *
 * If a word is unknown but has an edit distance <= 2 to a target vocabulary word,
 * it is corrected automatically before classification.
 */
public class SpellChecker {

    private final Set<String> vocabulary = new HashSet<>();
    private static final int MAX_EDIT_DISTANCE = 2;

    public SpellChecker() {
        // Default domain vocabulary
        addWords(Set.of(
            "hello", "hi", "hey", "greetings", "weather", "forecast", "time", "date",
            "today", "joke", "funny", "nlp", "creator", "developer", "author", "help",
            "bot", "gilgamesh", "processing", "intent", "goodbye", "bye", "create", "created"
        ));
    }

    public void addWords(Collection<String> words) {
        for (String word : words) {
            if (word != null && word.length() > 2) {
                vocabulary.add(word.toLowerCase());
            }
        }
    }

    /**
     * Corrects a token if it matches a dictionary word within MAX_EDIT_DISTANCE.
     * Returns the original token if no match is within threshold or if already valid.
     */
    public String correctToken(String token) {
        if (token == null || token.length() <= 2) {
            return token;
        }

        String lower = token.toLowerCase();
        if (vocabulary.contains(lower)) {
            return lower;
        }

        String bestMatch = lower;
        int minDistance = Integer.MAX_VALUE;

        for (String vocabWord : vocabulary) {
            // Quick optimization: length difference must not exceed MAX_EDIT_DISTANCE
            if (Math.abs(vocabWord.length() - lower.length()) > MAX_EDIT_DISTANCE) {
                continue;
            }

            int dist = computeLevenshteinDistance(lower, vocabWord);
            if (dist <= MAX_EDIT_DISTANCE && dist < minDistance) {
                minDistance = dist;
                bestMatch = vocabWord;
            }
        }

        return bestMatch;
    }

    /**
     * Computes the Levenshtein edit distance between two strings.
     */
    public static int computeLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s1.length()][s2.length()];
    }
}
