package com.gilgamesh.response;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.Entity;
import com.gilgamesh.model.Intent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeatherIntentHandlerTest {

    private final Intent weather = new Intent("weather", List.of("weather"), List.of("x"));

    /** Fake client recording the requested cities. */
    private static class FakeClient implements WeatherClient {
        final List<String> requested = new ArrayList<>();
        WeatherException failure;

        @Override
        public Report getCurrentWeather(String city) throws WeatherException {
            requested.add(city);
            if (failure != null) throw failure;
            return new Report(city, "France", 18.5, 12.0, 2);
        }
    }

    @Test
    void canOnlyHandleWeatherIntent() {
        WeatherIntentHandler handler = new WeatherIntentHandler(new FakeClient());
        assertTrue(handler.canHandle(weather));
        assertFalse(handler.canHandle(new Intent("datetime", List.of(), List.of())));
        assertFalse(handler.canHandle(null));
    }

    @Test
    void usesCityFromMessage() {
        FakeClient client = new FakeClient();
        String reply = new WeatherIntentHandler(client).handle(weather, "what's the weather in Paris today?", null);

        assertEquals(List.of("Paris"), client.requested);
        assertTrue(reply.contains("Paris, France"), reply);
        assertTrue(reply.contains("18.5°C"), reply);
        assertTrue(reply.contains("partly cloudy"), reply);
    }

    @Test
    void fallsBackToLocationFromContext() {
        FakeClient client = new FakeClient();
        ConversationContext context = new ConversationContext();
        context.update("weather in London", "ok", weather, List.of(new Entity("London", Entity.EntityType.LOCATION)));

        new WeatherIntentHandler(client).handle(weather, "how is the weather now", context);

        assertEquals(List.of("London"), client.requested);
    }

    @Test
    void asksForCityWhenUnknown() {
        FakeClient client = new FakeClient();
        String reply = new WeatherIntentHandler(client).handle(weather, "weather", new ConversationContext());

        assertEquals(WeatherIntentHandler.ASK_CITY_PROMPT, reply);
        assertTrue(client.requested.isEmpty());
    }

    @Test
    void unknownCityGivesHelpfulMessage() {
        FakeClient client = new FakeClient();
        client.failure = new WeatherClient.WeatherException("nope", true);

        String reply = new WeatherIntentHandler(client).handle(weather, "weather in Zzzzville", null);

        assertTrue(reply.contains("couldn't find"), reply);
        assertTrue(reply.contains("Zzzzville"), reply);
    }

    @Test
    void networkFailureNeverThrows() {
        FakeClient client = new FakeClient();
        client.failure = new WeatherClient.WeatherException("timeout", new RuntimeException("boom"));

        String reply = assertDoesNotThrow(
                () -> new WeatherIntentHandler(client).handle(weather, "weather in Paris", null));

        assertTrue(reply.contains("can't reach the weather service"), reply);
    }

    @Test
    void describesWeatherCodes() {
        assertEquals("clear skies", WeatherIntentHandler.describe(0));
        assertEquals("rain", WeatherIntentHandler.describe(63));
        assertEquals("a thunderstorm", WeatherIntentHandler.describe(95));
        assertEquals("mixed conditions", WeatherIntentHandler.describe(1234));
    }
}
