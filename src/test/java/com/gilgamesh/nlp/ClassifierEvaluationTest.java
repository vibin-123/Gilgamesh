package com.gilgamesh.nlp;

import com.gilgamesh.data.IntentRepository;
import com.gilgamesh.model.Intent;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Measures classifier accuracy on phrasings the bot was NOT taught (eval-set.json).
 *
 * This is the number to watch when changing intents.json, the preprocessing pipeline,
 * or the scoring: run the test and read the "EVAL" lines. The floors below are a ratchet;
 * raise them when accuracy improves so it can never silently regress.
 */
class ClassifierEvaluationTest {

    /** Minimum share of in-scope phrasings that must reach the right intent. */
    private static final double IN_SCOPE_FLOOR = 0.85;
    /** Minimum share of out-of-scope phrasings that must fall back (no intent). */
    private static final double OUT_OF_SCOPE_FLOOR = 0.80;

    private static class EvalSet {
        List<EvalCase> cases;
    }

    private static class EvalCase {
        String text;
        String expected;
    }

    @Test
    void accuracyOnHeldOutPhrasings() throws Exception {
        EvalSet evalSet;
        try (Reader reader = new InputStreamReader(
                getClass().getResourceAsStream("/eval-set.json"), StandardCharsets.UTF_8)) {
            evalSet = new Gson().fromJson(reader, EvalSet.class);
        }
        assertNotNull(evalSet.cases);

        IntentRepository repository = new IntentRepository();
        IntentClassifier classifier = new IntentClassifier(repository, new TextPreprocessor());

        int inScope = 0, inScopeCorrect = 0, outOfScope = 0, outOfScopeCorrect = 0;
        List<String> misses = new ArrayList<>();
        Map<String, Integer> confusions = new TreeMap<>();

        // Threshold sweep: for each candidate threshold, how many cases would be right?
        double[] thresholds = {0.15, 0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60};
        int[] sweepInScope = new int[thresholds.length];
        int[] sweepOutOfScope = new int[thresholds.length];

        for (EvalCase evalCase : evalSet.cases) {
            Classification classification = classifier.classifyDetailed(evalCase.text, null);
            Intent result = classification.intent();
            String got = result == null ? null : result.getTag();

            String topTag = classification.topIntent() == null ? null : classification.topIntent().getTag();
            for (int i = 0; i < thresholds.length; i++) {
                boolean accepted = topTag != null && classification.score() >= thresholds[i];
                if (evalCase.expected == null) {
                    if (!accepted) sweepOutOfScope[i]++;
                } else if (accepted && evalCase.expected.equals(topTag)) {
                    sweepInScope[i]++;
                }
            }

            if (evalCase.expected == null) {
                outOfScope++;
                if (got == null) {
                    outOfScopeCorrect++;
                } else {
                    misses.add("'" + evalCase.text + "' should fall back but got " + got);
                }
            } else {
                assertNotNull(repository.findByTag(evalCase.expected),
                        "eval-set.json expects unknown intent tag: " + evalCase.expected);
                inScope++;
                if (evalCase.expected.equals(got)) {
                    inScopeCorrect++;
                } else {
                    misses.add("'" + evalCase.text + "' expected " + evalCase.expected + " but got " + got);
                    confusions.merge(evalCase.expected + " -> " + got, 1, Integer::sum);
                }
            }
        }

        double inScopeAccuracy = (double) inScopeCorrect / inScope;
        double outOfScopeAccuracy = (double) outOfScopeCorrect / outOfScope;

        System.out.println("EVAL in-scope accuracy:     " + inScopeCorrect + "/" + inScope
                + String.format(" (%.1f%%)", inScopeAccuracy * 100));
        System.out.println("EVAL out-of-scope fallback: " + outOfScopeCorrect + "/" + outOfScope
                + String.format(" (%.1f%%)", outOfScopeAccuracy * 100));
        for (int i = 0; i < thresholds.length; i++) {
            System.out.println(String.format("EVAL sweep threshold %.2f: in-scope %d/%d, out-of-scope %d/%d",
                    thresholds[i], sweepInScope[i], inScope, sweepOutOfScope[i], outOfScope));
        }
        confusions.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .forEach(e -> System.out.println("EVAL repeated confusion: " + e.getKey() + " x" + e.getValue()));
        misses.forEach(miss -> System.out.println("EVAL miss: " + miss));

        assertTrue(inScopeAccuracy >= IN_SCOPE_FLOOR,
                String.format("In-scope accuracy %.3f fell below the floor %.3f", inScopeAccuracy, IN_SCOPE_FLOOR));
        assertTrue(outOfScopeAccuracy >= OUT_OF_SCOPE_FLOOR,
                String.format("Out-of-scope accuracy %.3f fell below the floor %.3f", outOfScopeAccuracy, OUT_OF_SCOPE_FLOOR));
    }
}
