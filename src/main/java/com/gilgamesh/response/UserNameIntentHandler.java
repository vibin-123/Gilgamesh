package com.gilgamesh.response;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.PendingQuestion;
import com.gilgamesh.model.Intent;
import com.gilgamesh.nlp.EntityExtractor;

import java.util.Optional;

/**
 * Learns and recalls the user's name.
 *
 * "My name is Sam" stores the name in the conversation context (it is saved with the
 * session, so it survives restarts); "what is my name" reads it back. If the name is
 * missing the bot asks for it and reads the next message as the answer.
 */
public class UserNameIntentHandler implements IntentHandler {

    public static final String INTRODUCTION = "small_talk_name";
    public static final String RECALL = "user_name_recall";

    public static final String ASK_NAME_PROMPT = "What should I call you?";

    /** The question to re-run once the user gives a name. */
    public static final String QUERY_TEMPLATE = "my name is %s";

    private static final EntityExtractor EXTRACTOR = new EntityExtractor();

    @Override
    public boolean canHandle(Intent intent) {
        return intent != null && (INTRODUCTION.equals(intent.getTag()) || RECALL.equals(intent.getTag()));
    }

    @Override
    public String handle(Intent intent) {
        return ASK_NAME_PROMPT;
    }

    @Override
    public String handle(Intent intent, String userInput, ConversationContext context) {
        if (RECALL.equals(intent.getTag())) {
            String known = context != null ? context.getUserName() : null;
            if (known != null) {
                return "Your name is " + known + ".";
            }
            return askForName(context, "I don't know your name yet. ");
        }

        Optional<String> name = extractName(userInput);
        if (name.isEmpty()) {
            return askForName(context, "");
        }
        if (context != null) {
            context.setUserName(name.get());
        }
        return "Nice to meet you, " + name.get() + "! I'll remember that.";
    }

    private String askForName(ConversationContext context, String prefix) {
        if (context != null) {
            context.setPendingQuestion(new PendingQuestion(INTRODUCTION, PendingQuestion.SLOT_NAME, QUERY_TEMPLATE));
        }
        return prefix + ASK_NAME_PROMPT;
    }

    /**
     * Pulls the name out of an introduction such as "my name is sam" (-> "Sam").
     *
     * @return the capitalised name, or empty if the message doesn't state one
     */
    public static Optional<String> extractName(String userInput) {
        return EXTRACTOR.extractUserName(userInput);
    }

    /**
     * Normalises a bare name ("sam lee" -> "Sam Lee"), dropping trailing filler words.
     *
     * @return the capitalised name, or empty if the text isn't plausibly a name
     */
    public static Optional<String> cleanName(String rawName) {
        return EXTRACTOR.cleanUserName(rawName);
    }
}
