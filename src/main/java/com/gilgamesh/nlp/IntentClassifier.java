package com.gilgamesh.nlp;

import java.util.logging.Logger;

import com.gilgamesh.data.IntentRepository;
import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.model.Intent;

import java.util.ArrayList;
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

    private static final Logger LOG = Logger.getLogger(IntentClassifier.class.getName());

    /**
     * Chosen from the threshold sweep printed by ClassifierEvaluationTest: below 0.40 the bot
     * answers most out-of-scope questions with a wrong intent; above it, in-scope accuracy drops fast.
     */
    public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.40;
    private static final double CONTEXT_BOOST_MULTIPLIER = 1.25;

    private final IntentRepository repository;
    private final TextPreprocessor preprocessor;
    private final TfidfVectorizer tfidfVectorizer;
    /** Precomputed per-pattern data (token set + TF-IDF vector), keyed by intent. */
    private record PatternData(Set<String> tokens, Map<String, Double> vector) {}

    private final Map<Intent, List<PatternData>> patternCache = new HashMap<>();
    private double confidenceThreshold = DEFAULT_CONFIDENCE_THRESHOLD;

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

            // 3. Precompute token sets and TF-IDF vectors for all patterns once,
            // so classify() never has to re-preprocess them.
            for (Intent intent : repository.getIntents()) {
                List<PatternData> data = new ArrayList<>();
                for (String pattern : intent.getPatterns()) {
                    List<String> tokens = preprocessor.preprocess(pattern);
                    if (tokens.isEmpty()) continue;
                    data.add(new PatternData(new HashSet<>(tokens), tfidfVectorizer.transform(tokens)));
                }
                patternCache.put(intent, data);
            }
        }
    }

    /**
     * Classifies raw input text with context awareness.
     *
     * @return the matched intent, or null if nothing scored above the confidence threshold
     */
    public Intent classify(String userInput, ConversationContext context) {
        return classifyDetailed(userInput, context).intent();
    }

    public Intent classify(String userInput) {
        return classify(userInput, null);
    }

    /**
     * Classifies raw input and returns the full evidence: best intent, its score,
     * and the runner-up. Use {@link Classification#intent()} for the final decision.
     */
    public Classification classifyDetailed(String userInput, ConversationContext context) {
        List<String> userTokens = preprocessor.preprocess(userInput);

        if (userTokens.isEmpty()) {
            return Classification.none(confidenceThreshold);
        }

        Set<String> userTokenSet = new HashSet<>(userTokens);
        Map<String, Double> userTfidfVec = tfidfVectorizer.transform(userTokens);

        Intent bestIntent = null;
        double bestScore = 0.0;
        Intent secondIntent = null;
        double secondScore = 0.0;

        String currentTopic = (context != null) ? context.getCurrentTopic() : null;

        for (Intent intent : repository.getIntents()) {
            double maxPatternScoreForIntent = 0.0;

            for (PatternData pattern : patternCache.getOrDefault(intent, List.of())) {
                // Score 1: Jaccard similarity
                double jaccard = jaccardSimilarity(userTokenSet, pattern.tokens());

                // Score 2: TF-IDF Cosine similarity
                double cosine = tfidfVectorizer.computeCosineSimilarity(userTfidfVec, pattern.vector());

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
                secondScore = bestScore;
                secondIntent = bestIntent;
                bestScore = finalScore;
                bestIntent = intent;
            } else if (finalScore > secondScore) {
                secondScore = finalScore;
                secondIntent = intent;
            }
        }

        Classification result = new Classification(bestIntent, bestScore, secondIntent, secondScore, confidenceThreshold);

        if (!result.isMatch()) {
            LOG.fine("[IntentClassifier] Best hybrid score " + String.format("%.3f", bestScore)
                    + " is below threshold " + confidenceThreshold + " — fallback.");
        } else {
            LOG.fine("[IntentClassifier] Matched intent '" + bestIntent.getTag()
                    + "' with hybrid score " + String.format("%.3f", bestScore)
                    + (secondIntent != null ? " (runner-up '" + secondIntent.getTag() + "' "
                            + String.format("%.3f", secondScore) + ")" : ""));
        }

        return result;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * IDF-weighted Jaccard similarity: shared words divided by all words, where each
     * word counts by its rarity instead of counting 1. Sharing a common word such as
     * "what" or "you" is weak evidence; sharing "weather" is strong evidence; and a
     * word the bot has never seen ("capital", "France") counts heavily against a match.
     */
    private double jaccardSimilarity(Set<String> setA, Set<String> setB) {
        double shared = 0.0;
        double total = 0.0;
        for (String token : setA) {
            double weight = tfidfVectorizer.idf(token);
            total += weight;
            if (setB.contains(token)) {
                shared += weight;
            }
        }
        for (String token : setB) {
            if (!setA.contains(token)) {
                total += tfidfVectorizer.idf(token);
            }
        }
        return total == 0.0 ? 0.0 : shared / total;
    }
}
