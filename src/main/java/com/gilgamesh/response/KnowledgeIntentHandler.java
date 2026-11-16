package com.gilgamesh.response;

import com.gilgamesh.data.KnowledgeBase;
import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.Entity;
import com.gilgamesh.memory.PendingQuestion;
import com.gilgamesh.model.Intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

/**
 * Answers the generic entity intents ("what is {entity}", "who created {entity}",
 * "advantages of {entity}") by looking the subject up in the {@link KnowledgeBase}.
 *
 * The subject comes from the user's message, else from the most recent known entity
 * in the conversation, else the bot asks which one is meant.
 */
public class KnowledgeIntentHandler implements IntentHandler {

    public static final String ABOUT = "entity_about";
    public static final String CREATOR = "entity_creator";
    public static final String ADVANTAGES = "entity_advantages";

    public static final String ASK_ENTITY_PROMPT = "Which programming language do you mean?";

    /** For each intent: the question to re-run once the user names the subject. */
    private static final Map<String, String> QUERY_TEMPLATES = Map.of(
            ABOUT, "tell me about %s",
            CREATOR, "who created %s",
            ADVANTAGES, "advantages of %s");

    private final KnowledgeBase knowledgeBase;
    private final Random random = new Random();

    public KnowledgeIntentHandler(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    /** True if the tag is one of the generic entity intents. */
    public static boolean isEntityIntent(String tag) {
        return tag != null && QUERY_TEMPLATES.containsKey(tag);
    }

    @Override
    public boolean canHandle(Intent intent) {
        return intent != null && isEntityIntent(intent.getTag());
    }

    @Override
    public String handle(Intent intent) {
        return ASK_ENTITY_PROMPT;
    }

    @Override
    public String handle(Intent intent, String userInput, ConversationContext context) {
        List<KnowledgeBase.Entry> subjects = new ArrayList<>(knowledgeBase.findEntries(userInput));

        if (subjects.isEmpty() && context != null) {
            // No subject named: fall back to the most recent known entity in the conversation
            List<Entity> recent = context.getRecentEntities();
            for (int i = recent.size() - 1; i >= 0 && subjects.isEmpty(); i--) {
                KnowledgeBase.Entry entry = knowledgeBase.find(recent.get(i).getName());
                if (entry != null) subjects.add(entry);
            }
        }

        if (subjects.isEmpty()) {
            if (context != null) {
                context.setPendingQuestion(new PendingQuestion(
                        intent.getTag(), PendingQuestion.SLOT_ENTITY, QUERY_TEMPLATES.get(intent.getTag())));
            }
            return ASK_ENTITY_PROMPT;
        }

        Function<KnowledgeBase.Entry, List<String>> field = switch (intent.getTag()) {
            case CREATOR -> KnowledgeBase.Entry::getCreatorFact;
            case ADVANTAGES -> KnowledgeBase.Entry::getAdvantages;
            default -> KnowledgeBase.Entry::getAbout;
        };

        List<String> answers = new ArrayList<>();
        for (KnowledgeBase.Entry subject : subjects) {
            List<String> variants = field.apply(subject);
            answers.add(variants.isEmpty()
                    ? "I don't know that about " + subject.getName() + " yet."
                    : variants.get(random.nextInt(variants.size())));
        }
        return String.join(" ", answers);
    }
}
