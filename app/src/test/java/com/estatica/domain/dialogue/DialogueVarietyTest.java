package com.estatica.domain.dialogue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.core.GameRandom;
import com.estatica.domain.content.DialogueDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;
import com.estatica.domain.scouts.DialogueOutcomes;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutRules;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Trust, stress and bond gates, and the everyday pool not repeating itself. */
public class DialogueVarietyTest {

    private static final long T0 = 1_000_000_000L;

    private GameContent content;
    private ScoutRules rules;
    private DialogueEngine engine;

    @Before
    public void setUp() {
        content = TestContent.get();
        rules = new ScoutRules(content);
        engine = new DialogueEngine(content, rules);
    }

    @Test
    public void trustGatesWhatTheyAsk() {
        DialogueDef doubt = content.dialogue("trust_why_there");
        DialogueDef faith = content.dialogue("trust_anywhere");
        Scout s = TestScouts.custom("mara", 5, 10, 0, T0);
        rules.addTrust(s, 20 - s.getTrust());
        assertTrue(engine.isEligible(s, doubt, List.of(s), T0));
        assertFalse(engine.isEligible(s, faith, List.of(s), T0));
        rules.addTrust(s, 90 - s.getTrust());
        assertFalse(engine.isEligible(s, doubt, List.of(s), T0));
        assertTrue(engine.isEligible(s, faith, List.of(s), T0));
    }

    @Test
    public void stressGatesTheShakes() {
        DialogueDef shaking = content.dialogue("stress_shaking");
        Scout calm = TestScouts.custom("mara", 5, 10, 0, T0);
        Scout scared = TestScouts.custom("mara", 5, 80, 0, T0);
        assertFalse(engine.isEligible(calm, shaking, List.of(calm), T0));
        assertTrue(engine.isEligible(scared, shaking, List.of(scared), T0));
    }

    @Test
    public void strangersTalkLikeStrangers() {
        DialogueDef quiet = content.dialogue("stranger_quiet");
        Scout stranger = TestScouts.custom("mara", 5, 10, 0, T0);
        Scout close = TestScouts.custom("mara", 5, 10, 90, T0);
        assertTrue(engine.isEligible(stranger, quiet, List.of(stranger), T0));
        assertFalse(engine.isEligible(close, quiet, List.of(close), T0));
    }

    @Test
    public void theEverydayPoolDoesNotRepeatBackToBack() {
        Scout s = TestScouts.custom("mara", 5, 30, 40, T0);
        GameRandom rng = GameRandom.fromSeed(5);
        Set<String> seen = new HashSet<>();
        String last = null;
        for (int i = 0; i < 40; i++) {
            DialogueDef d = engine.pick(s, true, List.of(s), rng, T0);
            assertNotNull(d);
            if (d.isRepeatable()) {
                assertNotEquals("same chat twice in a row", last, d.getId());
            }
            seen.add(d.getId());
            last = d.getId();
            engine.open(s, d, true, List.of(s), rng, T0);
        }
        assertTrue("a varied pool, got " + seen, seen.size() >= 10);
    }

    @Test
    public void everyBondChangeIsShown() {
        Scout s = TestScouts.custom("mara", 5, 10, 0, T0);
        List<LogLine> lines = new ArrayList<>();
        DialogueOutcomes.applyBond(content, rules, s, 3, T0, lines);
        assertEquals(1, lines.size());
        assertEquals(LogChannel.SYSTEM, lines.get(0).getChannel());
        assertTrue(lines.get(0).getText(), lines.get(0).getText().contains("20%"));
        lines.clear();
        DialogueOutcomes.applyBond(content, rules, s, -2, T0, lines);
        assertEquals(1, lines.size());
    }

    @Test
    public void bondProgressWithinALevel() {
        Scout s = TestScouts.custom("mara", 5, 10, 25, T0);
        // Thresholds 15 → 35: 25 is halfway.
        assertEquals(2, rules.bondLevel(s));
        assertEquals(50, rules.bondProgress(s));
        assertEquals(100, rules.bondProgress(TestScouts.custom("mara", 5, 10, 100, T0)));
    }
}
