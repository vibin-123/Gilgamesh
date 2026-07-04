package com.gilgamesh.model;

import java.util.List;

/**
 * Intent — Data model representing a single chatbot intent.
 *
 * NLP Concept: Intent Recognition
 * --------------------------------
 * In conversational AI, an "intent" represents the purpose or goal behind a user's
 * message. For example, "hello" and "hi there" both express the same GREETING intent.
 *
 * Each intent consists of:
 *   - tag:       A unique identifier (e.g., "greeting", "goodbye")
 *   - patterns:  Example phrases a user might say to express this intent.
 *                These serve as training examples for our classifier.
 *   - responses: Possible replies the bot can give when this intent is detected.
 *                Having multiple responses adds conversational variety.
 *
 * This class is a plain POJO (Plain Old Java Object) that Gson deserializes
 * directly from intents.json. No annotations are needed because the JSON field
 * names match the Java field names exactly.
 */
public class Intent {

    private String tag;
    private List<String> patterns;
    private List<String> responses;

    // --- Getters ---

    public String getTag() {
        return tag;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public List<String> getResponses() {
        return responses;
    }

    @Override
    public String toString() {
        return "Intent{tag='" + tag + "', patterns=" + patterns.size()
                + ", responses=" + responses.size() + "}";
    }
}
