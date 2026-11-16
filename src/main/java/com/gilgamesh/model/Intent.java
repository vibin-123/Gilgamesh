package com.gilgamesh.model;

import java.util.List;

/**
 * Intent — Data model representing a single chatbot intent.
 */
public class Intent {

    private String tag;
    private List<String> patterns;
    private List<String> responses;
    /** Optional: the help section this intent is listed under (e.g. "Live data"). */
    private String category;
    /** Optional: an example message shown in help. Intents without one are not listed. */
    private String example;

    public Intent() {}

    public Intent(String tag, List<String> patterns, List<String> responses) {
        this.tag = tag;
        this.patterns = patterns;
        this.responses = responses;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public void setPatterns(List<String> patterns) {
        this.patterns = patterns;
    }

    public List<String> getResponses() {
        return responses;
    }

    public void setResponses(List<String> responses) {
        this.responses = responses;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    @Override
    public String toString() {
        return "Intent{tag='" + tag + "', patterns=" + (patterns != null ? patterns.size() : 0)
                + ", responses=" + (responses != null ? responses.size() : 0) + "}";
    }
}
