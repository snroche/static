package com.estatica.domain.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.estatica.core.GameRandom;

import org.junit.Test;

public class SignalDegraderTest {

    private static final String TEXT = "no hay salida por el sótano, volvé al búnker";

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
    public void fullIntensityCutsEveryLongWord() {
        String out = SignalDegrader.degrade(TEXT, 1.0, GameRandom.fromSeed(3));
        assertNotEquals(TEXT, out);
        assertTrue(out.contains("—"));
    }
}
