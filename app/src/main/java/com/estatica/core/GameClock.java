package com.estatica.core;

/**
 * Single source of time for the game. Every timer is a timestamp compared against
 * {@link #nowMillis()}, so catch-up can be tested by injecting a fake clock.
 */
public interface GameClock {

    /** Wall-clock time in epoch milliseconds. */
    long nowMillis();

    /** Days since the epoch in the device time zone: daily goals reset at local midnight. */
    default long localEpochDay(long millis) {
        long offset = java.util.TimeZone.getDefault().getOffset(millis);
        return Math.floorDiv(millis + offset, 24L * 3_600_000L);
    }

    /** Hour of day (0–23) on the device for an instant: night raises power use (GDD §6.4). */
    default int localHour(long millis) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(java.util.Calendar.HOUR_OF_DAY);
    }
}
