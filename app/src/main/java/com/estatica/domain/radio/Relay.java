package com.estatica.domain.radio;

/**
 * A relay installed in a zone (GDD §4.4): improves signal there and extends reach. It wears
 * down over time and needs maintenance.
 */
public final class Relay {

    private static final double DAY = 24 * 3_600_000.0;

    private final String zoneId;
    private double condition;
    private long lastDecayAtMillis;

    public Relay(String zoneId, double condition, long lastDecayAtMillis) {
        this.zoneId = zoneId;
        this.condition = condition;
        this.lastDecayAtMillis = lastDecayAtMillis;
    }

    public static Relay installed(String zoneId, long now) {
        return new Relay(zoneId, 100, now);
    }

    public String getZoneId() {
        return zoneId;
    }

    public double getCondition() {
        return condition;
    }

    public long getLastDecayAtMillis() {
        return lastDecayAtMillis;
    }

    public boolean isWorking(double minCondition) {
        return condition >= minCondition;
    }

    /**
     * Wears the relay down by elapsed time.
     *
     * @return true if it just stopped working
     */
    public boolean decay(long now, double perDay, double minCondition) {
        boolean wasWorking = isWorking(minCondition);
        double days = Math.max(0, now - lastDecayAtMillis) / DAY;
        condition = Math.max(0, condition - days * perDay);
        lastDecayAtMillis = Math.max(now, lastDecayAtMillis);
        return wasWorking && !isWorking(minCondition);
    }

    public void repair(long now) {
        condition = 100;
        lastDecayAtMillis = now;
    }
}
