package com.gilgamesh.response;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * WeatherClient backed by Open-Meteo (https://open-meteo.com), which needs no API key.
 * Two calls: geocode the city name, then fetch current conditions for its coordinates.
 */
public class OpenMeteoWeatherClient implements WeatherClient {

    private static final String GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&name=";
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
            + "?current=temperature_2m,weather_code,wind_speed_10m&latitude=%s&longitude=%s";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Override
    public Report getCurrentWeather(String city) throws WeatherException {
        try {
            JsonObject geo = getJson(GEOCODE_URL + URLEncoder.encode(city, StandardCharsets.UTF_8));
            JsonArray results = geo.has("results") ? geo.getAsJsonArray("results") : null;
            if (results == null || results.isEmpty()) {
                throw new WeatherException("Unknown city: " + city, true);
            }
            JsonObject place = results.get(0).getAsJsonObject();
            String name = place.get("name").getAsString();
            String country = place.has("country") ? place.get("country").getAsString() : "";

            JsonObject forecast = getJson(String.format(FORECAST_URL,
                    place.get("latitude").getAsString(), place.get("longitude").getAsString()));
            JsonObject current = forecast.getAsJsonObject("current");

            return new Report(name, country,
                    current.get("temperature_2m").getAsDouble(),
                    current.get("wind_speed_10m").getAsDouble(),
                    current.get("weather_code").getAsInt());
        } catch (WeatherException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WeatherException("Weather request interrupted", e);
        } catch (IOException | RuntimeException e) {
            throw new WeatherException("Could not retrieve weather: " + e.getMessage(), e);
        }
    }

    private JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from " + request.uri().getHost());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
