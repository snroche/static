package com.estatica.domain.scouts;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Hidden closeness between pairs of scouts, from −max (rivals) to +max (inseparable). It
 * decides who grieves most, whom a dying scout names, and how sharing a task feels.
 */
public final class Affinities {

    private final Map<String, Double> values = new HashMap<>();
    private final double max;

    public Affinities(double max) {
        this.max = max;
    }

    /** Canonical key for an unordered pair. */
    public static String key(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    public void put(String a, String b, double value) {
        values.put(key(a, b), value);
    }

    public double get(String a, String b) {
        Double v = values.get(key(a, b));
        return v != null ? v : 0;
    }

    public void add(String a, String b, double delta) {
        if (a.equals(b) || delta == 0) {
            return;
        }
        values.put(key(a, b), Math.max(-max, Math.min(max, get(a, b) + delta)));
    }

    /** The scout among {@code others} closest to {@code scoutId}, or null if none above 0. */
    public Scout closest(String scoutId, Collection<Scout> others) {
        Scout best = null;
        double bestValue = 0;
        for (Scout other : others) {
            if (other.getId().equals(scoutId)) {
                continue;
            }
            double value = get(scoutId, other.getId());
            if (value > bestValue) {
                best = other;
                bestValue = value;
            }
        }
        return best;
    }

    /** Pair key to value, for persistence. */
    public Map<String, Double> asMap() {
        return Collections.unmodifiableMap(values);
    }
}
