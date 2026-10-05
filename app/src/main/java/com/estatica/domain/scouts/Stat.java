package com.estatica.domain.scouts;

/** One stat of one scout: current value, personal ceiling and XP toward the next point. */
public final class Stat {

    private int value;
    private final int ceiling;
    private int xp;

    public Stat(int value, int ceiling, int xp) {
        if (ceiling < value) {
            throw new IllegalArgumentException("ceiling " + ceiling + " < value " + value);
        }
        this.value = value;
        this.ceiling = ceiling;
        this.xp = xp;
    }

    public int getValue() {
        return value;
    }

    public int getCeiling() {
        return ceiling;
    }

    public int getXp() {
        return xp;
    }

    public boolean isMaxed() {
        return value >= ceiling;
    }

    /**
     * Adds XP; levels up when it reaches {@code xpPerLevel × value}.
     *
     * @return true if the stat went up
     */
    boolean gainXp(int amount, int xpPerLevel) {
        if (isMaxed() || amount <= 0) {
            return false;
        }
        xp += amount;
        int needed = xpPerLevel * Math.max(1, value);
        if (xp >= needed) {
            xp -= needed;
            value++;
            if (isMaxed()) {
                xp = 0;
            }
            return true;
        }
        return false;
    }
}
