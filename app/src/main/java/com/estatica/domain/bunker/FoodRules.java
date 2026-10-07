package com.estatica.domain.bunker;

import com.estatica.domain.content.Balance;

/**
 * The bunker eats: every living scout goes through rations, cooks stretch them, and when
 * they run out everyone at home gets hungry. Pure arithmetic over elapsed time, like the
 * rest of the catch-up; fractions carry over between steps.
 */
public final class FoodRules {

    private static final double HOURS_PER_DAY = 24.0;

    private final Balance.Food balance;

    public FoodRules(Balance.Food balance) {
        this.balance = balance;
    }

    /** What happened over an interval. */
    public static final class Meal {
        /** Whole rations taken from the stores. */
        public final int eaten;
        /** Fraction of a ration already nibbled, for next time. */
        public final double carry;
        /** Hours at the end of the interval spent with nothing to eat. */
        public final double hungryHours;

        Meal(int eaten, double carry, double hungryHours) {
            this.eaten = eaten;
            this.carry = carry;
            this.hungryHours = hungryHours;
        }
    }

    /** @param kitchenSaving sum of the cooks' savings (tasks.json foodSaving), before the cap */
    public double perHour(int mouths, double kitchenSaving) {
        double saving = Math.min(balance.getKitchenMaxSaving(), Math.max(0, kitchenSaving));
        return mouths * balance.getRationsPerScoutPerDay() / HOURS_PER_DAY * (1 - saving);
    }

    public Meal eat(int mouths, double kitchenSaving, int rations, double carry, double hours) {
        double rate = perHour(mouths, kitchenSaving);
        if (rate <= 0 || hours <= 0) {
            return new Meal(0, carry, 0);
        }
        double need = carry + rate * hours;
        int whole = (int) Math.floor(need);
        if (whole <= rations) {
            return new Meal(whole, need - whole, 0);
        }
        // Ran out: hungry for whatever the stores couldn't cover. No debt carries over.
        double deficit = need - rations;
        return new Meal(rations, 0, Math.min(hours, deficit / rate));
    }

    /** How long the stores last at the current rate, in days (infinite with nobody eating). */
    public double daysLeft(int mouths, double kitchenSaving, int rations, double carry) {
        double rate = perHour(mouths, kitchenSaving);
        return rate <= 0 ? Double.POSITIVE_INFINITY : Math.max(0, rations - carry) / rate / HOURS_PER_DAY;
    }

    public boolean isLow(double daysLeft) {
        return daysLeft < balance.getLowFoodDays();
    }

    public int hungerStress(double hungryHours) {
        return (int) Math.round(balance.getHungerStressPerHour() * hungryHours);
    }

    public double hungerHealMultiplier() {
        return balance.getHungerHealMultiplier();
    }
}
