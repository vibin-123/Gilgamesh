package com.gilgamesh.nlp;

import com.gilgamesh.model.Intent;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TfidfVectorizer — Vectorizes text patterns and computes Cosine Similarity scoring.
 *
 * NLP Concept: TF-IDF & Cosine Similarity
 * -----------------------------------------
 * TF (Term Frequency): How frequently a word appears in a specific document.
 * IDF (Inverse Document Frequency): Measures how rare/important a word is across all documents.
 *     IDF(t) = log((Total Documents + 1) / (Document Frequency(t) + 1)) + 1
 *
 * Rare words (e.g., "weather", "author") receive higher weights than common words.
 * Cosine Similarity measures the cosine of the angle between two multi-dimensional TF-IDF vectors:
 *     Cosine(A, B) = (A • B) / (||A|| * ||B||)
 */
public class TfidfVectorizer {

    private final Map<String, Double> idfMap = new HashMap<>();
    private final Set<String> vocabulary = new HashSet<>();
    private int totalDocuments = 0;

    /**
     * Fits the TF-IDF model on a collection of intents and their pattern documents.
     */
    public void fit(List<Intent> intents, TextPreprocessor preprocessor) {
        idfMap.clear();
        vocabulary.clear();
        Map<String, Integer> documentFrequencyMap = new HashMap<>();
        totalDocuments = 0;

        for (Intent intent : intents) {
            for (String pattern : intent.getPatterns()) {
                totalDocuments++;
                List<String> tokens = preprocessor.preprocess(pattern);
                Set<String> uniqueTokensInPattern = new HashSet<>(tokens);

                for (String token : uniqueTokensInPattern) {
                    vocabulary.add(token);
                    documentFrequencyMap.put(token, documentFrequencyMap.getOrDefault(token, 0) + 1);
                }
            }
        }

        // Calculate IDF for each vocabulary word
        for (Map.Entry<String, Integer> entry : documentFrequencyMap.entrySet()) {
            String word = entry.getKey();
            int df = entry.getValue();
            double idf = Math.log((double) (totalDocuments + 1) / (df + 1)) + 1.0;
            idfMap.put(word, idf);
        }
    }

    /**
     * Converts a token list into a TF-IDF vector map.
     */
    public Map<String, Double> transform(List<String> tokens) {
        Map<String, Double> tfMap = new HashMap<>();
        if (tokens == null || tokens.isEmpty()) {
            return tfMap;
        }

        double docLength = tokens.size();
        for (String token : tokens) {
            tfMap.put(token, tfMap.getOrDefault(token, 0.0) + 1.0);
        }

        Map<String, Double> tfidfVector = new HashMap<>();
        for (Map.Entry<String, Double> entry : tfMap.entrySet()) {
            String token = entry.getKey();
            double tf = entry.getValue() / docLength;
            double idf = idfMap.getOrDefault(token, Math.log((double) (totalDocuments + 1) / 1) + 1.0);
            tfidfVector.put(token, tf * idf);
        }

        return tfidfVector;
    }

    /**
     * Computes the Cosine Similarity between two TF-IDF vectors.
     */
    public double computeCosineSimilarity(Map<String, Double> vecA, Map<String, Double> vecB) {
        if (vecA == null || vecB == null || vecA.isEmpty() || vecB.isEmpty()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (Map.Entry<String, Double> entry : vecA.entrySet()) {
            String term = entry.getKey();
            double valA = entry.getValue();
            normA += valA * valA;

            if (vecB.containsKey(term)) {
                dotProduct += valA * vecB.get(term);
            }
        }

        for (double valB : vecB.values()) {
            normB += valB * valB;
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public Set<String> getVocabulary() { return vocabulary; }
}
