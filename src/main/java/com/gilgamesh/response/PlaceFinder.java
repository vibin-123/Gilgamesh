package com.gilgamesh.response;

import java.io.IOException;
import java.util.Optional;

/**
 * Looks a place name up and returns where it is and which time zone it uses.
 * Abstracted so handlers can be tested offline.
 */
public interface PlaceFinder {

    /** A resolved place. */
    record Place(String name, String country, double latitude, double longitude, String timeZone) {

        /** "Paris, France", or just "Paris" when the country is unknown. */
        public String displayName() {
            return country == null || country.isBlank() ? name : name + ", " + country;
        }
    }

    /**
     * @return the best match for the name, or empty if no such place is known
     * @throws IOException if the lookup service cannot be reached or answers with garbage
     */
    Optional<Place> find(String placeName) throws IOException;
}
