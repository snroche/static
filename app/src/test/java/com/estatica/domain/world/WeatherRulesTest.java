package com.estatica.domain.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.domain.content.Balance;

import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

/** Weather: deterministic per seed and time, with every kind showing up and fronts that last. */
public class WeatherRulesTest {

    private static final long HOUR = 3_600_000L;
    private final Balance.Weather balance = TestContent.get().getBalance().getWeather();

    @Test
    public void sameMomentSameSky() {
        WeatherRules a = new WeatherRules(balance, 42);
        WeatherRules b = new WeatherRules(balance, 42);
        for (long t = 0; t < 200 * HOUR; t += HOUR) {
            assertEquals(a.at(t), b.at(t));
        }
    }

    @Test
    public void itStaysTheSameWithinABlock() {
        WeatherRules w = new WeatherRules(balance, 7);
        long block = balance.getBlockHours() * HOUR;
        for (long start = 0; start < 50 * block; start += block) {
            assertEquals(w.at(start), w.at(start + block - 1));
        }
    }

    @Test
    public void everyKindHappensAndClearIsMostCommon() {
        WeatherRules w = new WeatherRules(balance, 3);
        Map<WeatherRules.Weather, Integer> seen = new EnumMap<>(WeatherRules.Weather.class);
        long block = balance.getBlockHours() * HOUR;
        for (long i = 0; i < 3000; i++) {
            seen.merge(w.at(i * block), 1, Integer::sum);
        }
        assertEquals(WeatherRules.Weather.values().length, seen.size());
        for (WeatherRules.Weather kind : WeatherRules.Weather.values()) {
            assertTrue(kind.name(), seen.get(WeatherRules.Weather.CLEAR) >= seen.get(kind));
        }
    }

    @Test
    public void fogHidesAndTheDarkHelpsTheSneaky() {
        WeatherRules w = new WeatherRules(balance, 1);
        assertTrue(w.checkBonus(WeatherRules.Weather.FOG, false, "stealth") > 0);
        assertTrue(w.checkBonus(WeatherRules.Weather.FOG, false, "wits") < 0);
        assertTrue(w.checkBonus(WeatherRules.Weather.STORM, false, "craft") < 0);
        assertTrue(w.checkBonus(WeatherRules.Weather.CLEAR, true, "stealth") > 0);
        assertEquals(0, w.checkBonus(WeatherRules.Weather.CLEAR, false, "nerve"), 1e-9);
        assertTrue(w.noise(WeatherRules.Weather.STORM) > w.noise(WeatherRules.Weather.CLEAR));
    }
}
