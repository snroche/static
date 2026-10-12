package com.estatica.domain.radio;

import com.estatica.domain.content.SignalsDef;

/**
 * When stations are on the air. Each scheduled station airs every few hours from a set time,
 * for a set number of minutes; the rest are always on. Times are local (the device's clock),
 * passed in already offset, so this stays pure and testable.
 */
public final class Broadcasts {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    private Broadcasts() {
    }

    /** Which airing is the latest to have started by {@code localMillis} (may be negative). */
    public static long airing(SignalsDef.Station station, long localMillis) {
        SignalsDef.Schedule s = station.getSchedule();
        if (s == null) {
            return 0;
        }
        long every = Math.round(s.getEveryHours() * HOUR);
        long offset = Math.round(s.getAtHour() * HOUR);
        return Math.floorDiv(localMillis - offset, every);
    }

    /** Local time airing {@code n} starts. */
    public static long startOf(SignalsDef.Station station, long n) {
        SignalsDef.Schedule s = station.getSchedule();
        return n * Math.round(s.getEveryHours() * HOUR) + Math.round(s.getAtHour() * HOUR);
    }

    public static boolean onAir(SignalsDef.Station station, long localMillis) {
        SignalsDef.Schedule s = station.getSchedule();
        if (s == null) {
            return true;
        }
        long start = startOf(station, airing(station, localMillis));
        return localMillis - start < s.getMinutes() * MINUTE;
    }

    /** Local time of the next airing to start after {@code localMillis}. */
    public static long nextStart(SignalsDef.Station station, long localMillis) {
        return startOf(station, airing(station, localMillis) + 1);
    }
}
