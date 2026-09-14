package com.gilgamesh.response;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.model.Intent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ResponseEngineTest {

    @Test
    public void testProgressiveFallbacks() {
        ResponseEngine engine = new ResponseEngine(List.of(new StaticIntentHandler()));
        ConversationContext context = new ConversationContext();

        String fb1 = engine.generateResponse(null, context);
        String fb2 = engine.generateResponse(null, context);
        String fb3 = engine.generateResponse(null, context);

        assertNotNull(fb1);
        assertNotNull(fb2);
        assertNotNull(fb3);

        assertEquals(3, engine.getFallbackHandler().getConsecutiveFallbacks());
    }

    @Test
    public void testResponseVariationsAndInterpolation() {
        ResponseEngine engine = new ResponseEngine(List.of(new StaticIntentHandler()));
        ConversationContext context = new ConversationContext();

        Intent intent = new Intent();
        intent.setTag("time_check");
        intent.setResponses(List.of("The current time is {time}.", "Right now it's {time}."));

        String resp1 = engine.generateResponse(intent, context);
        assertTrue(resp1.contains("The current time is") || resp1.contains("Right now it's"));
        assertFalse(resp1.contains("{time}"), "Placeholder {time} should be interpolated");
    }
}
