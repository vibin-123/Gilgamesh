package com.gilgamesh;

import java.util.List;

/**
 * BotReply — Everything the bot knows about one answer, not just its text.
 *
 * The terminal only prints {@link #text()}. A graphical UI can also show how the message
 * was understood (intent, confidence, entities, resolved text) and draw richer answers
 * from {@link #data()}.
 *
 * @param text               the reply to show the user
 * @param kind               what sort of reply this is (see {@link Kind})
 * @param intent             tag of the intent the message was matched to, or null
 * @param confidence         how strongly it matched, 0..1
 * @param runnerUpIntent     tag of the second-best intent, or null
 * @param runnerUpConfidence score of the second-best intent, 0..1
 * @param entities           the things named in the message (after "it" was resolved)
 * @param messageType        how the message relates to the conversation (STANDALONE, FOLLOW_UP, ...)
 * @param resolvedInput      the message as the bot read it, e.g. "who created Java?" for "who created it?"
 * @param topic              what the conversation is about after this turn
 * @param suspendedTopics    earlier topics that can be resumed with "back to ..."
 * @param userName           the user's name if known, else null
 * @param pendingQuestion    what the bot is now waiting to be told, or null
 * @param dataType           kind of structured data attached (e.g. "weather"), or null
 * @param data               the structured data, or null
 */
public record BotReply(
        String text,
        Kind kind,
        String intent,
        double confidence,
        String runnerUpIntent,
        double runnerUpConfidence,
        List<EntityInfo> entities,
        String messageType,
        String resolvedInput,
        String topic,
        List<String> suspendedTopics,
        String userName,
        PendingInfo pendingQuestion,
        String dataType,
        Object data) {

    /** What sort of reply this is. */
    public enum Kind {
        /** The bot understood and answered. */
        ANSWER,
        /** The bot did not understand. */
        FALLBACK,
        /** Two meanings were equally likely, so the bot asks which was meant. */
        CLARIFICATION,
        /** The bot needs one more piece of information (a city, a name) before it can answer. */
        QUESTION,
        /** Something went wrong inside the bot. */
        ERROR
    }

    /** An entity named in the message. */
    public record EntityInfo(String name, String type) {}

    /** What the bot is waiting for: the kind of answer, and the choices if it offered any. */
    public record PendingInfo(String slot, List<String> options) {}

    /** The reply used when processing a message failed. */
    public static BotReply error(String text) {
        return new BotReply(text, Kind.ERROR, null, 0.0, null, 0.0, List.of(), null, null, null,
                List.of(), null, null, null, null);
    }
}
