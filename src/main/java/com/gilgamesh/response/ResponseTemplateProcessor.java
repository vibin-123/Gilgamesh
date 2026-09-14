package com.gilgamesh.response;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.model.Intent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
        String previousResponse = lastUsedResponsePerIntent.get(tag);

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

        lastUsedResponsePerIntent.put(tag, selected);
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

        return template
            .replace("{time}", timeStr)
            .replace("{date}", dateStr)
            .replace("{topic}", topicStr);
    }
}
