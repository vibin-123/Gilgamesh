package com.gilgamesh.response;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.Entity;
import com.gilgamesh.model.Intent;
import com.gilgamesh.nlp.EntityExtractor;

import java.io.IOException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Answers date and time questions.
 *
 * It answers only what was asked ("what time is it" gets the time, "what day is it"
 * gets the date), and can answer for another place ("what time is it in Tokyo") by
 * looking up that place's time zone.
 */
public class DateTimeIntentHandler implements IntentHandler {

    private static final Logger LOG = Logger.getLogger(DateTimeIntentHandler.class.getName());

    private static final Pattern TIME_WORDS = Pattern.compile("(?i)\\b(time|clock|hour)\\b");
    private static final Pattern DATE_WORDS = Pattern.compile("(?i)\\b(date|day|today|month|year)\\b");

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);

    private final PlaceFinder placeFinder;
    private final Clock clock;
    private final EntityExtractor entityExtractor = new EntityExtractor();

    /** Local date and time only (no lookups for other places). */
    public DateTimeIntentHandler() {
        this(null, Clock.systemDefaultZone());
    }

    public DateTimeIntentHandler(PlaceFinder placeFinder) {
        this(placeFinder, Clock.systemDefaultZone());
    }

    /**
     * @param placeFinder resolves a place to its time zone; null disables "time in <place>"
     * @param clock       source of the current time (injectable for tests)
     */
    public DateTimeIntentHandler(PlaceFinder placeFinder, Clock clock) {
        this.placeFinder = placeFinder;
        this.clock = clock;
    }

    @Override
    public boolean canHandle(Intent intent) {
        return intent != null && "datetime".equals(intent.getTag());
    }

    @Override
    public String handle(Intent intent) {
        return handle(intent, null, null);
    }

    @Override
    public String handle(Intent intent, String userInput, ConversationContext context) {
        String text = userInput == null ? "" : userInput;
        boolean wantsTime = TIME_WORDS.matcher(text).find();
        boolean wantsDate = DATE_WORDS.matcher(text).find();
        if (!wantsTime && !wantsDate) {
            wantsTime = true;
            wantsDate = true;
        }

        Optional<Entity> location = placeFinder == null ? Optional.empty() : entityExtractor.extractLocation(text);
        if (location.isEmpty()) {
            ZonedDateTime now = ZonedDateTime.now(clock);
            if (wantsTime && wantsDate) {
                return "Right now, it is " + DATE.format(now) + " at " + TIME.format(now) + ".";
            }
            return wantsTime ? "It's " + TIME.format(now) + "." : "Today is " + DATE.format(now) + ".";
        }

        String placeName = location.get().getName();
        try {
            Optional<PlaceFinder.Place> found = placeFinder.find(placeName);
            if (found.isEmpty() || found.get().timeZone() == null) {
                return "I couldn't find a place called \"" + placeName + "\". Could you check the spelling or try another city?";
            }
            ZonedDateTime there = ZonedDateTime.now(clock.withZone(ZoneId.of(found.get().timeZone())));
            String where = "In " + found.get().displayName() + " it's ";
            if (wantsTime && wantsDate) {
                return where + TIME.format(there) + " on " + DATE.format(there) + ".";
            }
            return where + (wantsTime ? "currently " + TIME.format(there) : DATE.format(there)) + ".";
        } catch (IOException | DateTimeException e) {
            LOG.log(Level.WARNING, "Time zone lookup failed", e);
            return "I can't look up the time in " + placeName + " right now. Please try again in a moment.";
        }
    }
}
