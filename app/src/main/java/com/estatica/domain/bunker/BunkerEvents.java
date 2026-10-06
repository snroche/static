package com.estatica.domain.bunker;

import com.estatica.core.GameRandom;
import com.estatica.domain.content.EnergyDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Blackouts and raids happen on a schedule derived from the save seed and the day, so they
 * are reproducible and need no background simulation: catch-up asks what happened between
 * two instants.
 */
public final class BunkerEvents {

    public static final long DAY = 24L * 3_600_000L;
    private static final long HOUR = 3_600_000L;
    private static final long BLACKOUT_SALT = 0x5DEECE66DL;
    private static final long RAID_SALT = 0x2545F4914F6CDD1DL;

    private final EnergyDef energy;
    private final long seed;

    public BunkerEvents(EnergyDef energy, long seed) {
        this.energy = energy;
        this.seed = seed;
    }

    public static final class Blackout {
        public final long start;
        public final long end;
        public final int watts;

        Blackout(long start, long end, int watts) {
            this.start = start;
            this.end = end;
            this.watts = watts;
        }

        public boolean covers(long t) {
            return t >= start && t < end;
        }
    }

    /** The blackout scheduled on a day, or null. */
    public Blackout blackoutOn(long day) {
        GameRandom rng = GameRandom.fromSeed(seed ^ BLACKOUT_SALT ^ day * 0x9E3779B97F4A7C15L);
        if (!rng.chance(energy.getBlackoutChancePerDay())) {
            return null;
        }
        long start = day * DAY + rng.nextInt(24) * HOUR + rng.nextInt(60) * 60_000L;
        int hours = rng.nextIntInclusive(energy.getBlackoutMinHours(), energy.getBlackoutMaxHours());
        return new Blackout(start, start + hours * HOUR, energy.getBlackoutWatts());
    }

    /** Watts lost to a blackout at {@code t} (0 if none). */
    public int blackoutWattsAt(long t) {
        long day = Math.floorDiv(t, DAY);
        for (long d = day - 1; d <= day; d++) {
            Blackout b = blackoutOn(d);
            if (b != null && b.covers(t)) {
                return b.watts;
            }
        }
        return 0;
    }

    /** Blackouts that started in {@code (from, to]}. */
    public List<Blackout> blackoutsStarting(long from, long to) {
        List<Blackout> out = new ArrayList<>();
        for (long d = Math.floorDiv(from, DAY); d <= Math.floorDiv(to, DAY); d++) {
            Blackout b = blackoutOn(d);
            if (b != null && b.start > from && b.start <= to) {
                out.add(b);
            }
        }
        return out;
    }

    /** The raid scheduled on a day (its instant), or -1. */
    public long raidOn(long day) {
        GameRandom rng = GameRandom.fromSeed(seed ^ RAID_SALT ^ day * 0xC2B2AE3D27D4EB4FL);
        if (!rng.chance(energy.getRaids().getChancePerDay())) {
            return -1;
        }
        return day * DAY + rng.nextInt(24) * HOUR + rng.nextInt(60) * 60_000L;
    }

    /** Raid instants in {@code (from, to]}, in order. */
    public List<Long> raidsBetween(long from, long to) {
        List<Long> out = new ArrayList<>();
        for (long d = Math.floorDiv(from, DAY); d <= Math.floorDiv(to, DAY); d++) {
            long t = raidOn(d);
            if (t > from && t <= to) {
                out.add(t);
            }
        }
        return out;
    }

    /** Next raid or blackout start after {@code t} within a week, or -1 (for wake-ups). */
    public long nextEventAfter(long t) {
        long best = -1;
        for (long d = Math.floorDiv(t, DAY); d <= Math.floorDiv(t, DAY) + 7; d++) {
            long raid = raidOn(d);
            Blackout b = blackoutOn(d);
            for (long candidate : new long[]{raid, b != null ? b.start : -1, b != null ? b.end : -1}) {
                if (candidate > t && (best < 0 || candidate < best)) {
                    best = candidate;
                }
            }
        }
        return best;
    }
}
