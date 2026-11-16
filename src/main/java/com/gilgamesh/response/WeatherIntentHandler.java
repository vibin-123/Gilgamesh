package com.gilgamesh.response;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.Entity;
import com.gilgamesh.memory.PendingQuestion;
import com.gilgamesh.model.Intent;
import com.gilgamesh.nlp.EntityExtractor;

import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Answers weather queries with live data from a {@link WeatherClient}.
 *
 * The city comes from the user's message ("weather in Paris"), else from the most
 * recent LOCATION entity in the conversation, else the bot asks for one.
 */
public class WeatherIntentHandler implements IntentHandler {

    private static final Logger LOG = Logger.getLogger(WeatherIntentHandler.class.getName());

    /** Reply used when no city is known. */
    public static final String ASK_CITY_PROMPT = "Which city would you like the weather for?";

    private final WeatherClient client;
    private final EntityExtractor entityExtractor = new EntityExtractor();

    public WeatherIntentHandler(WeatherClient client) {
        this.client = client;
    }

    @Override
    public boolean canHandle(Intent intent) {
        return intent != null && "weather".equals(intent.getTag());
    }

    @Override
    public String handle(Intent intent) {
        return handle(intent, null, null);
    }

    @Override
    public String handle(Intent intent, String userInput, ConversationContext context) {
        Optional<Entity> location = entityExtractor.extractLocation(userInput);
        if (location.isEmpty() && context != null) {
            location = Optional.ofNullable(context.getMostRecentEntityByType(Entity.EntityType.LOCATION));
        }
        if (location.isEmpty()) {
            if (context != null) {
                // Remember the question so the next message ("Paris") is read as its answer
                context.setPendingQuestion(new PendingQuestion("weather", PendingQuestion.SLOT_LOCATION, "weather in %s"));
            }
            return ASK_CITY_PROMPT;
        }

        String city = location.get().getName();
        try {
            return format(client.getCurrentWeather(city));
        } catch (WeatherClient.WeatherException e) {
            if (e.isCityNotFound()) {
                return "I couldn't find a place called \"" + city + "\". Could you check the spelling or try another city?";
            }
            LOG.log(Level.WARNING, "Weather lookup failed", e);
            return "I can't reach the weather service right now. Please try again in a moment.";
        }
    }

    static String format(WeatherClient.Report report) {
        double fahrenheit = report.temperatureC() * 9 / 5 + 32;
        String place = report.country().isBlank() ? report.city() : report.city() + ", " + report.country();
        return String.format(Locale.ROOT, "In %s it's currently %.1f°C (%.1f°F) with %s and wind at %.0f km/h.",
                place, report.temperatureC(), fahrenheit, describe(report.weatherCode()), report.windKph());
    }

    /** Maps WMO weather interpretation codes (as used by Open-Meteo) to a phrase. */
    static String describe(int code) {
        return switch (code) {
            case 0 -> "clear skies";
            case 1 -> "mainly clear skies";
            case 2 -> "partly cloudy skies";
            case 3 -> "overcast skies";
            case 45, 48 -> "fog";
            case 51, 53, 55 -> "drizzle";
            case 56, 57 -> "freezing drizzle";
            case 61, 63, 65 -> "rain";
            case 66, 67 -> "freezing rain";
            case 71, 73, 75, 77 -> "snow";
            case 80, 81, 82 -> "rain showers";
            case 85, 86 -> "snow showers";
            case 95 -> "a thunderstorm";
            case 96, 99 -> "a thunderstorm with hail";
            default -> "mixed conditions";
        };
    }
}
