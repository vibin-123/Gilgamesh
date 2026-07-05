package com.gilgamesh.response;

import com.gilgamesh.model.Intent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles datetime related intents by returning the current system date and time.
 */
public class DateTimeIntentHandler implements IntentHandler {

    @Override
    public boolean canHandle(Intent intent) {
        return intent != null && "datetime".equals(intent.getTag());
    }

    @Override
    public String handle(Intent intent) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a");
        return "Right now, it is " + now.format(formatter) + ".";
    }
}
