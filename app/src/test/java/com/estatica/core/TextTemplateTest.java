package com.estatica.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Map;

public class TextTemplateTest {

    @Test
    public void replacesKnownPlaceholders() {
        assertEquals("Pasaron 3 h desde v0.1",
                TextTemplate.format("Pasaron {elapsed} desde v{version}",
                        Map.of("elapsed", "3 h", "version", "0.1")));
    }

    @Test
    public void leavesUnknownPlaceholdersAndBracesAlone() {
        assertEquals("hola {nadie} y { suelto",
                TextTemplate.format("hola {nadie} y { suelto", Map.of()));
    }

    @Test
    public void templateWithoutPlaceholdersIsUnchanged() {
        assertEquals("sin novedad", TextTemplate.format("sin novedad", Map.of("x", "y")));
    }
}
