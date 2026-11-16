package com.gilgamesh.nlp;

import com.gilgamesh.model.Intent;

/**
 * Classification — The full result of classifying one message.
 *
 * Instead of only "the winning intent", this keeps the evidence behind the decision:
 * how strong the best match was, and which intent came second. That lets the bot say
 * "did you mean X or Y?" when two intents are nearly tied, and lets a UI show confidence.
 *
 * @param topIntent     best-scoring intent, even if its score is below the threshold (null if nothing scored)
 * @param score         score of the best intent (0..~1; can exceed 1 slightly with the context boost)
 * @param runnerUp      second-best intent (null if none scored)
 * @param runnerUpScore score of the second-best intent
 * @param threshold     minimum score for the top intent to count as a match
 */
public record Classification(Intent topIntent, double score, Intent runnerUp, double runnerUpScore, double threshold) {

    /** A classification with no candidates at all (e.g. empty input). */
    public static Classification none(double threshold) {
        return new Classification(null, 0.0, null, 0.0, threshold);
    }

    /** True when the best intent scored high enough to be used. */
    public boolean isMatch() {
        return topIntent != null && score >= threshold;
    }

    /** The matched intent, or null when the bot should fall back. */
    public Intent intent() {
        return isMatch() ? topIntent : null;
    }

    /**
     * True when the top two intents are both acceptable matches and so close that
     * picking one would be a guess.
     *
     * @param margin maximum allowed gap, as a fraction of the top score (e.g. 0.1 = within 10%)
     */
    public boolean isAmbiguous(double margin) {
        return isMatch() && runnerUp != null && runnerUpScore >= threshold
                && (score - runnerUpScore) <= margin * score;
    }
}
