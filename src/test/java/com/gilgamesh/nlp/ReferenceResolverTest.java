package com.gilgamesh.nlp;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.model.Intent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ReferenceResolverTest {

    @Test
    public void testAnaphoraResolutionWithTopic() {
        ReferenceResolver resolver = new ReferenceResolver();
        ConversationContext context = new ConversationContext();

        Intent intent = new Intent();
        intent.setTag("nlp_definition");
        context.update("what is nlp", "NLP is natural language processing", intent);

        String resolved = resolver.resolveReferences("Who created it?", context);
        assertEquals("Who created NLP?", resolved);
    }

    @Test
    public void testAnaphoraResolutionForBotInfo() {
        ReferenceResolver resolver = new ReferenceResolver();
        ConversationContext context = new ConversationContext();

        Intent intent = new Intent();
        intent.setTag("bot_info");
        context.update("tell me about yourself", "I am Gilgamesh", intent);

        String resolved = resolver.resolveReferences("Who built it?", context);
        assertEquals("Who built Gilgamesh?", resolved);
    }
}
