package com.estatica.domain.bunker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.core.GameRandom;
import com.estatica.domain.content.EnergyDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.expedition.Expedition;
import com.estatica.domain.expedition.ExpeditionEngine;
import com.estatica.domain.expedition.ExpeditionResult;
import com.estatica.domain.log.LogLine;
import com.estatica.domain.scouts.Injury;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutRules;
import com.estatica.domain.scouts.StatType;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Phase 4: power grid, scheduled events, raids, workshop, gear. */
public class BaseSystemsTest {

    private static final long T0 = 1_000_000_000_000L;
    private static final long HOUR = 3_600_000L;

    private GameContent content;
    private EnergyDef energy;
    private ScoutRules rules;

    @Before
    public void setUp() {
        content = TestContent.get();
        energy = content.getBalance().getEnergy();
        rules = new ScoutRules(content);
    }

    private static Map<String, Integer> plan(int filters, int radio, int antenna, int workshop, int infirmary) {
        Map<String, Integer> p = new HashMap<>();
        p.put("filters", filters);
        p.put("radio", radio);
        p.put("antenna", antenna);
        p.put("workshop", workshop);
        p.put("infirmary", infirmary);
        return p;
    }

    // ---- Power ----

    @Test
    public void enoughPowerGivesEveryoneWhatTheyAsked() {
        PowerGrid.State g = PowerGrid.compute(energy, plan(2, 2, 1, 1, 1), 1, 0, false);
        assertEquals(7, g.available);
        assertEquals(7, g.used);
        assertTrue(g.cut.isEmpty());
        assertFalse(g.filtersShort);
    }

    @Test
    public void shortagesCutTheLowestPriorityFirst() {
        PowerGrid.State g = PowerGrid.compute(energy, plan(2, 2, 1, 1, 1), 1, 4, false);
        assertEquals(3, g.available);
        assertEquals(0, g.units("workshop"));
        assertEquals(0, g.units("antenna"));
        assertEquals("filters are cut last", 2, g.units("filters"));
        assertTrue(g.cut.contains("workshop"));
    }

    @Test
    public void nightNeedsMoreFromTheFilters() {
        assertFalse(PowerGrid.compute(energy, plan(2, 0, 0, 0, 0), 1, 0, false).filtersShort);
        assertTrue(PowerGrid.compute(energy, plan(2, 0, 0, 0, 0), 1, 0, true).filtersShort);
        assertFalse(PowerGrid.compute(energy, plan(3, 0, 0, 0, 0), 1, 0, true).filtersShort);
        assertTrue(energy.isNight(23) && energy.isNight(3) && !energy.isNight(12));
    }

    @Test
    public void spareWattsAreWhatNobodyUses() {
        PowerGrid.State g = PowerGrid.compute(energy, plan(2, 1, 0, 0, 0), 2, 0, false);
        assertEquals(9 - 3, g.spare());
    }

    // ---- Scheduled events ----

    @Test
    public void eventsAreTheSameNoMatterHowCatchUpIsSplit() {
        BunkerEvents events = new BunkerEvents(energy, 77);
        List<Long> once = events.raidsBetween(T0, T0 + 60 * BunkerEvents.DAY);
        List<Long> split = new ArrayList<>();
        for (long t = T0; t < T0 + 60 * BunkerEvents.DAY; t += 5 * HOUR) {
            split.addAll(events.raidsBetween(t, Math.min(t + 5 * HOUR, T0 + 60 * BunkerEvents.DAY)));
        }
        assertEquals(once, split);
        assertTrue("some raids in two months", once.size() > 3 && once.size() < 30);
    }

    @Test
    public void blackoutsLastAWhileAndLowerOutput() {
        BunkerEvents events = new BunkerEvents(energy, 5);
        List<BunkerEvents.Blackout> all = events.blackoutsStarting(T0, T0 + 30 * BunkerEvents.DAY);
        assertTrue(all.size() > 2);
        BunkerEvents.Blackout b = all.get(0);
        assertEquals(energy.getBlackoutWatts(), events.blackoutWattsAt(b.start + 1));
        assertEquals(0, events.blackoutWattsAt(b.end));
        assertTrue(events.nextEventAfter(T0) > T0);
    }

