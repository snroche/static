package com.estatica.domain.expedition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.core.GameRandom;
import com.estatica.domain.content.EventDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.ZoneDef;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Common situations per band, fresh ones first, and options that cost or save time. */
public class SituationVarietyTest {

    private final GameContent content = TestContent.get();

    @Test
    public void everyBandHasCommonSituations() {
        for (String band : List.of("short", "medium", "long")) {
            long common = content.getEvents().values().stream().filter(e -> e.getBands().contains(band)).count();
            assertTrue(band + ": " + common, common >= 10);
        }
    }

    @Test
    public void recentSituationsWaitAtTheBack() {
        ZoneDef zone = content.zone("almacen");
        List<String> pool = new ArrayList<>(zone.getEvents());
        Set<String> recent = Set.copyOf(zone.getEvents());
        for (EventDef e : content.getEvents().values()) {
            if (e.getBands().contains(zone.getBand())) {
                pool.add(e.getId());
            }
        }
        for (long seed = 0; seed < 50; seed++) {
            List<Segment> segments = TimelineGenerator.generate(zone, 1.0, GameRandom.fromSeed(seed), pool,
                    id -> true, recent::contains);
            for (Segment s : segments) {
                if (s.getEventId() != null) {
                    assertFalse("seen lately, but fresh ones were left: " + s.getEventId(),
                            recent.contains(s.getEventId()));
                }
            }
        }
    }

    @Test
    public void waitingCostsTimeAndShortcutsOnlySaveItWhenTheyWork() {
        ZoneDef zone = content.zone("almacen");
        List<Segment> segments = List.of(
                new Segment(Segment.Type.TRANSIT, 60_000, null),
                new Segment(Segment.Type.SITUATION, 60_000, "saqueadores"),
                new Segment(Segment.Type.TRANSIT, 120_000, null),
                new Segment(Segment.Type.RETURN, 60_000, null));
        Expedition e = new Expedition("e", "s", zone.getId(), 1, 0, new ArrayList<>(segments), 0,
                Expedition.Status.ACTIVE, 1, 0, Expedition.NONE, Expedition.NO_CHOICE, Expedition.NONE,
                java.util.Map.of(), List.of(), false, Mission.explore(), false, 0);
        ExpeditionEngine engine = new ExpeditionEngine(content);
        EventDef bandits = content.event("saqueadores");
        EventDef.Option hide = option(bandits, "esconderse");
        EventDef.Option rob = option(bandits, "robar");
        assertEquals(Math.round(300_000 * hide.getTime()), engine.timeShift(e, hide, false));
        assertTrue(engine.timeShift(e, rob, true) < 0);
        assertEquals(0, engine.timeShift(e, rob, false));
        // Never sooner than most of the next leg.
        assertTrue(engine.timeShift(e, rob, true) >= -120_000 * 4 / 5);
    }

    private static EventDef.Option option(EventDef e, String id) {
        for (EventDef.Option o : e.getOptions()) {
            if (o.getId().equals(id)) {
                return o;
            }
        }
        throw new AssertionError(id);
    }
}
