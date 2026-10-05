package com.estatica.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class GameRandomTest {

    @Test
    public void sameSeedProducesSameSequence() {
        GameRandom a = GameRandom.fromSeed(42);
        GameRandom b = GameRandom.fromSeed(42);
        for (int i = 0; i < 1000; i++) {
            assertEquals(a.nextLong(), b.nextLong());
        }
    }

    @Test
    public void differentSeedsDiverge() {
        assertNotEquals(GameRandom.fromSeed(1).nextLong(), GameRandom.fromSeed(2).nextLong());
    }

    @Test
    public void restoredStateContinuesTheSameSequence() {
        GameRandom original = GameRandom.fromSeed(7);
        for (int i = 0; i < 10; i++) {
            original.nextLong();
        }
        GameRandom restored = GameRandom.fromState(original.getState());
        for (int i = 0; i < 100; i++) {
            assertEquals(original.nextInt(1000), restored.nextInt(1000));
        }
    }

    @Test
    public void nextIntStaysInBoundsAndCoversRange() {
        GameRandom random = GameRandom.fromSeed(123);
        boolean[] seen = new boolean[6];
        for (int i = 0; i < 10_000; i++) {
            int v = random.nextInt(6);
            assertTrue(v >= 0 && v < 6);
            seen[v] = true;
        }
        for (boolean s : seen) {
            assertTrue(s);
        }
    }

    @Test
    public void nextIntInclusiveIncludesBothEnds() {
        GameRandom random = GameRandom.fromSeed(5);
        boolean min = false;
        boolean max = false;
        for (int i = 0; i < 10_000; i++) {
            int v = random.nextIntInclusive(3, 5);
            assertTrue(v >= 3 && v <= 5);
            min |= v == 3;
            max |= v == 5;
        }
        assertTrue(min && max);
    }

    @Test
    public void nextDoubleIsInUnitInterval() {
        GameRandom random = GameRandom.fromSeed(9);
        for (int i = 0; i < 10_000; i++) {
            double d = random.nextDouble();
            assertTrue(d >= 0.0 && d < 1.0);
        }
    }

    @Test
    public void chanceRespectsExtremes() {
        GameRandom random = GameRandom.fromSeed(11);
        for (int i = 0; i < 1000; i++) {
            assertTrue(random.chance(1.0));
            assertTrue(!random.chance(0.0));
        }
    }

    @Test
    public void pickIsDeterministic() {
        List<String> items = List.of("a", "b", "c", "d");
        assertEquals(GameRandom.fromSeed(99).pick(items), GameRandom.fromSeed(99).pick(items));
    }

    @Test
    public void invalidArgumentsThrow() {
        GameRandom random = GameRandom.fromSeed(0);
        assertThrows(IllegalArgumentException.class, () -> random.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> random.nextIntInclusive(5, 4));
        assertThrows(IllegalArgumentException.class, () -> random.pick(List.of()));
    }
}
