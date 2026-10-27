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
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;
import com.estatica.domain.scouts.Scout;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Prices out there, situations that lead into others, and saying plainly what came of them. */
public class EventChainsTest {

    private static final long T0 = 1_000_000_000_000L;
    private final GameContent content = TestContent.get();

    /** A trip whose first situation is {@code eventId}, advanced until it's waiting for orders. */
    private static final class Trip {
        Expedition expedition;
        Scout scout;
        long now;
    }

    private long nextSeed;

    private Trip tripTo(ExpeditionEngine engine, String zone, String eventId, int stats) {
        for (long seed = nextSeed; seed < nextSeed + 20000; seed++) {
            Scout s = TestScouts.custom("irene", stats, 0, 0, T0);
            Expedition e = engine.dispatch(s, content.zone(zone), seed, T0).expedition;
            String first = null;
            for (Segment seg : e.getSegments()) {
                if (seg.getType() == Segment.Type.SITUATION) {
                    first = seg.getEventId();
                    break;
                }
            }
            if (!eventId.equals(first)) {
                continue;
            }
            nextSeed = seed + 1; // the next trip is a different one
            Trip trip = new Trip();
            trip.expedition = e;
            trip.scout = s;
            trip.now = T0;
            while (!e.isAwaitingDecision()) {
                trip.now += 30_000;
                engine.advance(e, s, List.of(), trip.now);
            }
            return trip;
        }
        throw new AssertionError("never came up: " + eventId);
    }

    private static int optionIndex(EventDef event, String id) {
        for (int i = 0; i < event.getOptions().size(); i++) {
            if (event.getOptions().get(i).getId().equals(id)) {
                return i;
            }
        }
        throw new AssertionError(id);
    }

    /** Orders an option and advances until the situation is resolved (or the next one waits). */
    private ExpeditionResult order(ExpeditionEngine engine, Trip trip, int option) {
        assertTrue(engine.choose(trip.expedition, option, trip.now));
        ExpeditionResult all = new ExpeditionResult();
        int index = trip.expedition.getIndex();
        String waiting = trip.expedition.currentSegment().getEventId();
        do {
            trip.now += 30_000;
            ExpeditionResult r = engine.advance(trip.expedition, trip.scout, List.of(), trip.now);
            for (LogLine l : r.getLines()) {
                all.line(l);
            }
            r.getSpent().forEach(all::spend);
        } while (trip.expedition.isActive() && trip.expedition.getIndex() == index
                && trip.expedition.isAwaitingDecision()
                && waiting.equals(trip.expedition.currentSegment().getEventId()));
        return all;
    }

    @Test
    public void aTradeIsPaidForOutOfTheBunker() {
        ExpeditionEngine engine = new ExpeditionEngine(content);
        Map<String, Integer> bunker = new HashMap<>();
        bunker.put("batteries", 10);
        engine.setStock(id -> bunker.getOrDefault(id, 0));
        Trip trip = tripTo(engine, "hospital", "c_trader", 9);
        EventDef trader = content.event("c_trader");
        int trade = optionIndex(trader, "trade");
        String label = engine.pendingOptions(trip.expedition, trip.scout).get(trade);
        assertTrue("the price is on the button: " + label, label.contains("2 batteries"));

        ExpeditionResult r = order(engine, trip, trade);
        assertEquals(Integer.valueOf(2), r.getSpent().get("batteries"));
        boolean said = false;
        for (LogLine l : r.getLines()) {
            said |= l.getChannel() == LogChannel.SYSTEM && l.getText().contains("paid 2 batteries");
        }
        assertTrue("the tally says what it cost", said);
    }

    @Test
    public void whatYouCantPayForIsntOnOffer() {
        ExpeditionEngine engine = new ExpeditionEngine(content);
        engine.setStock(id -> 0);
        Trip trip = tripTo(engine, "hospital", "c_trader", 9);
        if (trip.expedition.getCarriedLoot().getOrDefault("batteries", 0) >= 2) {
            return; // found some on the way: they can pay out of the bag
        }
        EventDef trader = content.event("c_trader");
        assertNull(engine.pendingOptions(trip.expedition, trip.scout).get(optionIndex(trader, "trade")));
        assertNotNull(engine.pendingOptions(trip.expedition, trip.scout).get(optionIndex(trader, "pass")));
    }

    @Test
    public void aSituationCanLeadStraightIntoAnother() {
        ExpeditionEngine engine = new ExpeditionEngine(content);
        boolean chained = false;
        for (int attempt = 0; attempt < 40 && !chained; attempt++) {
            Trip trip = tripTo(engine, "almacen", "ch_cellar_1", 9);
            int knock = optionIndex(content.event("ch_cellar_1"), "knock");
            order(engine, trip, knock);
            for (int i = 0; i < 6 && trip.expedition.isActive() && !trip.expedition.isAwaitingDecision(); i++) {
                trip.now += 30_000;
                engine.advance(trip.expedition, trip.scout, List.of(), trip.now);
            }
            if (trip.expedition.isAwaitingDecision()
                    && "ch_cellar_2".equals(trip.expedition.currentSegment().getEventId())) {
                chained = true;
                assertTrue("the new step gets saved", trip.expedition.isSegmentsChanged());
                assertFalse(engine.situationText(trip.expedition, trip.scout).isEmpty());
            }
        }
        assertTrue(chained);
    }

    @Test
    public void followUpsNeverComeUpOnTheirOwn() {
        ExpeditionEngine engine = new ExpeditionEngine(content);
        for (long seed = 0; seed < 500; seed++) {
            Scout s = TestScouts.custom("irene", 5, 0, 0, T0);
            for (Segment seg : engine.dispatch(s, content.zone("almacen"), seed, T0).expedition.getSegments()) {
                if (seg.getEventId() != null) {
                    assertFalse(seg.getEventId(), content.event(seg.getEventId()).isFollowUp());
                }
            }
        }
    }

    @Test
    public void whatCameOfItIsSaidPlainly() {
        ExpeditionEngine engine = new ExpeditionEngine(content);
        engine.setStock(id -> 50);
        boolean tallied = false;
        for (int attempt = 0; attempt < 20 && !tallied; attempt++) {
            Trip trip = tripTo(engine, "hospital", "c_trader", 9);
            ExpeditionResult r = order(engine, trip, optionIndex(content.event("c_trader"), "trade"));
            for (LogLine l : r.getLines()) {
                tallied |= l.getChannel() == LogChannel.SYSTEM && l.getText().contains("rations");
            }
        }
        assertTrue("+N rations, not just 'eggs!'", tallied);
    }
}
