package com.estatica.domain.expedition;

import com.estatica.domain.content.Balance;
import com.estatica.domain.content.EventDef;
import com.estatica.domain.content.ZoneDef;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutRules;
import com.estatica.domain.scouts.StatType;

/** Success chance of a situation option for a scout in a zone. */
public final class CheckCalculator {

    private final Balance.Checks balance;
    private final ScoutRules rules;

    public CheckCalculator(Balance.Checks balance, ScoutRules rules) {
        this.balance = balance;
        this.rules = rules;
    }

    public double chance(Scout scout, EventDef.Option option, ZoneDef zone) {
        int stat = rules.effectiveStat(scout, StatType.fromKey(option.getStat()));
        double chance = balance.getBaseChance()
                + (stat - option.getDifficulty()) * balance.getPerPoint()
                - Math.max(0, zone.getDanger() - 1) * balance.getPerDanger()
                + rules.checkBonus(scout)
                - Math.max(0, scout.getStress() - balance.getStressPenaltyFrom())
                * balance.getStressPenaltyPerPoint();
        return Math.max(balance.getMinChance(), Math.min(balance.getMaxChance(), chance));
    }

    public OddsHint hint(double chance) {
        if (chance >= balance.getHintGood()) {
            return OddsHint.GOOD;
        }
        return chance < balance.getHintBad() ? OddsHint.BAD : OddsHint.UNSURE;
    }
}
