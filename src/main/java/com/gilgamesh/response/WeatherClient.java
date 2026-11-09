package com.gilgamesh.response;

/**
 * Source of current-weather data. Abstracted so the handler can be tested offline.
 */
public interface WeatherClient {

    /** Current conditions for a resolved place. */
    record Report(String city, String country, double temperatureC, double windKph, int weatherCode) {}

    /** Thrown when weather cannot be retrieved. */
    class WeatherException extends Exception {
        private final boolean cityNotFound;

        public WeatherException(String message, boolean cityNotFound) {
            super(message);
            this.cityNotFound = cityNotFound;
        }

        public WeatherException(String message, Throwable cause) {
            super(message, cause);
            this.cityNotFound = false;
        }

        /** True when the service responded but knows no such city (vs. a network/parse failure). */
        public boolean isCityNotFound() {
            return cityNotFound;
        }
    }

    /**
     * Fetches current weather for the named city.
     *
     * @throws WeatherException if the city is unknown or the service is unreachable
     */
    Report getCurrentWeather(String city) throws WeatherException;
}
