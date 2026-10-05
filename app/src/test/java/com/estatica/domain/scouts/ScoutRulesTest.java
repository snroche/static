package com.estatica.domain.scouts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.domain.content.GameContent;

import org.junit.Before;
import org.junit.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ScoutRulesTest {

    private static final long HOUR = 3_600_000L;

    private GameContent content;
    private ScoutRules rules;

    @Before
    public void setUp() {
        content = TestContent.get();
        rules = new ScoutRules(content);
    }

    /** A scout with every stat at {@code value} (ceiling 10) and the given traits. */
    static Scout scout(int value, List<ScoutTrait> traits) {
        Map<StatType, Stat> stats = new EnumMap<>(StatType.class);
        for (StatType type : StatType.values()) {
            stats.put(type, new Stat(value, 10, 0));
        }
        return new Scout("id", null, "Test", "formal", stats, traits, 10, 50,
                Injury.NONE, ScoutStatus.IDLE, 0, 0);
    }

    @Test
    public void startersAreBuiltFromTemplates() {
        Scout mara = ScoutFactory.fromTemplate(content.getStarters().get(0),
                content.getBalance().getScouts(), 0);
        assertEquals("Mara", mara.getName());
        assertEquals(5, mara.stat(StatType.STEALTH).getValue());
        assertEquals(9, mara.stat(StatType.STEALTH).getCeiling());
        assertTrue(mara.getTraits().stream().anyMatch(t -> t.isHidden() && !t.isVisible()));
    }

    @Test
    public void effectiveStatAppliesInjuryAndTraitBonus() {
        Scout s = scout(5, List.of(new ScoutTrait("metodo", false, false)));
        assertEquals(6, rules.effectiveStat(s, StatType.WITS));
        rules.setInjury(s, Injury.SERIOUS);
        assertEquals(4, rules.effectiveStat(s, StatType.WITS));
        assertEquals(3, rules.effectiveStat(s, StatType.ENDURANCE));
    }

    @Test
    public void hiddenTraitsStillApply() {
        Scout s = scout(5, List.of(new ScoutTrait("lealtad", true, false)));
        assertEquals(6, rules.effectiveStat(s, StatType.NERVE));
    }

    @Test
    public void stressIsScaledByTraitsAndNerveAndClamped() {
        Scout calm = scout(0, List.of());
        Scout fearful = scout(0, List.of(new ScoutTrait("miedo", false, false)));
        rules.addStress(calm, 10);
        rules.addStress(fearful, 10);
        assertEquals(20, calm.getStress());
        assertEquals(24, fearful.getStress());

        Scout nervy = scout(10, List.of());
        rules.addStress(nervy, 10);
        assertEquals(15, nervy.getStress()); // 10 × (1 − 10 × 0.05)

        rules.addStress(calm, 1000);
        assertEquals(100, calm.getStress());
        rules.addStress(calm, -1000);
        assertEquals(0, calm.getStress());
    }

    @Test
    public void statsGrowWithUseUpToCeiling() {
        Scout s = scout(9, List.of());
        // xpPerLevel 2 × value 9 = 18 XP; success gives 3 → 6 successes.
        for (int i = 0; i < 5; i++) {
            assertFalse(rules.train(s, StatType.STEALTH, true));
        }
        assertTrue(rules.train(s, StatType.STEALTH, true));
        assertEquals(10, s.stat(StatType.STEALTH).getValue());
        for (int i = 0; i < 50; i++) {
            assertFalse(rules.train(s, StatType.STEALTH, true));
        }
        assertEquals(10, s.stat(StatType.STEALTH).getValue());
    }

    @Test
    public void idleRecoveryUsesElapsedTimeOnly() {
        Scout s = scout(5, List.of());
        rules.addStress(s, 40); // 10 + 30 (Nerve 5 → ×0.75)
        assertEquals(40, s.getStress());
        rules.setInjury(s, Injury.SERIOUS);

        rules.recoverIdle(s, 2 * HOUR);
        assertEquals(20, s.getStress());
        assertEquals(Injury.SERIOUS, s.getInjury());

        rules.recoverIdle(s, 4 * HOUR); // 4 h total healing → one level
        assertEquals(Injury.LIGHT, s.getInjury());
        assertEquals(0, s.getStress());

        rules.recoverIdle(s, 100 * HOUR);
        assertEquals(Injury.NONE, s.getInjury());
    }

    @Test
    public void noRecoveryWhileAway() {
        Scout s = scout(5, List.of());
        rules.markDeparted(s, 0);
        rules.addStress(s, 40);
        rules.recoverIdle(s, 10 * HOUR);
        assertEquals(40, s.getStress());
        rules.markReturned(s, 10 * HOUR);
        rules.recoverIdle(s, 11 * HOUR);
        assertEquals(30, s.getStress());
    }

    @Test
    public void warningRequiresSeriousInjuryOrHighStress() {
        Scout s = scout(0, List.of());
        assertFalse(rules.isWarned(s));
        rules.setInjury(s, Injury.LIGHT);
        assertFalse(rules.isWarned(s));
        rules.setInjury(s, Injury.SERIOUS);
        assertTrue(rules.isWarned(s));
        rules.setInjury(s, Injury.NONE);
        rules.addStress(s, 60);
        assertTrue(rules.isWarned(s));
    }

    @Test
    public void injuriesStack() {
        assertEquals(Injury.LIGHT, Injury.NONE.combine(Injury.LIGHT));
        assertEquals(Injury.SERIOUS, Injury.LIGHT.combine(Injury.LIGHT));
        assertEquals(Injury.CRITICAL, Injury.LIGHT.combine(Injury.SERIOUS));
        assertEquals(Injury.CRITICAL, Injury.CRITICAL.combine(Injury.LIGHT));
        assertEquals(Injury.SERIOUS, Injury.SERIOUS.combine(Injury.NONE));
    }
}
