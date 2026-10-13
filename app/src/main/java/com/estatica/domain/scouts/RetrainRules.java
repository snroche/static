package com.estatica.domain.scouts;

import com.estatica.domain.content.Balance;

/**
 * Once every stat is at its ceiling, a person can retrain: every stat drops back (to half,
 * rounded up), every ceiling goes up by one and the one they focus on by one more. Each
 * retraining makes them a veteran one step further; far-off places push veterans harder.
 */
public final class RetrainRules {

    /** Scout flag prefix, one per retraining: "retrained:1", "retrained:2"… */
    public static final String FLAG = "retrained:";

    private final Balance.Retrain balance;

    public RetrainRules(Balance.Retrain balance) {
        this.balance = balance;
    }

    public boolean canRetrain(Scout scout) {
        if (!scout.isAlive()) {
            return false;
        }
        for (StatType type : StatType.values()) {
            if (!scout.stat(type).isMaxed()) {
                return false;
            }
        }
        return true;
    }

    /** How many times they've retrained. */
    public static int tier(Scout scout) {
        int n = 0;
        while (scout.getLife().hasFlag(FLAG + (n + 1))) {
            n++;
        }
        return n;
    }

    /** @return false if they can't yet */
    public boolean retrain(Scout scout, StatType focus) {
        if (!canRetrain(scout)) {
            return false;
        }
        for (StatType type : StatType.values()) {
            Stat stat = scout.stat(type);
            int ceiling = stat.getCeiling() + balance.getCeilingGain() + (type == focus ? balance.getFocusGain() : 0);
            int value = Math.max(1, (int) Math.ceil(stat.getValue() * balance.getKeep()));
            scout.setStat(type, new Stat(Math.min(value, ceiling), ceiling, 0));
        }
        scout.getLife().addFlag(FLAG + (tier(scout) + 1));
        return true;
    }

    /** Odds lost on far-off checks per retraining of the lead (their stats outgrew the rest). */
    public double farPenalty(Scout lead) {
        return tier(lead) * balance.getFarPenaltyPerTier();
    }
}
