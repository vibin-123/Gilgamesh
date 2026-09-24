package com.gilgamesh.nlp;

import com.gilgamesh.data.IntentRepository;
import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.model.Intent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IntentClassifier — Context-Aware Hybrid Intent Classification Engine.
 *
 * Scoring Approaches Combined (Ensemble):
 * ----------------------------------------
 * 1. Jaccard Similarity (Token Overlap)
 * 2. TF-IDF Cosine Similarity (Weighted Vector Similarity)
 * 3. Context Boost (Biasing based on conversation topic/turn context)
 */
public class IntentClassifier {

    private final IntentRepository repository;
    private final TextPreprocessor preprocessor;
    private final TfidfVectorizer tfidfVectorizer;
    private final Map<String, Map<String, Double>> patternTfidfVectors = new HashMap<>();

    private static final double CONFIDENCE_THRESHOLD = 0.15;
    private static final double CONTEXT_BOOST_MULTIPLIER = 1.25;

    public IntentClassifier(IntentRepository repository, TextPreprocessor preprocessor) {
        this.repository = repository;
        this.preprocessor = preprocessor;
        this.tfidfVectorizer = new TfidfVectorizer();
        initVectorizer();
    }

    private void initVectorizer() {
        if (repository != null && repository.getIntents() != null) {
            // 1. Collect all raw, unstemmed vocabulary words from intent patterns and
            // register them into the spell checker so valid words are never falsely corrected
            Set<String> rawVocab = new HashSet<>();
            for (Intent intent : repository.getIntents()) {
                for (String pattern : intent.getPatterns()) {
                    String[] words = pattern.toLowerCase().split("[^a-z0-9]+");
                    for (String w : words) {
                        if (w.length() > 2) {
                            rawVocab.add(w);
                        }
                    }
                }
            }
            rawVocab.addAll(Set.of("tell", "about", "python", "java", "what", "who", "where", "when", "why", "how", "can", "please"));
            preprocessor.registerVocabulary(rawVocab);

            // 2. Train TF-IDF vectorizer on intent corpus patterns
            tfidfVectorizer.fit(repository.getIntents(), preprocessor);

            // 3. Precompute TF-IDF vectors for all pattern documents
            for (Intent intent : repository.getIntents()) {
                for (String pattern : intent.getPatterns()) {
                    List<String> tokens = preprocessor.preprocess(pattern);
                    Map<String, Double> vec = tfidfVectorizer.transform(tokens);
                    patternTfidfVectors.put(pattern, vec);
                }
            }
        }
    }

    /**
     * Classifies raw input text with context awareness.
     */
    public Intent classify(String userInput, ConversationContext context) {
        List<String> userTokens = preprocessor.preprocess(userInput);

        if (userTokens.isEmpty()) {
            return null;
        }

        Set<String> userTokenSet = new HashSet<>(userTokens);
        Map<String, Double> userTfidfVec = tfidfVectorizer.transform(userTokens);

        Intent bestIntent = null;
        double bestScore = 0.0;

        String currentTopic = (context != null) ? context.getCurrentTopic() : null;

        for (Intent intent : repository.getIntents()) {
            double maxPatternScoreForIntent = 0.0;

            for (String pattern : intent.getPatterns()) {
                List<String> patternTokens = preprocessor.preprocess(pattern);
                if (patternTokens.isEmpty()) continue;

                Set<String> patternTokenSet = new HashSet<>(patternTokens);

                // Score 1: Jaccard similarity
                double jaccard = jaccardSimilarity(userTokenSet, patternTokenSet);

                // Score 2: TF-IDF Cosine similarity
                Map<String, Double> patternVec = patternTfidfVectors.getOrDefault(pattern, Map.of());
                double cosine = tfidfVectorizer.computeCosineSimilarity(userTfidfVec, patternVec);

                // Ensemble hybrid score (0.4 * Jaccard + 0.6 * Cosine)
                double rawHybridScore = (0.4 * jaccard) + (0.6 * cosine);

                if (rawHybridScore > maxPatternScoreForIntent) {
                    maxPatternScoreForIntent = rawHybridScore;
                }
            }

            double finalScore = maxPatternScoreForIntent;

            // Context-aware intent score boost
            if (currentTopic != null && intent.getTag().equalsIgnoreCase(currentTopic)) {
                finalScore *= CONTEXT_BOOST_MULTIPLIER;
            }

            if (finalScore > bestScore) {
                bestScore = finalScore;
                bestIntent = intent;
            }
        }

        if (bestScore < CONFIDENCE_THRESHOLD) {
            System.out.println("[IntentClassifier] Best hybrid score " + String.format("%.3f", bestScore)
                    + " is below threshold " + CONFIDENCE_THRESHOLD + " — fallback.");
            return null;
        }

        System.out.println("[IntentClassifier] Matched intent '" + bestIntent.getTag()
                + "' with hybrid score " + String.format("%.3f", bestScore));

        return bestIntent;
    }

    public Intent classify(String userInput) {
        return classify(userInput, null);
    }

    private double jaccardSimilarity(Set<String> setA, Set<String> setB) {
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }
}
