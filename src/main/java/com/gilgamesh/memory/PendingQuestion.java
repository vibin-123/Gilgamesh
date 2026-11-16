package com.gilgamesh.memory;

import java.util.List;

/**
 * PendingQuestion — A question the bot just asked and is waiting to have answered.
 *
 * NLP Concept: Slot Filling
 * -------------------------
 * Some intents need a piece of information (a "slot") before they can be answered:
 * weather needs a city. When the slot is missing the bot asks for it and remembers
 * what it asked. The user's next message ("Paris") is then read as the answer to that
 * question instead of as a brand-new message.
 *
 * A pending question is only valid for the very next user message.
 *
 * @param intentTag     the intent that asked the question
 * @param slot          what kind of answer is expected: {@link #SLOT_LOCATION}, {@link #SLOT_ENTITY},
 *                      {@link #SLOT_NAME} or {@link #SLOT_CHOICE}
 * @param queryTemplate the full question to run once the slot is known, with %s for the value (e.g. "weather in %s")
 * @param options       for {@link #SLOT_CHOICE}: the candidate messages the user can pick from; otherwise empty
 */
public record PendingQuestion(String intentTag, String slot, String queryTemplate, List<String> options) {

    public static final String SLOT_LOCATION = "location";
    public static final String SLOT_ENTITY = "entity";
    public static final String SLOT_CHOICE = "choice";
    public static final String SLOT_NAME = "name";

    public PendingQuestion {
        options = options != null ? List.copyOf(options) : List.of();
    }

    public PendingQuestion(String intentTag, String slot, String queryTemplate) {
        this(intentTag, slot, queryTemplate, List.of());
    }

    /** A "did you mean A or B?" question. */
    public static PendingQuestion choice(List<String> options) {
        return new PendingQuestion(null, SLOT_CHOICE, "%s", options);
    }
}
