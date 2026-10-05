package com.estatica.core;

/**
 * Single source of time for the game. Every timer is a timestamp compared against
 * {@link #nowMillis()}, so catch-up can be tested by injecting a fake clock.
 */
public interface GameClock {

    /** Wall-clock time in epoch milliseconds. */
    long nowMillis();
}
