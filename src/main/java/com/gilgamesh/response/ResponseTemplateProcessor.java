package com.gilgamesh.response;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.model.Intent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ResponseTemplateProcessor — Manages stateful response variations and variable interpolation.
 *
 * Concepts Demonstrated:
 * 1. Non-Repeating Response Selection: Avoids picking the exact same response template consecutively.
 * 2. Dynamic Variable Substitution: Replaces placeholders like {time}, {date}, {topic} with live values.
 */
public class ResponseTemplateProcessor {

    private final Map<String, String> lastUsedResponsePerIntent = new HashMap<>();
    private final Random random = new Random();

    /**
     * Selects a response template from an intent without repeating the previously selected template.
     */
    public String selectResponse(Intent intent, ConversationContext context) {
        if (intent == null || intent.getResponses() == null || intent.getResponses().isEmpty()) {
            return null;
        }

        List<String> templates = intent.getResponses();
        String tag = intent.getTag();
        // Remembered per conversation when there is one, so users don't affect each other's variety
        String previousResponse = context != null ? context.getLastResponseFor(tag) : lastUsedResponsePerIntent.get(tag);

        String selected = templates.get(random.nextInt(templates.size()));

        if (templates.size() > 1 && selected.equals(previousResponse)) {
            // Pick an alternative template
            for (String candidate : templates) {
                if (!candidate.equals(previousResponse)) {
                    selected = candidate;
                    break;
                }
            }
        }

        if (context != null) {
            context.rememberResponse(tag, selected);
        } else {
            lastUsedResponsePerIntent.put(tag, selected);
        }
        return interpolateVariables(selected, context);
    }

    /**
     * Interpolates dynamic variables in the template string.
     */
    public String interpolateVariables(String template, ConversationContext context) {
        if (template == null) return "";

        LocalDateTime now = LocalDateTime.now();
        String timeStr = now.format(DateTimeFormatter.ofPattern("h:mm a"));
        String dateStr = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));

        String topicStr = (context != null && context.getCurrentTopic() != null) ? context.getCurrentTopic() : "general";

        String userName = context != null ? context.getUserName() : null;

        return interpolateUserName(template, userName)
            .replace("{time}", timeStr)
            .replace("{date}", dateStr)
            .replace("{topic}", topicStr);
    }

    // {user_name}, optionally with text that only appears when the name is known: "Hello{, user_name}!"
    private static final Pattern USER_NAME_PLACEHOLDER = Pattern.compile("\\{([^{}]*?)user_name([^{}]*?)\\}");

    /**
     * Fills in the user's name. "Hello{, user_name}!" becomes "Hello, Sam!" when the name
     * is known and "Hello!" when it is not, so responses read naturally either way.
     */
    static String interpolateUserName(String template, String userName) {
        return USER_NAME_PLACEHOLDER.matcher(template).replaceAll(match -> Matcher.quoteReplacement(
                userName == null ? "" : match.group(1) + userName + match.group(2)));
    }
}
