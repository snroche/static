package com.estatica.domain.expedition;

import java.util.Objects;

/**
 * Why an expedition goes out and under what conditions: purpose, signal quality at the
 * moment of dispatch, and intel the operator decoded about the zone.
 */
public final class Mission {

    public enum Type {
        /** Scavenge. */
        EXPLORE,
        /** Bring a survivor heard on the dial back to the bunker. */
        RESCUE,
        /** Carry a relay kit and install it. */
        INSTALL_RELAY,
        /** Repair the relay in the zone. */
        MAINTAIN_RELAY
    }

    private final Type type;
    /** RESCUE: survivor template id. Null otherwise. */
    private final String target;
    /** Report noise (0 = clean). */
    private final double noise;
    /** How long an order takes to reach the scout. */
    private final long orderDelayMillis;
    /** Decoded intel: a hidden stash (extra loot on return). */
    private final boolean cacheIntel;
    /** Decoded intel: known dangers (better odds). */
    private final boolean dangerIntel;

    public Mission(Type type, String target, double noise, long orderDelayMillis,
                   boolean cacheIntel, boolean dangerIntel) {
        this.type = Objects.requireNonNull(type);
        this.target = target;
        this.noise = noise;
        this.orderDelayMillis = orderDelayMillis;
        this.cacheIntel = cacheIntel;
        this.dangerIntel = dangerIntel;
    }

    /** A plain scavenging run with a clean signal. */
    public static Mission explore() {
        return new Mission(Type.EXPLORE, null, 0, 0, false, false);
    }

    public Type getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public double getNoise() {
        return noise;
    }

    public long getOrderDelayMillis() {
        return orderDelayMillis;
    }

    public boolean hasCacheIntel() {
        return cacheIntel;
    }

    public boolean hasDangerIntel() {
        return dangerIntel;
    }
}
