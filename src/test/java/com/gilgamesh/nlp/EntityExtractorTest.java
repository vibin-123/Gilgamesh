package com.gilgamesh.nlp;

import com.gilgamesh.memory.Entity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EntityExtractorTest {

    private final EntityExtractor extractor = new EntityExtractor();

    @Test
    void extractsKnownLanguage() {
        List<Entity> entities = extractor.extractEntities("Tell me about Java");
        assertEquals(1, entities.size());
        assertEquals("Java", entities.get(0).getName());
        assertEquals(Entity.EntityType.LANGUAGE, entities.get(0).getType());
    }

    @Test
    void extractsPerson() {
        List<Entity> entities = extractor.extractEntities("Who is James Gosling?");
        assertTrue(entities.stream().anyMatch(e -> e.getName().equals("James Gosling")
                && e.getType() == Entity.EntityType.PERSON));
    }

    @Test
    void blankInputGivesNoEntities() {
        assertTrue(extractor.extractEntities("").isEmpty());
        assertTrue(extractor.extractEntities(null).isEmpty());
    }

    @Test
    void extractsLocationFromWeatherQuery() {
        for (String q : List.of("what's the weather in Paris?", "weather in paris", "forecast for Paris today",
                "How is the weather at Paris right now")) {
            Optional<Entity> location = extractor.extractLocation(q);
            assertTrue(location.isPresent(), q);
            assertEquals("Paris", location.get().getName(), q);
            assertEquals(Entity.EntityType.LOCATION, location.get().getType(), q);
        }
    }

    @Test
    void extractsMultiWordLocation() {
        Optional<Entity> location = extractor.extractLocation("weather in new york tomorrow");
        assertEquals("New York", location.orElseThrow().getName());
    }

    @Test
    void extractsBareFollowUpLocation() {
        assertEquals("London", extractor.extractLocation("and in London?").orElseThrow().getName());
        assertEquals("Tokyo", extractor.extractLocation("in tokyo").orElseThrow().getName());
    }

    @Test
    void weatherQueryDoesNotLeakPhraseAsGeneralEntity() {
        List<Entity> entities = extractor.extractEntities("What is the weather in Paris?");
        assertEquals(1, entities.size(), entities.toString());
        assertEquals(Entity.EntityType.LOCATION, entities.get(0).getType());
    }

    @Test
    void extractsLocationFromTimeQuery() {
        assertEquals("Tokyo", extractor.extractLocation("what time is it in Tokyo?").orElseThrow().getName());
        assertEquals("New York", extractor.extractLocation("current date in new york right now").orElseThrow().getName());
        assertTrue(extractor.extractLocation("what time is it in the morning").isEmpty());
        assertTrue(extractor.extractLocation("what time is it").isEmpty());
    }

    @Test
    void masksThePlaceSoAnyCityMatchesTheSamePattern() {
        assertEquals("weather in {place}", extractor.maskLocation("weather in Ouagadougou"));
        assertEquals("what time is it in {place}?", extractor.maskLocation("what time is it in Tokyo?"));
        assertEquals("forecast for {place} tomorrow", extractor.maskLocation("forecast for New York tomorrow"));
        assertEquals("tell me a joke", extractor.maskLocation("tell me a joke"));
        assertEquals("for example", extractor.maskLocation("for example"));
        assertNull(extractor.maskLocation(null));
    }

    @Test
    void everydayWordsAreNotLanguages() {
        assertTrue(extractor.extractEntities("I have to go now").isEmpty());
        assertEquals("Go", extractor.extractEntities("what is go").get(0).getName());
        assertEquals("C++", extractor.extractEntities("tell me about cpp").get(0).getName());
    }

    @Test
    void ignoresNonPlaces() {
        assertTrue(extractor.extractLocation("weather").isEmpty());
        assertTrue(extractor.extractLocation("what is the weather in general").isEmpty());
        assertTrue(extractor.extractLocation("tell me about Java").isEmpty());
        assertTrue(extractor.toLocation("here").isEmpty());
        assertTrue(extractor.toLocation(null).isEmpty());
    }
}
