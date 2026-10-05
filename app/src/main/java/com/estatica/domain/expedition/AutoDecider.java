package com.estatica.domain.expedition;

import com.estatica.core.GameRandom;
import com.estatica.domain.content.EventDef;
import com.estatica.domain.content.TraitDef;
import com.estatica.domain.content.ZoneDef;
import com.estatica.domain.scouts.Scout;

import java.util.List;

/** How a scout picks an option on their own (no answer in time, or ignoring an order). */
public final class AutoDecider {

    private static final double BIAS_WEIGHT = 0.2;
    private static final double JITTER = 0.05;

    private final CheckCalculator checks;

    public AutoDecider(CheckCalculator checks) {
        this.checks = checks;
    }

    public int pick(EventDef event, Scout scout, ZoneDef zone, TraitDef.Bias bias, GameRandom rng) {
        List<EventDef.Option> options = event.getOptions();
        int best = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < options.size(); i++) {
            EventDef.Option option = options.get(i);
            double score = checks.chance(scout, option, zone) + rng.nextDouble() * JITTER;
            boolean risky = option.getRisk() == EventDef.Option.Risk.RISKY;
            if ((bias == TraitDef.Bias.RISKY && risky) || (bias == TraitDef.Bias.CAUTIOUS && !risky)) {
                score += BIAS_WEIGHT;
            }
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }
}
