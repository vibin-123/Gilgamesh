package com.gilgamesh.nlp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PorterStemmerTest {

    @Test
    public void testStemming() {
        PorterStemmer stemmer = new PorterStemmer();

        assertEquals("run", stemmer.stem("running"));
        assertEquals("connect", stemmer.stem("connection"));
        assertEquals("connect", stemmer.stem("connections"));
        assertEquals("weather", stemmer.stem("weather"));
    }
}
