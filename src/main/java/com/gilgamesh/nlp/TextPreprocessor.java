package com.gilgamesh.nlp;

import opennlp.tools.tokenize.SimpleTokenizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * TextPreprocessor — Performs the NLP preprocessing pipeline on raw text.
 *
 * NLP Pipeline Steps:
 * 1. TOKENIZATION (OpenNLP SimpleTokenizer)
 * 2. CASE NORMALIZATION (Lowercasing)
 * 3. SPELL CORRECTION (Fuzzy Levenshtein Edit Distance)
 * 4. STOPWORD REMOVAL
 * 5. SYNONYM NORMALIZATION (Mapping to canonical synonyms)
 * 6. STEMMING (Porter Stemmer algorithm)
 */
public class TextPreprocessor {

    private final SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
    private final PorterStemmer stemmer = new PorterStemmer();
    private final SynonymProvider synonymProvider = new SynonymProvider();
    private final SpellChecker spellChecker = new SpellChecker();

    private boolean useStemming = true;
    private boolean useSynonyms = true;
    private boolean useSpellChecking = true;

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the",
            "i", "me", "my", "you", "your", "he", "she", "it", "we", "they",
            "him", "her", "us", "them", "its", "our", "their",
            "in", "on", "at", "to", "for", "of", "with", "by", "from", "up",
            "into", "through", "during", "before", "after",
            "and", "but", "or", "nor", "so", "yet",
            "is", "am", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did",
            "will", "would", "shall", "should", "may", "might", "must",
            "can", "could",
            "not", "no", "this", "that", "these", "those",
            "if", "then", "than", "too", "very", "just",
            "there", "here", "all", "any", "each", "some",
            "s", "t", "re", "ve", "ll", "d", "m"
    );

    public TextPreprocessor() {}

    public TextPreprocessor(boolean useStemming, boolean useSynonyms, boolean useSpellChecking) {
        this.useStemming = useStemming;
        this.useSynonyms = useSynonyms;
        this.useSpellChecking = useSpellChecking;
    }

    public void registerVocabulary(Collection<String> words) {
        if (useSpellChecking) {
            spellChecker.addWords(words);
        }
    }

    /**
     * Preprocesses raw user input through the full NLP pipeline.
     */
    public List<String> preprocess(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        // Step 1: Tokenization
        String[] rawTokens = tokenizer.tokenize(input);

        List<String> processed = new ArrayList<>();
        for (String token : rawTokens) {
            // Step 2: Case normalization
            String lower = token.toLowerCase();

            // Step 3: Remove punctuation
            if (!lower.matches("[a-z0-9]+")) {
                continue;
            }

            // Step 4: Spell checking
            if (useSpellChecking) {
                lower = spellChecker.correctToken(lower);
            }

            // Step 5: Stopword removal
            if (STOPWORDS.contains(lower)) {
                continue;
            }

            // Step 6: Synonym resolution
            if (useSynonyms) {
                lower = synonymProvider.getCanonicalForm(lower);
            }

            // Step 7: Stemming
            if (useStemming) {
                lower = stemmer.stem(lower);
            }

            processed.add(lower);
        }

        return processed;
    }

    public boolean isUseStemming() { return useStemming; }
    public void setUseStemming(boolean useStemming) { this.useStemming = useStemming; }

    public boolean isUseSynonyms() { return useSynonyms; }
    public void setUseSynonyms(boolean useSynonyms) { this.useSynonyms = useSynonyms; }

    public boolean isUseSpellChecking() { return useSpellChecking; }
    public void setUseSpellChecking(boolean useSpellChecking) { this.useSpellChecking = useSpellChecking; }
}
