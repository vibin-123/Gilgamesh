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
import java.util.Optional;

/**
 * Open-Meteo client (https://open-meteo.com), which needs no API key.
 *
 * It serves two purposes: finding a place by name (coordinates, country, time zone)
 * and fetching the current weather for it.
 */
public class OpenMeteoWeatherClient implements WeatherClient, PlaceFinder {

    private static final String GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&name=";
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
            + "?current=temperature_2m,weather_code,wind_speed_10m&latitude=%s&longitude=%s";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Override
    public Optional<Place> find(String placeName) throws IOException {
        try {
            JsonObject geo = getJson(GEOCODE_URL + URLEncoder.encode(placeName, StandardCharsets.UTF_8));
            JsonArray results = geo.has("results") ? geo.getAsJsonArray("results") : null;
            if (results == null || results.isEmpty()) {
                return Optional.empty();
            }
            JsonObject place = results.get(0).getAsJsonObject();
            return Optional.of(new Place(
                    place.get("name").getAsString(),
                    place.has("country") ? place.get("country").getAsString() : "",
                    place.get("latitude").getAsDouble(),
                    place.get("longitude").getAsDouble(),
                    place.has("timezone") ? place.get("timezone").getAsString() : null));
        } catch (RuntimeException e) {
            throw new IOException("Unexpected answer from the place lookup: " + e.getMessage(), e);
        }
    }

    @Override
    public Report getCurrentWeather(String city) throws WeatherException {
        try {
            Optional<Place> found = find(city);
            if (found.isEmpty()) {
                throw new WeatherException("Unknown city: " + city, true);
            }
            Place place = found.get();

            JsonObject forecast = getJson(String.format(FORECAST_URL, place.latitude(), place.longitude()));
            JsonObject current = forecast.getAsJsonObject("current");

            return new Report(place.name(), place.country(),
                    current.get("temperature_2m").getAsDouble(),
                    current.get("wind_speed_10m").getAsDouble(),
                    current.get("weather_code").getAsInt());
        } catch (WeatherException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new WeatherException("Could not retrieve weather: " + e.getMessage(), e);
        }
    }

    private JsonObject getJson(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from " + request.uri().getHost());
        }
        try {
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Invalid JSON from " + request.uri().getHost(), e);
        }
    }
}
