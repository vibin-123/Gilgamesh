package com.gilgamesh.nlp;

import com.gilgamesh.model.Intent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TfidfVectorizerTest {

    @Test
    public void testVectorizationAndCosineSimilarity() {
        TfidfVectorizer vectorizer = new TfidfVectorizer();
        TextPreprocessor preprocessor = new TextPreprocessor();

        Intent weatherIntent = new Intent();
        weatherIntent.setTag("weather");
        weatherIntent.setPatterns(List.of("what is the weather today", "weather forecast"));

        vectorizer.fit(List.of(weatherIntent), preprocessor);

        Map<String, Double> vec1 = vectorizer.transform(preprocessor.preprocess("weather forecast today"));
        Map<String, Double> vec2 = vectorizer.transform(preprocessor.preprocess("weather forecast"));

        double sim = vectorizer.computeCosineSimilarity(vec1, vec2);
        assertTrue(sim > 0.5, "Cosine similarity for overlapping weather tokens should be high");
    }
}
