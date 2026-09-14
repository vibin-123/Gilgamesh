package com.gilgamesh.nlp;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.model.Intent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReferenceResolver — Performs Anaphora / Reference Resolution using conversation memory.
 *
 * NLP Concept: Coreference & Anaphora Resolution
 * ----------------------------------------------
 * Anaphora resolution identifies what a pronoun or demonstrative refers back to in context.
 * Example:
 *   Turn 1: "Tell me about Gilgamesh NLP" -> Topic: "Gilgamesh NLP"
 *   Turn 2: "Who created it?"             -> Resolved: "Who created Gilgamesh NLP?"
 *
 * This turns ambiguous follow-up utterances into clear, self-contained inputs for classification.
 */
public class ReferenceResolver {

    private static final Pattern PRONOUN_PATTERN = Pattern.compile(
        "\\b(it|that|this|he|she|them|there)\\b", Pattern.CASE_INSENSITIVE
    );

    /**
     * Resolves references in user text using the active conversation context.
     *
     * @param userInput Raw input from user
     * @param context Active conversation context
     * @return Resolved input string
     */
    public String resolveReferences(String userInput, ConversationContext context) {
        if (userInput == null || context == null) {
            return userInput;
        }

        String referentEntity = determineReferentEntity(context);
        if (referentEntity == null || referentEntity.isBlank() || referentEntity.equalsIgnoreCase("unknown")) {
            return userInput;
        }

        Matcher matcher = PRONOUN_PATTERN.matcher(userInput);
        if (matcher.find()) {
            String resolved = matcher.replaceAll(Matcher.quoteReplacement(referentEntity));
            System.out.println("[ReferenceResolver] Resolved anaphora: '" + userInput + "' -> '" + resolved + "'");
            return resolved;
        }

        return userInput;
    }

    private String determineReferentEntity(ConversationContext context) {
        Intent lastIntent = context.getLastDetectedIntent();
        if (lastIntent != null) {
            String tag = lastIntent.getTag();
            switch (tag) {
                case "bot_info":
                case "creator_info":
                    return "Gilgamesh";
                case "nlp_definition":
                    return "NLP";
                case "weather":
                    return "weather";
                case "datetime":
                    return "time";
                default:
                    return context.getCurrentTopic();
            }
        }
        return context.getCurrentTopic();
    }
}
