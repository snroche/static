package com.estatica.domain.expedition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.domain.content.EventDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.ZoneDef;
import com.estatica.domain.log.LogLine;
import com.estatica.domain.scouts.Injury;
import com.estatica.domain.scouts.Memory;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutStatus;
import com.estatica.domain.scouts.ScoutTrait;
import com.estatica.domain.scouts.Stat;
import com.estatica.domain.scouts.StatType;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExpeditionEngineTest {

    private static final long T0 = 1_000_000_000L;
    private static final long HOUR = 3_600_000L;

    private GameContent content;
    private ExpeditionEngine engine;

    @Before
    public void setUp() {
        content = TestContent.get();
        engine = new ExpeditionEngine(content);
    }

    private static Scout scout(int statValue, int stress, List<ScoutTrait> traits) {
        Map<StatType, Stat> stats = new EnumMap<>(StatType.class);
        for (StatType type : StatType.values()) {
            stats.put(type, new Stat(statValue, 10, 0));
        }
        return new Scout("scout-1", null, "Test", "formal", stats, traits, stress, 50,
                Injury.NONE, ScoutStatus.IDLE, T0, 0);
    }

    /** Runs an expedition to the end with no operator input (all situations time out). */
    private static final class Run {
        final Scout scout;
        final Expedition expedition;
        final List<String> log = new ArrayList<>();
        final List<Memory> memories = new ArrayList<>();
        final Map<String, Integer> delivered = new LinkedHashMap<>();

        Run(ExpeditionEngine engine, Scout scout, ZoneDef zone, long seed) {
            this.scout = scout;
            ExpeditionEngine.Dispatch d = engine.dispatch(scout, zone, seed, T0);
            this.expedition = d.expedition;
            collect(d.lines);
        }

        void advance(ExpeditionEngine engine, long now) {
            ExpeditionResult r = engine.advance(expedition, scout, memories, now);
            collect(r.getLines());
            memories.addAll(r.getMemories());
            r.getDelivered().forEach((k, v) -> delivered.merge(k, v, Integer::sum));
        }

        private void collect(List<LogLine> lines) {
            for (LogLine line : lines) {
                log.add(line.getTimestampMillis() + "|" + line.getChannel() + "|" + line.getText());
            }
        }
    }

    @Test
    public void catchingUpOnceEqualsCatchingUpStepByStep() {
        for (ZoneDef zone : content.getZones().values()) {
            for (long seed = 0; seed < 60; seed++) {
                Run once = new Run(engine, scout(5, 10, List.of()), zone, seed);
                Run steps = new Run(engine, scout(5, 10, List.of()), zone, seed);

                long end = T0 + 3 * HOUR;
                once.advance(engine, end);
                for (long t = T0; t <= end; t += 7_000) {
                    steps.advance(engine, t);
                }
                steps.advance(engine, end);

                String where = zone.getId() + " seed " + seed;
                assertEquals(where, once.log, steps.log);
                assertEquals(where, once.delivered, steps.delivered);
                assertEquals(where, once.expedition.getStatus(), steps.expedition.getStatus());
                assertEquals(where, once.scout.getStress(), steps.scout.getStress());
                assertEquals(where, once.scout.getInjury(), steps.scout.getInjury());
                assertEquals(where, once.memories.size(), steps.memories.size());
                assertFalse(where, once.expedition.isActive());
            }
        }
    }

    @Test
    public void situationWaitsForTheOperatorThenUsesTheOrder() {
        Run run = new Run(engine, scout(5, 10, List.of()), content.zone("almacen"), 42);
        long t = T0;
        while (!run.expedition.isAwaitingDecision()) {
            t += 1_000;
            run.advance(engine, t);
            assertTrue("never reached a situation", run.expedition.isActive());
        }
        assertNotNull(engine.pendingEvent(run.expedition));
        long arrived = run.expedition.getPendingSinceMillis();
        assertEquals(arrived + engine.timeoutMillis(), engine.nextEventAt(run.expedition));

        // Nothing changes while waiting, however long, until the deadline.
        int before = run.log.size();
        run.advance(engine, arrived + engine.timeoutMillis() - 1);
        assertEquals(before, run.log.size());
        assertTrue(run.expedition.isAwaitingDecision());

        long answer = arrived + 60_000;
        assertTrue(engine.choose(run.expedition, 0, answer));
        run.advance(engine, answer);
        assertFalse(run.expedition.isAwaitingDecision());
        assertNull(engine.pendingEvent(run.expedition));
        assertTrue(run.log.size() > before);
        // Resolution lines are stamped at the time of the answer.
        assertTrue(run.log.get(before).startsWith(answer + "|"));
    }

    @Test
    public void unansweredSituationIsDecidedAtTheDeadline() {
        Run run = new Run(engine, scout(5, 10, List.of()), content.zone("almacen"), 42);
        long t = T0;
        while (!run.expedition.isAwaitingDecision()) {
            t += 1_000;
            run.advance(engine, t);
        }
        long deadline = engine.deadline(run.expedition);
        int before = run.log.size();
        run.advance(engine, deadline + HOUR);
        assertTrue(run.log.get(before).startsWith(deadline + "|"));
        assertFalse(engine.choose(run.expedition, 0, deadline + HOUR));
    }

    @Test
    public void lateAnswerIsRejected() {
        Run run = new Run(engine, scout(5, 10, List.of()), content.zone("almacen"), 42);
        long t = T0;
        while (!run.expedition.isAwaitingDecision()) {
            t += 1_000;
            run.advance(engine, t);
        }
        assertFalse(engine.choose(run.expedition, 0, engine.deadline(run.expedition)));
    }

    @Test
    public void returnDeliversLootAndFreesTheScout() {
        int returned = 0;
        for (long seed = 0; seed < 100; seed++) {
            Run run = new Run(engine, scout(7, 10, List.of()), content.zone("estacion"), seed);
            assertEquals(ScoutStatus.AWAY, run.scout.getStatus());
            run.advance(engine, T0 + 3 * HOUR);
            if (run.expedition.getStatus() == Expedition.Status.COMPLETED) {
                returned++;
                assertEquals(ScoutStatus.IDLE, run.scout.getStatus());
                assertTrue(run.expedition.getCarriedLoot().isEmpty());
                assertFalse("finds always bring something", run.delivered.isEmpty());
            }
        }
        assertTrue(returned > 90);
    }

    /** Plays to the end, ordering the first lethal option of every situation (if any). */
    private void playOrderingLethalOptions(Run run) {
        long t = T0;
        while (run.expedition.isActive()) {
            t += 5_000;
            run.advance(engine, t);
            if (run.expedition.isAwaitingDecision()) {
                List<EventDef.Option> options = engine.pendingEvent(run.expedition).getOptions();
                int choice = 0;
                for (int i = 0; i < options.size(); i++) {
                    if (options.get(i).getFailure().isLethal()) {
                        choice = i;
                        break;
                    }
                }
                assertTrue(engine.choose(run.expedition, choice, t));
                run.advance(engine, t);
            }
        }
    }

    @Test
    public void noDeathWithoutWarning() {
        // Almacén has exactly one situation. A calm, uninjured, highly skilled scout is never
        // warned (odds are "good"), so even ordered into lethal options they must survive,
        // taking the outcome's injury instead.
        int hurt = 0;
        for (long seed = 0; seed < 1500; seed++) {
            Run run = new Run(engine, scout(10, 0, List.of()), content.zone("almacen"), seed);
            playOrderingLethalOptions(run);
            assertTrue("seed " + seed, run.scout.isAlive());
            assertEquals(Expedition.Status.COMPLETED, run.expedition.getStatus());
            if (run.scout.getInjury() != Injury.NONE) {
                hurt++;
            }
        }
        assertTrue("some lethal failures should have left injuries", hurt > 0);
    }

    @Test
    public void scoutsDecidingAloneNeverDie() {
        // Warned (stressed, bad odds) but nobody answers: hurt, never dead (GDD §4.2).
        for (ZoneDef zone : content.getZones().values()) {
            for (long seed = 0; seed < 400; seed++) {
                Run run = new Run(engine, scout(2, 95, List.of(new ScoutTrait("imprudencia", false, false))),
                        zone, seed);
                run.advance(engine, T0 + 3 * HOUR);
                assertTrue(zone.getId() + " seed " + seed, run.scout.isAlive());
            }
        }
    }

    @Test
    public void criticalInjurySendsTheScoutHome() {
        int critical = 0;
        for (long seed = 0; seed < 400; seed++) {
            Run run = new Run(engine, scout(2, 10, List.of()), content.zone("escuela"), seed);
            run.advance(engine, T0 + 3 * HOUR);
            if (run.scout.getInjury() == Injury.CRITICAL) {
                critical++;
                assertEquals(Expedition.Status.COMPLETED, run.expedition.getStatus());
                assertEquals(ScoutStatus.IDLE, run.scout.getStatus());
            }
        }
        assertTrue(critical > 0);
    }

    @Test
    public void warnedScoutsCanDie() {
        // Stressed scout with poor odds ("mala espina"): warned, so lethal failures kill.
        int deaths = 0;
        for (long seed = 0; seed < 400; seed++) {
            Run run = new Run(engine, scout(3, 90, List.of()), content.zone("farmacia"), seed);
            playOrderingLethalOptions(run);
            if (!run.scout.isAlive()) {
                deaths++;
                assertEquals(Expedition.Status.LOST, run.expedition.getStatus());
                assertTrue(run.delivered.isEmpty());
                assertTrue(run.memories.stream().anyMatch(m -> m.getType() == Memory.Type.LAST_WORDS));
                assertTrue(run.log.stream().anyMatch(l -> l.contains("señal perdida")));
            }
        }
        assertTrue(deaths > 0);
    }

    @Test
    public void noticesPredictExactlyWhenThingsHappen() {
        for (long seed = 0; seed < 50; seed++) {
            Run run = new Run(engine, scout(10, 0, List.of()), content.zone("almacen"), seed);

            ExpeditionEngine.Notice situation = engine.nextNotice(run.expedition);
            assertEquals(ExpeditionEngine.Notice.Type.SITUATION, situation.type);
            run.advance(engine, situation.atMillis - 1);
            assertFalse(run.expedition.isAwaitingDecision());
            run.advance(engine, situation.atMillis);
            assertTrue(run.expedition.isAwaitingDecision());
            assertEquals(situation.atMillis, run.expedition.getPendingSinceMillis());
            assertNull("already seen: no notice while waiting", engine.nextNotice(run.expedition));

            long answer = situation.atMillis + 30_000;
            assertTrue(engine.choose(run.expedition, 0, answer));
            run.advance(engine, answer);

            ExpeditionEngine.Notice back = engine.nextNotice(run.expedition);
            assertEquals(ExpeditionEngine.Notice.Type.RETURN, back.type);
            run.advance(engine, back.atMillis - 1);
            assertTrue(run.expedition.isActive());
            run.advance(engine, back.atMillis);
            assertEquals(Expedition.Status.COMPLETED, run.expedition.getStatus());
            assertNull(engine.nextNotice(run.expedition));
        }
    }

    // ---- Phase 2 ----

    /** Advances until the first situation is waiting. */
    private void untilSituation(Run run) {
        long t = T0;
        while (!run.expedition.isAwaitingDecision() && run.expedition.isActive()) {
            t += 1_000;
            run.advance(engine, t);
        }
    }

    @Test
    public void aCloseWarnedScoutAsksToComeHome() {
        Scout close = TestScouts.custom("mara", 5, 80, 40, T0);
        Run run = new Run(engine, close, content.zone("almacen"), 7);
        untilSituation(run);
        assertTrue(run.expedition.isRetreatOffered());
        List<String> options = engine.pendingOptions(run.expedition);
        assertEquals("Volvé al búnker", options.get(options.size() - 1));

        long now = run.expedition.getPendingSinceMillis() + 1_000;
        assertTrue(engine.choose(run.expedition, options.size() - 1, now));
        run.advance(engine, now + 3 * HOUR);
        assertEquals(Expedition.Status.COMPLETED, run.expedition.getStatus());
        assertTrue(close.isAlive());
    }

    @Test
    public void strangersDontAskToComeHome() {
        Scout stranger = TestScouts.custom("mara", 5, 80, 0, T0);
        Run run = new Run(engine, stranger, content.zone("almacen"), 7);
        untilSituation(run);
        assertFalse(run.expedition.isRetreatOffered());
        assertEquals(content.event(run.expedition.currentSegment().getEventId()).getOptions().size(),
                engine.pendingOptions(run.expedition).size());
    }

    @Test
    public void personalObjectsTurnUpAndComeHome() {
        int found = 0;
        for (long seed = 0; seed < 200; seed++) {
            Run run = new Run(engine, TestScouts.custom("irene", 7, 0, 0, T0), content.zone("escuela"), seed);
            ExpeditionResult last = null;
            long t = T0;
            while (run.expedition.isActive()) {
                t += 60_000;
                last = engine.advance(run.expedition, run.scout, List.of(), t);
            }
            if (last != null && !last.getDeliveredItems().isEmpty()) {
                found++;
                assertTrue(content.getItems().containsKey(last.getDeliveredItems().get(0)));
            }
        }
        assertTrue("escuela should yield objects sometimes", found > 10);
    }

    @Test
    public void lastWordsRememberTheGiftAndTheCompanion() {
        ExpeditionEngine.DeathContext death =
                new ExpeditionEngine.DeathContext(List.of("armónica"), "Irene");
        int deaths = 0;
        int mentions = 0;
        for (long seed = 0; seed < 400; seed++) {
            Scout s = scout(3, 90, List.of());
            ExpeditionEngine.Dispatch d = engine.dispatch(s, content.zone("farmacia"), seed, T0);
            long t = T0;
            List<Memory> memories = new ArrayList<>();
            while (d.expedition.isActive()) {
                t += 5_000;
                ExpeditionResult r = engine.advance(d.expedition, s, memories, death, t);
                memories.addAll(r.getMemories());
                List<String> options = engine.pendingOptions(d.expedition);
                if (!options.isEmpty()) {
                    engine.choose(d.expedition, 0, t);
                }
            }
            for (Memory m : memories) {
                if (m.getType() == Memory.Type.LAST_WORDS) {
                    deaths++;
                    if (m.getText().contains("Irene") || m.getText().contains("armónica")) {
                        mentions++;
                    }
                }
            }
        }
        assertTrue(deaths > 0);
        assertTrue("most last words name the gift or the companion", mentions * 2 > deaths);
    }

    @Test
    public void criticallyInjuredScoutCantBeDispatched() {
        Scout s = scout(5, 10, List.of());
        engine.rules().setInjury(s, Injury.CRITICAL);
        assertFalse(engine.rules().canDispatch(s));
    }
}
