package com.estatica.domain.world;

import com.estatica.core.GameRandom;
import com.estatica.domain.content.Balance;

import java.util.Map;

/**
 * Weather outside, in blocks of a few hours, derived from the game seed and the time alone:
 * the same moment always has the same sky, so catch-up stays deterministic. A block tends to
 * look like the one before (fronts last), which keeps it from flickering.
 */
public final class WeatherRules {

    public enum Weather { CLEAR, CLOUDY, RAIN, FOG, STORM }

    private static final long HOUR = 3_600_000L;

    private final Balance.Weather balance;
    private final long seed;

    public WeatherRules(Balance.Weather balance, long seed) {
        this.balance = balance;
        this.seed = seed;
    }

    public Weather at(long millis) {
        long block = Math.floorDiv(millis, balance.getBlockHours() * HOUR);
        GameRandom rng = GameRandom.fromSeed(seed ^ (block * 0x9E3779B97F4A7C15L));
        if (rng.chance(balance.getPersistence())) {
            // Same front as the previous block.
            GameRandom previous = GameRandom.fromSeed(seed ^ ((block - 1) * 0x9E3779B97F4A7C15L));
            previous.chance(balance.getPersistence());
            return pick(previous);
        }
        return pick(rng);
    }

    private Weather pick(GameRandom rng) {
        Map<String, Integer> weights = balance.getWeights();
        int total = 0;
        for (Weather w : Weather.values()) {
            total += weights.getOrDefault(w.name(), 0);
        }
        int roll = rng.nextInt(Math.max(1, total));
        for (Weather w : Weather.values()) {
            roll -= weights.getOrDefault(w.name(), 0);
            if (roll < 0) {
                return w;
            }
        }
        return Weather.CLEAR;
    }

    /** Check bonus for a stat in this weather (and at night), e.g. fog helps stealth. */
    public double checkBonus(Weather weather, boolean night, String stat) {
        double bonus = effect(balance.getEffects().get(weather.name()), stat);
        if (night) {
            bonus += effect(balance.getNight(), stat);
        }
        return bonus;
    }

    private static double effect(Map<String, Double> effects, String stat) {
        if (effects == null) {
            return 0;
        }
        return effects.getOrDefault(stat, 0.0) + effects.getOrDefault("all", 0.0);
    }

    /** Extra static on the radio. */
    public double noise(Weather weather) {
        return balance.getNoise().getOrDefault(weather.name(), 0.0);
    }

    public double flavorChance() {
        return balance.getFlavorChance();
    }
}
