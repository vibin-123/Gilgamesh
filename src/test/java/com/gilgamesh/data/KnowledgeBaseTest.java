package com.gilgamesh.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeBaseTest {

    private final KnowledgeBase kb = KnowledgeBase.loadDefault();

    private List<String> names(String text) {
        return kb.findEntries(text).stream().map(KnowledgeBase.Entry::getName).toList();
    }

    @Test
    void everyEntryHasAllThreeAnswers() {
        assertTrue(kb.getEntries().size() >= 10);
        for (KnowledgeBase.Entry entry : kb.getEntries()) {
            assertFalse(entry.getAbout().isEmpty(), entry.getName() + " has no 'about'");
            assertFalse(entry.getCreatorFact().isEmpty(), entry.getName() + " has no 'creatorFact'");
            assertFalse(entry.getAdvantages().isEmpty(), entry.getName() + " has no 'advantages'");
        }
    }

    @Test
    void findsByNameAndAliasIgnoringCase() {
        assertEquals("Java", kb.find("java").getName());
        assertEquals("Go", kb.find("golang").getName());
        assertEquals("C#", kb.find("csharp").getName());
        assertNull(kb.find("Klingon"));
        assertNull(kb.find(null));
    }

    @Test
    void findsMentionsInReadingOrder() {
        assertEquals(List.of("Java", "Python"), names("compare java and Python"));
        assertEquals(List.of("Rust"), names("who created Rust?"));
        assertTrue(names("tell me a joke").isEmpty());
    }

    @Test
    void doesNotMatchInsideLongerNames() {
        assertEquals(List.of("JavaScript"), names("what is JavaScript"));
        assertEquals(List.of("C++"), names("tell me about C++"));
        assertEquals(List.of("C#"), names("tell me about C#"));
        assertEquals(List.of("C"), names("tell me about C"));
    }

    @Test
    void everydayWordsAreNotMistakenForLanguages() {
        assertTrue(names("I have to go now").isEmpty());
        assertTrue(names("Go away").isEmpty(), "sentence-initial 'Go' is the verb");
        assertEquals(List.of("Go"), names("tell me about Go"));
        assertEquals(List.of("Go"), names("what is golang"));
        assertTrue(names("vitamin c is healthy").isEmpty());
    }

    @Test
    void masksEntitiesWithPlaceholder() {
        assertEquals("who created {entity}?", kb.maskEntities("who created Rust?"));
        assertEquals("compare {entity} and {entity}", kb.maskEntities("compare Java and C++"));
        assertEquals("tell me a joke", kb.maskEntities("tell me a joke"));
    }

    @Test
    void missingResourceFailsClearly() {
        assertThrows(RuntimeException.class, () -> new KnowledgeBase("/no-such-knowledge.json"));
    }
}
