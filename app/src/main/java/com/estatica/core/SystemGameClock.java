package com.estatica.core;

/** Production {@link GameClock} backed by the device clock. */
public final class SystemGameClock implements GameClock {

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }
}
