package com.gilgamesh.response;

/**
 * What a handler answers: the text to show, plus optional structured data behind it.
 *
 * The text is all a terminal needs. A graphical UI can use the data to draw something
 * richer, for example a weather card from the temperature and conditions.
 *
 * @param text     the reply to show the user
 * @param dataType what kind of data is attached (e.g. "weather"), or null if none
 * @param data     the structured data, or null if none
 */
public record HandlerReply(String text, String dataType, Object data) {

    /** A reply that is only text. */
    public static HandlerReply text(String text) {
        return new HandlerReply(text, null, null);
    }
}
