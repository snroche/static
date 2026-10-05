package com.estatica.core;

import java.security.SecureRandom;
import java.util.List;

/**
 * Deterministic random source (SplitMix64). Its whole state is a single {@code long},
 * so it can be persisted and restored to get reproducible results.
 *
 * <p>Not thread-safe: use it only from the game executor.
 */
public final class GameRandom {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    private GameRandom(long state) {
        this.state = state;
    }

    public static GameRandom fromSeed(long seed) {
        return new GameRandom(seed);
    }

    /** Restores a generator from a value previously returned by {@link #getState()}. */
    public static GameRandom fromState(long state) {
        return new GameRandom(state);
    }

    /** Fresh, non-deterministic seed. Only for creating a new game. */
    public static long newSeed() {
        return new SecureRandom().nextLong();
    }

    public long getState() {
        return state;
    }

    public long nextLong() {
        long z = (state += GOLDEN_GAMMA);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform int in {@code [0, bound)}, without modulo bias. */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        int bits;
        int val;
        do {
            bits = (int) (nextLong() >>> 33);
            val = bits % bound;
        } while (bits - val + (bound - 1) < 0);
        return val;
    }

    /** Uniform int in {@code [min, max]}. */
    public int nextIntInclusive(int min, int max) {
        if (max < min) {
            throw new IllegalArgumentException("max < min: " + min + ".." + max);
        }
        return min + nextInt(max - min + 1);
    }

    /** Uniform double in {@code [0, 1)}. */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** True with probability {@code p}. */
    public boolean chance(double p) {
        return nextDouble() < p;
    }

    public <T> T pick(List<T> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("cannot pick from an empty list");
        }
        return items.get(nextInt(items.size()));
    }

    /** Seed for an independent child generator (e.g. one per expedition timeline). */
    public long deriveSeed() {
        return nextLong();
    }
}
