package com.estatica.domain.scouts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;

import org.junit.Test;

/** Every stat maxed: start over, higher. */
public class RetrainTest {

    private static final long T0 = 1_000_000_000L;
    private final RetrainRules rules = new RetrainRules(TestContent.get().getBalance().getRetrain());

    @Test
    public void onlyOnceEverythingIsMaxed() {
        Scout halfway = TestScouts.custom("mara", 6, 0, 0, T0); // 6 of 10
        assertFalse(rules.canRetrain(halfway));
        assertFalse(rules.retrain(halfway, StatType.WITS));
        Scout maxed = TestScouts.custom("mara", 10, 0, 0, T0);
        assertTrue(rules.canRetrain(maxed));
    }

    @Test
    public void halfTheValueAndHigherCeilings() {
        Scout s = TestScouts.custom("mara", 10, 0, 0, T0);
        assertTrue(rules.retrain(s, StatType.WITS));
        assertEquals(1, RetrainRules.tier(s));
        assertEquals(5, s.stat(StatType.ENDURANCE).getValue());
        assertEquals(11, s.stat(StatType.ENDURANCE).getCeiling());
        assertEquals(12, s.stat(StatType.WITS).getCeiling());
        assertFalse("growing again first", rules.canRetrain(s));
        assertTrue("far-off places push veterans", rules.farPenalty(s) > 0);
    }

    @Test
    public void tiersStack() {
        Scout s = TestScouts.custom("mara", 10, 0, 0, T0);
        rules.retrain(s, StatType.NERVE);
        for (StatType type : StatType.values()) {
            Stat st = s.stat(type);
            s.setStat(type, new Stat(st.getCeiling(), st.getCeiling(), 0));
        }
        assertTrue(rules.retrain(s, StatType.NERVE));
        assertEquals(2, RetrainRules.tier(s));
        assertEquals(14, s.stat(StatType.NERVE).getCeiling());
    }
}
