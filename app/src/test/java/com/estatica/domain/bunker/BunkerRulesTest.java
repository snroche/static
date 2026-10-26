package com.estatica.domain.bunker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.scouts.Affinities;
import com.estatica.domain.scouts.Injury;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutRules;
import com.estatica.domain.scouts.StatType;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BunkerRulesTest {

    private static final long T0 = 1_000_000_000L;
    private static final long HOUR = 3_600_000L;

    private GameContent content;
    private ScoutRules rules;
    private BunkerRules bunker;
    private Affinities affinities;

    @Before
    public void setUp() {
        content = TestContent.get();
        rules = new ScoutRules(content);
        bunker = new BunkerRules(content, rules);
        affinities = new Affinities(100);
    }

    private BunkerRules.Result advance(List<Scout> scouts, Map<String, Integer> resources, long now) {
        return bunker.advance(scouts, affinities, resources, now);
    }

    @Test
    public void restingRecoversStressFaster() {
        Scout resting = TestScouts.custom("mara", 0, 60, 0, T0);
        Scout idle = TestScouts.custom("lucho", 0, 60, 0, T0);
        resting.getLife().setTaskId("descanso");
        advance(List.of(resting, idle), Map.of(), T0 + 2 * HOUR);
        assertEquals(10, resting.getStress()); // 60 − 2 h × 10 × 2.5
        assertEquals(40, idle.getStress());    // 60 − 2 h × 10
    }

    @Test
    public void kitchenCalmsEveryoneElse() {
        Scout cook = TestScouts.custom("mara", 0, 0, 0, T0);
        Scout other = TestScouts.custom("lucho", 0, 60, 0, T0);
        cook.getLife().setTaskId("cocina");
        advance(List.of(cook, other), Map.of(), T0 + 2 * HOUR);
        assertEquals(37, other.getStress()); // 60 − 2 × (10 + 1.5)
    }

    @Test
    public void infirmaryHealsOthersFaster() {
        Scout nurse = TestScouts.custom("irene", 0, 0, 0, T0);
        Scout patient = TestScouts.custom("lucho", 0, 0, 0, T0);
        Scout alone = TestScouts.custom("mara", 0, 0, 0, T0);
        nurse.getLife().setTaskId("enfermeria");
        rules.setInjury(patient, Injury.SERIOUS);
        advance(List.of(nurse, patient), Map.of(), T0 + 3 * HOUR);
        rules.setInjury(alone, Injury.SERIOUS);
        advance(List.of(alone), Map.of(), T0 + 3 * HOUR);
        assertEquals(Injury.LIGHT, patient.getInjury()); // 3 h × 1.5 ≥ 4 h per level
        assertEquals(Injury.SERIOUS, alone.getInjury());
    }

    @Test
    public void workshopTurnsScrapIntoComponents() {
        Scout worker = TestScouts.custom("lucho", 5, 0, 0, T0);
        worker.getLife().setTaskId("taller");
        Map<String, Integer> resources = new HashMap<>();
        resources.put("scrap", 27);
        BunkerRules.Result result = advance(List.of(worker), resources, T0 + 8 * HOUR);
        assertEquals(Integer.valueOf(-6), result.resourceDelta.get("scrap"));
        assertEquals(Integer.valueOf(2), result.resourceDelta.get("components"));
    }

    @Test
    public void workshopLeavesAScrapReserveAndStopsWhenThereArePlenty() {
        Scout worker = TestScouts.custom("lucho", 5, 0, 0, T0);
        worker.getLife().setTaskId("taller");
        Map<String, Integer> low = new HashMap<>();
        low.put("scrap", 20);
        assertEquals("the reserve stays", null, advance(List.of(worker), low, T0 + 12 * HOUR).resourceDelta.get("components"));
        Map<String, Integer> full = new HashMap<>();
        full.put("scrap", 60);
        full.put("components", 40);
        assertEquals("enough components already", null,
                advance(List.of(worker), full, T0 + 12 * HOUR).resourceDelta.get("components"));
    }

    @Test
    public void workshopWithoutScrapProducesNothing() {
        Scout worker = TestScouts.custom("lucho", 5, 0, 0, T0);
        worker.getLife().setTaskId("taller");
        BunkerRules.Result result = advance(List.of(worker), new HashMap<>(), T0 + 10 * HOUR);
        assertTrue(result.resourceDelta.isEmpty());
    }

    @Test
    public void mentoringTeachesTheMentorsStrongStatAndBringsThemCloser() {
        // Irene and Mara don't clash.
        Scout student = TestScouts.starter("irene", T0);  // endurance 2
        Scout mentor = TestScouts.starter("mara", T0);    // endurance 3
        student.getLife().setTaskId("entrenamiento");
        mentor.getLife().setTaskId("entrenamiento");
        int before = student.stat(StatType.ENDURANCE).getValue();
        advance(List.of(student, mentor), Map.of(), T0 + 10 * HOUR);
        assertTrue(student.stat(StatType.ENDURANCE).getValue() > before);
        assertTrue(affinities.get(student.getId(), mentor.getId()) > 0);
    }

    @Test
    public void bondFadesOnlyAfterTheGracePeriod() {
        Scout s = TestScouts.custom("mara", 5, 0, 30, T0);
        advance(List.of(s), Map.of(), T0 + 48 * HOUR);
        assertEquals(30, s.getLife().getBond());
        advance(List.of(s), Map.of(), T0 + 48 * HOUR + 3 * 24 * HOUR);
        assertEquals(21, s.getLife().getBond()); // 3 days × 3
    }

    @Test
    public void frequentCatchUpsStillRecoverStress() {
        // Regression: rounding each tick down used to lose all recovery while the app was open.
        Scout s = TestScouts.custom("mara", 0, 50, 0, T0);
        for (long t = T0; t <= T0 + HOUR; t += 1_000) {
            advance(List.of(s), Map.of(), t);
        }
        assertTrue("stress was " + s.getStress(), s.getStress() <= 41);
    }
}
