package com.gilgamesh.nlp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SpellCheckerTest {

    @Test
    public void testLevenshteinDistance() {
        assertEquals(1, SpellChecker.computeLevenshteinDistance("wether", "weather"));
        assertEquals(0, SpellChecker.computeLevenshteinDistance("hello", "hello"));
        assertEquals(1, SpellChecker.computeLevenshteinDistance("helllo", "hello"));
        assertEquals(2, SpellChecker.computeLevenshteinDistance("hellllo", "hello"));
    }

    @Test
    public void testSpellCorrection() {
        SpellChecker spellChecker = new SpellChecker();
        assertEquals("weather", spellChecker.correctToken("wether"));
        assertEquals("hello", spellChecker.correctToken("helllo"));
        assertEquals("unknownwordxyz", spellChecker.correctToken("unknownwordxyz"));
    }
}
