package com.gilgamesh.nlp;

import com.gilgamesh.data.IntentRepository;
import com.gilgamesh.model.Intent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * IntentClassifier — Matches preprocessed user input to the best-fitting intent.
 *
 * NLP Concept: Text Classification / Intent Recognition
 * ------------------------------------------------------
 * Intent classification is the task of determining what a user "means" from
 * their text input. It's a core component of every chatbot and virtual assistant.
 *
 * Classification Approaches (from simple to complex):
 *   1. Rule-based / Keyword matching  — exact word lookups
 *   2. Token overlap scoring (Jaccard similarity) — what we use here ★
 *   3. TF-IDF + Cosine similarity — weighted term frequency matching
 *   4. Naive Bayes / SVM classifiers — traditional ML
 *   5. Deep learning (BERT, GPT) — transformer-based contextual models
 *
 * Our Approach: Jaccard Similarity (Token Overlap Scoring)
 * --------------------------------------------------------
 * For each intent, we compare the user's preprocessed tokens against each
 * pattern's preprocessed tokens using the Jaccard similarity coefficient:
 *
 *     J(A, B) = |A ∩ B| / |A ∪ B|
 *
 * Where A = user's token set, B = pattern's token set.
 *
 * This produces a score between 0.0 (no overlap) and 1.0 (identical sets).
 * The intent whose best-matching pattern has the highest Jaccard score wins.
 *
 * Why Jaccard over simple keyword counting?
 *   - It normalizes for length: "weather" matching "weather" scores 1.0,
 *     while "tell me about the weather forecast for today" matching "weather"
 *     would score much lower with raw overlap but still matches correctly
 *     because "weather" is the dominant content word after stopword removal.
 *   - It's symmetric and bounded [0, 1], making the confidence threshold
 *     intuitive to tune.
 *
 * Why not OpenNLP's DocumentCategorizer?
 *   - The DocumentCategorizer requires pre-training a model from labeled data
 *     in OpenNLP's specific format, then saving/loading a .bin model file.
 *   - For a small intent set, Jaccard similarity is more transparent, requires
 *     no training step, and clearly demonstrates the NLP concept of
 *     bag-of-words similarity.
 */
public class IntentClassifier {

    private final IntentRepository repository;
    private final TextPreprocessor preprocessor;

    /**
     * Minimum Jaccard similarity score required to accept a classification.
     *
     * If the best match scores below this threshold, we consider the input
     * unrecognized and return null (triggering a fallback response).
     *
     * Tuning this value:
     *   - Too high (e.g., 0.5): Many valid inputs won't match
     *   - Too low  (e.g., 0.05): Unrelated inputs get false matches
     *   - 0.15 is a reasonable default for small intent sets with short patterns
     */
    private static final double CONFIDENCE_THRESHOLD = 0.15;

    public IntentClassifier(IntentRepository repository, TextPreprocessor preprocessor) {
        this.repository = repository;
        this.preprocessor = preprocessor;
    }

    /**
     * Classifies the user's raw input text against all known intents.
     *
     * Algorithm:
     *   1. Preprocess the user input (tokenize → lowercase → remove stopwords)
     *   2. For each intent, preprocess every pattern
     *   3. Compute Jaccard similarity between user tokens and each pattern's tokens
     *   4. Track the intent + score of the single best-matching pattern
     *   5. If best score >= CONFIDENCE_THRESHOLD, return that intent; else null
     *
     * @param userInput Raw text from the user
     * @return The best-matching Intent, or null if no intent scores above threshold
     */
    public Intent classify(String userInput) {
        List<String> userTokens = preprocessor.preprocess(userInput);

        // Edge case: if preprocessing removes all tokens (e.g., input was only stopwords),
        // we can't meaningfully classify — return null for fallback handling.
        if (userTokens.isEmpty()) {
            return null;
        }

        Set<String> userTokenSet = new HashSet<>(userTokens);

        Intent bestIntent = null;
        double bestScore = 0.0;

        // Compare user input against every pattern across all intents
        for (Intent intent : repository.getIntents()) {
            for (String pattern : intent.getPatterns()) {
                List<String> patternTokens = preprocessor.preprocess(pattern);
                if (patternTokens.isEmpty()) {
                    continue;
                }

                Set<String> patternTokenSet = new HashSet<>(patternTokens);

                double score = jaccardSimilarity(userTokenSet, patternTokenSet);

                if (score > bestScore) {
                    bestScore = score;
                    bestIntent = intent;
                }
            }
        }

        // Apply confidence threshold — reject weak matches
        if (bestScore < CONFIDENCE_THRESHOLD) {
            System.out.println("[IntentClassifier] Best score " + String.format("%.3f", bestScore)
                    + " is below threshold " + CONFIDENCE_THRESHOLD + " — no match.");
            return null;
        }

        System.out.println("[IntentClassifier] Matched intent '" + bestIntent.getTag()
                + "' with score " + String.format("%.3f", bestScore));

        return bestIntent;
    }

    /**
     * Computes the Jaccard similarity coefficient between two sets.
     *
     * Jaccard(A, B) = |A ∩ B| / |A ∪ B|
     *
     * Properties:
     *   - Returns 1.0 when sets are identical
     *   - Returns 0.0 when sets have no elements in common
     *   - Symmetric: J(A, B) == J(B, A)
     *   - Normalized: always in range [0.0, 1.0]
     *
     * @param setA First token set (user input)
     * @param setB Second token set (pattern)
     * @return Jaccard similarity score
     */
    private double jaccardSimilarity(Set<String> setA, Set<String> setB) {
        // Compute intersection: tokens present in both sets
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        // Compute union: all unique tokens across both sets
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }
}
