package com.gilgamesh.nlp;

import opennlp.tools.tokenize.SimpleTokenizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * TextPreprocessor — Performs the initial NLP pipeline steps on raw text.
 *
 * NLP Concepts Demonstrated:
 * --------------------------
 * 1. TOKENIZATION:
 *    Breaking raw text into individual tokens (words, punctuation).
 *    This is the foundational step in almost every NLP pipeline because
 *    machines operate on discrete units, not continuous strings.
 *
 *    We use Apache OpenNLP's SimpleTokenizer rather than String.split(" ")
 *    because it handles edge cases like punctuation attached to words
 *    (e.g., "hello!" → ["hello", "!"] instead of ["hello!"]).
 *
 * 2. CASE NORMALIZATION (Lowercasing):
 *    Converting all tokens to lowercase ensures that "Hello", "HELLO",
 *    and "hello" are treated as the same word. Without this, our intent
 *    matcher would miss obvious matches due to casing differences.
 *
 * 3. STOPWORD REMOVAL:
 *    Stopwords are extremely common words (the, is, a, an, in, etc.)
 *    that carry little semantic meaning. Removing them focuses the
 *    classifier on the "content words" that actually signal intent.
 *    For example, "what is the weather" → ["weather"] after stopword
 *    removal, which directly points to the weather intent.
 *
 *    Note: Stopword removal is a trade-off. In some NLP tasks (like
 *    sentiment analysis or machine translation), stopwords carry
 *    important grammatical information. For intent classification
 *    with keyword matching, removing them improves accuracy.
 */
public class TextPreprocessor {

    /**
     * OpenNLP's SimpleTokenizer: A rule-based tokenizer that splits text
     * on whitespace and punctuation boundaries. Unlike regex-based splitting,
     * it correctly identifies punctuation as separate tokens.
     */
    private final SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;

    /**
     * A curated set of English stopwords. These are the most frequent words
     * in English that don't contribute to intent classification.
     *
     * In production NLP systems, stopword lists are often loaded from files
     * or use established lists (e.g., NLTK's English stopwords). Here we
     * hardcode a concise set for simplicity and transparency.
     */
    private static final Set<String> STOPWORDS = Set.of(
            // Articles
            "a", "an", "the",
            // Personal pronouns
            // Note: "you" and "your" are kept as stopwords because they rarely
            // distinguish intents on their own. However, question words (who, what,
            // how, when, where, why, which) are intentionally NOT stopwords — they
            // define the type of question and are essential for classifying inputs
            // like "who are you" or "how are you doing".
            "i", "me", "my", "you", "your", "he", "she", "it", "we", "they",
            "him", "her", "us", "them", "its", "our", "their",
            // Prepositions
            "in", "on", "at", "to", "for", "of", "with", "by", "from", "up",
            "into", "through", "during", "before", "after",
            // Conjunctions
            "and", "but", "or", "nor", "so", "yet",
            // Auxiliary / linking verbs
            "is", "am", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did",
            "will", "would", "shall", "should", "may", "might", "must",
            "can", "could",
            // Other filler stopwords
            "not", "no", "this", "that", "these", "those",
            "if", "then", "than", "too", "very", "just",
            "there", "here", "all", "any", "each", "some",
            // Common contractions (after tokenization splits them)
            "s", "t", "re", "ve", "ll", "d", "m"
    );

    /**
     * Preprocesses raw user input through the full NLP pipeline.
     *
     * Pipeline: Raw Text → Tokenize → Lowercase → Remove Stopwords → Remove Punctuation
     *
     * @param input The raw text from the user
     * @return A list of cleaned, meaningful tokens ready for intent classification
     */
    public List<String> preprocess(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        // Step 1: Tokenization — split the input into discrete tokens
        String[] rawTokens = tokenizer.tokenize(input);

        List<String> processed = new ArrayList<>();
        for (String token : rawTokens) {
            // Step 2: Case normalization — convert to lowercase
            String lower = token.toLowerCase();

            // Step 3: Remove pure punctuation tokens (e.g., "!", "?", ".")
            // We keep alphanumeric tokens only. This is important because
            // punctuation doesn't help with keyword-based intent matching.
            if (!lower.matches("[a-z0-9]+")) {
                continue;
            }

            // Step 4: Stopword removal — discard low-information words
            if (STOPWORDS.contains(lower)) {
                continue;
            }

            processed.add(lower);
        }

        return processed;
    }
}