    // ---- Raids ----

    private List<Scout> bunkerOf(Scout... scouts) {
        return List.of(scouts);
    }

    @Test
    public void strongGuardsRepelRaids() {
        RaidRules raids = new RaidRules(content, rules);
        Scout guard = TestScouts.custom("tano", 10, 0, 0, T0);
        guard.getLife().setTaskId("guardia");
        Scout other = TestScouts.custom("tano", 10, 0, 0, T0);
        other.getLife().setTaskId("guardia");
        Map<String, Integer> stock = Map.of("scrap", 10, "rations", 10);
        RaidRules.Result r = raids.resolve(bunkerOf(guard, other), stock, 1, GameRandom.fromSeed(1));
        assertTrue(r.repelled);
        assertTrue(r.stolen.isEmpty());
        assertNull(r.injured);
    }

    @Test
    public void unguardedBunkersGetRobbedButNobodyDies() {
        RaidRules raids = new RaidRules(content, rules);
        for (long seed = 0; seed < 200; seed++) {
            Scout a = TestScouts.custom("mara", 3, 0, 0, T0);
            rules.setInjury(a, Injury.CRITICAL);
            Map<String, Integer> stock = Map.of("scrap", 10, "rations", 1, "components", 0);
            RaidRules.Result r = raids.resolve(bunkerOf(a), stock, 4, GameRandom.fromSeed(seed));
            assertFalse(r.repelled);
            assertTrue(r.unguarded);
            assertNotNull(r.injured);
            assertTrue(a.isAlive());
            assertFalse("never steals what isn't there", r.stolen.containsKey("components"));
            for (int v : r.stolen.values()) {
                assertTrue(v >= 1);
            }
        }
    }

    // ---- Workshop ----

    @Test
    public void workshopJobsProgressInParallelAndPauseWithoutPower() {
        CraftJob a = CraftJob.start("parts", T0);
        CraftJob b = CraftJob.start("linterna", T0);
        List<CraftJob> jobs = List.of(a, b);
        List<CraftJob> done = CraftJob.work(jobs, List.of(6.0, 3.0), 4 / 60.0, 1.0);
        assertEquals(List.of(b), done);
        assertEquals(4.0, a.getProgressMinutes(), 1e-9);
        assertEquals(-1, a.minutesLeft(6, 0), 1e-9);
        assertEquals(1.0, a.minutesLeft(6, 2), 1e-9);
        assertTrue(CraftJob.work(List.of(a), List.of(6.0), 10, PowerGrid.workshopSpeed(0)).isEmpty());
    }

    // ---- Gear ----

    @Test
    public void gearChangesStatsAndLoot() {
        Scout s = TestScouts.custom("irene", 5, 0, 0, T0);
        int wits = rules.effectiveStat(s, StatType.WITS);
        double loot = rules.lootMultiplier(s);
        s.getLife().setGearId("gear_linterna");
        assertEquals(wits + 1, rules.effectiveStat(s, StatType.WITS));
        s.getLife().setGearId("gear_mochila");
        assertEquals(loot * 1.25, rules.lootMultiplier(s), 1e-9);
    }

    @Test
    public void theFirstAidKitIsUsedUpSavingTheScout() {
        ExpeditionEngine engine = new ExpeditionEngine(content);
        int used = 0;
        for (long seed = 0; seed < 200 && used == 0; seed++) {
            Scout s = TestScouts.custom("mara", 2, 0, 0, T0);
            s.getLife().setGearId("gear_botiquin");
            Expedition e = engine.dispatch(s, content.zone("escuela"), seed, T0).expedition;
            ExpeditionResult r = engine.advance(e, s, List.of(), T0 + 3 * HOUR);
            if (s.getLife().getGearId() == null) {
                used++;
                boolean announced = false;
                for (LogLine line : r.getLines()) {
                    announced |= line.getText().contains("botiquín");
                }
                assertTrue(announced);
            }
        }
        assertTrue(used > 0);
    }
}
