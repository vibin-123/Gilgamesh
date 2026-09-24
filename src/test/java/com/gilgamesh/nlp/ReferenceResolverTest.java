package com.gilgamesh.nlp;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.Entity;
import com.gilgamesh.memory.Entity.EntityType;
import com.gilgamesh.model.Intent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReferenceResolverTest {

    private ReferenceResolver resolver;
    private ConversationContext context;

    @BeforeEach
    public void setUp() {
        resolver = new ReferenceResolver();
        context = new ConversationContext();
    }

    @Test
    public void testAnaphoraResolutionWithTopic() {
        Intent intent = new Intent();
        intent.setTag("nlp_definition");
        context.update("what is nlp", "NLP is natural language processing", intent);

        String resolved = resolver.resolveReferences("Who created it?", context);
        assertEquals("Who created NLP?", resolved);
    }

    @Test
    public void testAnaphoraResolutionForBotInfo() {
        Intent intent = new Intent();
        intent.setTag("bot_info");
        context.update("tell me about yourself", "I am Gilgamesh", intent);

        String resolved = resolver.resolveReferences("Who built it?", context);
        assertEquals("Who built Gilgamesh?", resolved);
    }

    @Test
    public void testPronounItResolution() {
        Intent intent = new Intent();
        intent.setTag("about_technology");
        Entity javaEntity = new Entity("Java", EntityType.LANGUAGE);
        context.update("what is java", "Java is a programming language", intent, List.of(javaEntity));

        String resolved = resolver.resolveReferences("Who created it?", context);
        assertEquals("Who created Java?", resolved);
    }

    @Test
    public void testPossessiveItsResolution() {
        Intent intent = new Intent();
        intent.setTag("about_python");
        Entity pythonEntity = new Entity("Python", EntityType.LANGUAGE);
        context.update("tell me about python", "Python is a language", intent, List.of(pythonEntity));

        String resolved = resolver.resolveReferences("What are its advantages?", context);
        assertEquals("What are Python's advantages?", resolved);
    }

    @Test
    public void testPossessivePhraseFeatures() {
        Intent intent = new Intent();
        intent.setTag("about_python");
        Entity pythonEntity = new Entity("Python", EntityType.LANGUAGE);
        context.update("tell me about python", "Python is a language", intent, List.of(pythonEntity));

        String resolved = resolver.resolveReferences("Tell me its features", context);
        assertEquals("Tell me Python's features", resolved);
    }

    @Test
    public void testPluralPronounsWithMultipleEntities() {
        Intent intent = new Intent();
        intent.setTag("about_technology");
        Entity java = new Entity("Java", EntityType.LANGUAGE);
        Entity python = new Entity("Python", EntityType.LANGUAGE);
        context.update("tell me about java and python", "Both are languages", intent, List.of(java, python));

        String resolved = resolver.resolveReferences("What are they used for?", context);
        assertEquals("What are Java and Python used for?", resolved);
    }

    @Test
    public void testPluralPronounsThem() {
        Intent intent = new Intent();
        intent.setTag("about_technology");
        Entity java = new Entity("Java", EntityType.LANGUAGE);
        Entity python = new Entity("Python", EntityType.LANGUAGE);
        context.update("tell me about java and python", "Both are languages", intent, List.of(java, python));

        String resolved = resolver.resolveReferences("Explain them", context);
        assertEquals("Explain Java and Python", resolved);
    }

    @Test
    public void testDemonstrativesThisAndThat() {
        Intent intent = new Intent();
        intent.setTag("about_technology");
        Entity java = new Entity("Java", EntityType.LANGUAGE);
        context.update("what is java", "Java is a language", intent, List.of(java));

        String resolvedThis = resolver.resolveReferences("Tell me more about this", context);
        assertEquals("Tell me more about Java", resolvedThis);

        String resolvedThat = resolver.resolveReferences("Explain that", context);
        assertEquals("Explain Java", resolvedThat);
    }

    @Test
    public void testContextualNounPhraseTheLanguage() {
        Intent intent = new Intent();
        intent.setTag("about_technology");
        Entity java = new Entity("Java", EntityType.LANGUAGE);
        context.update("what is java", "Java is a language", intent, List.of(java));

        String resolved = resolver.resolveReferences("Who created the language?", context);
        assertEquals("Who created Java?", resolved);
    }

    @Test
    public void testContextualNounPhraseTheProjectAndChatbot() {
        Intent intent = new Intent();
        intent.setTag("about_technology");
        context.update("what is java", "Java is a language", intent);

        String resolvedChatbot = resolver.resolveReferences("Who built the chatbot?", context);
        assertEquals("Who built Gilgamesh?", resolvedChatbot);

        String resolvedProject = resolver.resolveReferences("Tell me about the project", context);
        assertEquals("Tell me about Gilgamesh", resolvedProject);
    }

    @Test
    public void testContextualNounPhraseTheCreator() {
        Intent intent = new Intent();
        intent.setTag("java_creator");
        Entity gosling = new Entity("James Gosling", EntityType.PERSON);
        context.update("who created java", "James Gosling created Java", intent, List.of(gosling));

        String resolved = resolver.resolveReferences("Tell me about the creator", context);
        assertEquals("Tell me about James Gosling", resolved);
    }

    @Test
    public void testUnresolvableReferenceWithEmptyContext() {
        String input = "Who created it?";
        String resolved = resolver.resolveReferences(input, context);
        assertEquals(input, resolved);
    }

    @Test
    public void testUnresolvableReferenceWithIncompatibleType() {
        Intent intent = new Intent();
        intent.setTag("creator_info");
        Entity person = new Entity("James Gosling", EntityType.PERSON);
        context.update("who is james gosling", "He is a computer scientist", intent, List.of(person));

        // "the language" has no matching LANGUAGE entity in context
        String input = "Tell me about the language";
        String resolved = resolver.resolveReferences(input, context);
        assertEquals(input, resolved);
    }

    @Test
    public void testAmbiguousReferencePreserved() {
        Intent intent = new Intent();
        intent.setTag("about_technology");
        Entity java = new Entity("Java", EntityType.LANGUAGE);
        Entity python = new Entity("Python", EntityType.LANGUAGE);
        context.update("tell me about java and python", "Both are popular languages", intent, List.of(java, python));

        // When multiple entities of the same type exist in the turn, "its" is ambiguous
        String input = "What are its advantages?";
        String resolved = resolver.resolveReferences(input, context);
        assertEquals(input, resolved, "Ambiguous reference must not be falsely bound");
    }

    @Test
    public void testTopicSwitchingSequence() {
        // Turn 1: Java
        Intent javaIntent = new Intent();
        javaIntent.setTag("about_technology");
        Entity java = new Entity("Java", EntityType.LANGUAGE);
        context.update("what is java", "Java is a programming language", javaIntent, List.of(java));

        // Turn 2: Follow-up on Java
        String turn2 = resolver.resolveReferences("Who created it?", context);
        assertEquals("Who created Java?", turn2);
        Entity gosling = new Entity("James Gosling", EntityType.PERSON);
        Intent creatorIntent = new Intent();
        creatorIntent.setTag("java_creator");
        context.update("who created it", "James Gosling created Java", creatorIntent, List.of(gosling));

        // Turn 3: Topic change to Python
        Intent pythonIntent = new Intent();
        pythonIntent.setTag("about_python");
        Entity python = new Entity("Python", EntityType.LANGUAGE);
        context.update("tell me about python", "Python is a programming language", pythonIntent, List.of(python));

        // Turn 4: Follow-up "its advantages" -> MUST resolve to Python, not Java
        String turn4 = resolver.resolveReferences("What are its advantages?", context);
        assertEquals("What are Python's advantages?", turn4);
    }

    @Test
    public void testExpletiveItPreserved() {
        Intent intent = new Intent();
        intent.setTag("about_technology");
        Entity java = new Entity("Java", EntityType.LANGUAGE);
        context.update("what is java", "Java is a programming language", intent, List.of(java));

        // Expletive "it" questions must not resolve to "Java"
        assertEquals("What time is it?", resolver.resolveReferences("What time is it?", context));
        assertEquals("how's it going", resolver.resolveReferences("how's it going", context));
        assertEquals("is it going to rain", resolver.resolveReferences("is it going to rain", context));
    }
}
