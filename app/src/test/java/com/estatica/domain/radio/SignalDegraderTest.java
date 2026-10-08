package com.estatica.domain.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.estatica.core.GameRandom;

import org.junit.Test;

public class SignalDegraderTest {

    private static final String TEXT = "there is no way out through the basement, come back to the bunker";

    @Test
    public void zeroIntensityIsClean() {
        assertEquals(TEXT, SignalDegrader.degrade(TEXT, 0, GameRandom.fromSeed(1)));
    }

    @Test
    public void deterministicForSameSeed() {
        assertEquals(SignalDegrader.degrade(TEXT, 0.5, GameRandom.fromSeed(7)),
                SignalDegrader.degrade(TEXT, 0.5, GameRandom.fromSeed(7)));
    }

    @Test
    public void staticReplacesLettersButKeepsTheWordsShape() {
        String out = SignalDegrader.degrade(TEXT, 1.0, GameRandom.fromSeed(3));
        assertNotEquals(TEXT, out);
        assertEquals("same length: you can still piece it together", TEXT.length(), out.length());
        String[] before = TEXT.split(" ");
        String[] after = out.split(" ");
        assertEquals(before.length, after.length);
        for (int i = 0; i < before.length; i++) {
            assertEquals("first letter survives", before[i].charAt(0), after[i].charAt(0));
            if (before[i].length() > 2) {
                assertTrue(after[i], after[i].contains("·") || after[i].contains("-"));
            }
        }
    }
}
