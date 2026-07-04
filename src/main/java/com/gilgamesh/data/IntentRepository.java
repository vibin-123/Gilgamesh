package com.gilgamesh.data;

import com.gilgamesh.model.Intent;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * IntentRepository — Loads and provides access to intent definitions.
 *
 * NLP Concept: Training Data / Knowledge Base
 * --------------------------------------------
 * Every NLP classifier needs data to classify against. In machine-learning-based
 * NLP systems, this data is used to train a statistical model. In our simpler
 * keyword-matching approach, the patterns serve as a lookup table that we compare
 * user input against at runtime.
 *
 * The intents.json file acts as our "knowledge base" — it defines:
 *   - What patterns (example phrases) map to each intent
 *   - What responses are appropriate for each intent
 *
 * Separating data from code (loading from a JSON file rather than hardcoding)
 * follows the principle of data-driven design: you can add new intents or
 * modify responses without changing any Java code.
 */
public class IntentRepository {

    private static final String INTENTS_FILE = "/intents.json";

    private final List<Intent> intents;

    /**
     * Wrapper class matching the JSON structure: { "intents": [...] }
     * Gson maps the top-level "intents" array to this field automatically.
     */
    private static class IntentData {
        List<Intent> intents;
    }

    /**
     * Loads intents from the classpath resource intents.json.
     *
     * We load from the classpath (not the filesystem) so the intents file
     * is bundled inside the JAR and works regardless of the working directory.
     *
     * @throws RuntimeException if the file is missing or contains invalid JSON
     */
    public IntentRepository() {
        this.intents = loadIntents();
    }

    private List<Intent> loadIntents() {
        // Load the JSON file from the classpath (src/main/resources/)
        InputStream stream = getClass().getResourceAsStream(INTENTS_FILE);
        if (stream == null) {
            throw new RuntimeException(
                    "Could not find " + INTENTS_FILE + " on the classpath. "
                    + "Ensure it exists in src/main/resources/."
            );
        }

        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            IntentData data = gson.fromJson(reader, IntentData.class);

            if (data == null || data.intents == null || data.intents.isEmpty()) {
                throw new RuntimeException(
                        "intents.json is empty or has no 'intents' array."
                );
            }

            System.out.println("[IntentRepository] Loaded " + data.intents.size() + " intents: "
                    + data.intents.stream()
                            .map(Intent::getTag)
                            .toList());

            return data.intents;

        } catch (JsonSyntaxException e) {
            throw new RuntimeException("intents.json contains invalid JSON: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load intents: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the full list of loaded intents.
     *
     * @return An unmodifiable view of intent definitions
     */
    public List<Intent> getIntents() {
        return intents;
    }
}
