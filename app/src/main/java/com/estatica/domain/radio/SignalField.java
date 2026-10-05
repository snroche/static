package com.estatica.domain.radio;

import com.estatica.core.GameRandom;
import com.estatica.domain.content.Balance;
import com.estatica.domain.content.BandDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where signals are on the dial (GDD §6.1). Positions are a pure function of the save seed,
 * the band and the current time window, so they rotate every few hours without storing
 * anything but which ones were already caught.
 */
public final class SignalField {

    public enum Kind { ZONE, SURVIVOR, TRANSMISSION, RARE }

    /** One hidden signal in a band during a window. */
    public static final class Spot {
        public final String bandId;
        public final long window;
        public final int slot;
        public final int position;
        public final Kind kind;

        Spot(String bandId, long window, int slot, int position, Kind kind) {
            this.bandId = bandId;
            this.window = window;
            this.slot = slot;
            this.position = position;
            this.kind = kind;
        }

        /** Stable key for "already caught". */
        public String key() {
            return key(bandId, window, slot);
        }

        public static String key(String bandId, long window, int slot) {
            return bandId + "@" + window + "#" + slot;
        }
    }

    /** What the dial picks up at a position. */
    public static final class Reading {
        /** 0 (nothing but static) to 1 (perfectly tuned). */
        public final double strength;
        /** The nearest uncaught signal heard, or null. */
        public final Spot spot;

        Reading(double strength, Spot spot) {
            this.strength = strength;
            this.spot = spot;
        }
    }

    private final Balance balance;
    private final long seed;

    public SignalField(Balance balance, long seed) {
        this.balance = balance;
        this.seed = seed;
    }

    public long window(long nowMillis) {
        return nowMillis / (balance.getRadio().getSignalRotationHours() * 3_600_000L);
    }

    /** When the current window ends (signals move). */
    public long windowEndsAt(long nowMillis) {
        long length = balance.getRadio().getSignalRotationHours() * 3_600_000L;
        return (window(nowMillis) + 1) * length;
    }

    public List<Spot> spots(BandDef band, long window) {
        int bandIndex = balance.bandIndex(band.getId());
        GameRandom rng = GameRandom.fromSeed(seed ^ (bandIndex + 1) * 0x9E3779B97F4A7C15L ^ window * 0xC2B2AE3D27D4EB4FL);
        List<Spot> spots = new ArrayList<>();
        for (int slot = 0; slot < band.getSignalsPerWindow(); slot++) {
            int position = rng.nextInt(balance.getRadio().getDialMax());
            spots.add(new Spot(band.getId(), window, slot, position, pickKind(band, rng)));
        }
        return spots;
    }

    /** Strength of the strongest uncaught signal near {@code position}. */
    public Reading tune(BandDef band, int position, long window, Set<String> caught) {
        Spot best = null;
        double bestStrength = 0;
        for (Spot spot : spots(band, window)) {
            if (caught.contains(spot.key())) {
                continue;
            }
            double strength = Math.max(0, 1.0 - Math.abs(position - spot.position) / (double) band.getTuneRadius());
            if (strength > bestStrength) {
                best = spot;
                bestStrength = strength;
            }
        }
        return new Reading(bestStrength, best);
    }

    /** The spot caught at {@code position}, or null if the signal isn't clear enough. */
    public Spot lock(BandDef band, int position, long window, Set<String> caught) {
        Reading reading = tune(band, position, window, caught);
        return reading.strength >= balance.getRadio().getLockThreshold() ? reading.spot : null;
    }

    private static Kind pickKind(BandDef band, GameRandom rng) {
        Map<String, Integer> weights = band.getSignalWeights();
        int total = 0;
        for (Kind kind : Kind.values()) {
            total += weights.getOrDefault(kind.name(), 0);
        }
        if (total <= 0) {
            return Kind.RARE;
        }
        int ticket = rng.nextInt(total);
        for (Kind kind : Kind.values()) {
            ticket -= weights.getOrDefault(kind.name(), 0);
            if (ticket < 0) {
                return kind;
            }
        }
        return Kind.RARE;
    }
}
