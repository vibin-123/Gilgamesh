package com.gilgamesh.response;

import com.gilgamesh.model.Intent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DateTimeIntentHandlerTest {

    /** Tuesday 17 November 2026, 12:30 UTC. */
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T12:30:00Z"), ZoneOffset.UTC);
    private final Intent datetime = new Intent("datetime", List.of("what time is it"), List.of("x"));
    private final List<String> lookedUp = new ArrayList<>();

    private final PlaceFinder finder = name -> {
        lookedUp.add(name);
        return switch (name) {
            case "Tokyo" -> Optional.of(new PlaceFinder.Place("Tokyo", "Japan", 35.7, 139.7, "Asia/Tokyo"));
            case "Honolulu" -> Optional.of(new PlaceFinder.Place("Honolulu", "United States", 21.3, -157.9, "Pacific/Honolulu"));
            case "Offline" -> throw new IOException("no network");
            default -> Optional.empty();
        };
    };

    private final DateTimeIntentHandler handler = new DateTimeIntentHandler(finder, clock);

    private String ask(String question) {
        return handler.handle(datetime, question, null);
    }

    @Test
    void answersOnlyWhatWasAsked() {
        assertEquals("It's 12:30 PM.", ask("what time is it?"));
        assertEquals("Today is Tuesday, November 17, 2026.", ask("what day is it"));
        assertEquals("Today is Tuesday, November 17, 2026.", ask("what's today's date"));
        assertEquals("Right now, it is Tuesday, November 17, 2026 at 12:30 PM.", ask("date and time please"));
        assertTrue(lookedUp.isEmpty(), "local questions need no lookup");
    }

    @Test
    void givesBothWhenNeitherIsNamed() {
        assertEquals("Right now, it is Tuesday, November 17, 2026 at 12:30 PM.", handler.handle(datetime));
    }

    @Test
    void answersForAnotherPlaceInItsTimeZone() {
        assertEquals("In Tokyo, Japan it's currently 9:30 PM.", ask("what time is it in Tokyo?"));
        assertEquals("In Tokyo, Japan it's Tuesday, November 17, 2026.", ask("what is the date in tokyo"));
        assertEquals(List.of("Tokyo", "Tokyo"), lookedUp);
    }

    @Test
    void theDateCanDifferFromTheLocalOne() {
        // 02:30 UTC on Tuesday is still 16:30 on Monday in Honolulu (UTC-10)
        Clock lateMonday = Clock.fixed(Instant.parse("2026-11-17T02:30:00Z"), ZoneOffset.UTC);
        String reply = new DateTimeIntentHandler(finder, lateMonday).handle(datetime, "what day is it in Honolulu", null);
        assertEquals("In Honolulu, United States it's Monday, November 16, 2026.", reply);
    }

    @Test
    void unknownPlaceAndLookupFailureGiveFriendlyReplies() {
        assertTrue(ask("what time is it in Atlantis").contains("couldn't find a place called \"Atlantis\""));
        String offline = assertDoesNotThrow(() -> ask("what time is it in Offline"));
        assertTrue(offline.contains("can't look up the time"), offline);
    }

    @Test
    void timesOfDayAreNotPlaces() {
        assertEquals("It's 12:30 PM.", ask("what time is it in the morning"));
        assertTrue(lookedUp.isEmpty());
    }

    @Test
    void withoutAPlaceFinderItStaysLocal() {
        DateTimeIntentHandler localOnly = new DateTimeIntentHandler(null, clock);
        assertEquals("It's 12:30 PM.", localOnly.handle(datetime, "what time is it in Tokyo", null));
        assertTrue(localOnly.canHandle(datetime));
        assertFalse(localOnly.canHandle(new Intent("weather", List.of(), List.of())));
    }
}
