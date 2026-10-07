package com.estatica.domain.expedition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.domain.content.EventDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.log.LogLine;
import com.estatica.domain.scouts.Injury;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutStatus;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/** Two scouts out together (GDD §5.3). */
public class PairExpeditionTest {

    private static final long T0 = 1_000_000_000_000L;
    private static final long HOUR = 3_600_000L;

    private GameContent content;
    private ExpeditionEngine engine;

    @Before
    public void setUp() {
        content = TestContent.get();
        engine = new ExpeditionEngine(content);
    }

    private static int total(Map<String, Integer> loot) {
        int sum = 0;
        for (int v : loot.values()) {
            sum += v;
        }
        return sum;
    }

    /** Runs to the end letting them decide alone. */
    private ExpeditionResult runAlone(Expedition e, Scout lead, ExpeditionEngine.Party party) {
        ExpeditionResult last = null;
        long t = T0;
        while (e.isActive() && t < T0 + 48 * HOUR) {
            t += 10 * 60_000;
            last = engine.advance(e, lead, party, List.of(), ExpeditionEngine.DeathContext.EMPTY, t);
        }
        return last;
    }

    @Test
    public void bothLeaveAndBothComeBack() {
        Scout mara = TestScouts.custom("mara", 6, 0, 0, T0);
        Scout irene = TestScouts.custom("irene", 6, 0, 0, T0);
        ExpeditionEngine.Party party = new ExpeditionEngine.Party(irene, null);
        ExpeditionEngine.Dispatch d = engine.dispatch(mara, content.zone("almacen"), 5, Mission.explore(), party, T0);
        assertEquals(irene.getId(), d.expedition.getPartnerId());
        assertTrue(d.expedition.involves(irene.getId()));
        assertEquals(ScoutStatus.AWAY, mara.getStatus());
        assertEquals(ScoutStatus.AWAY, irene.getStatus());
        boolean joined = false;
        for (LogLine l : d.lines) {
            joined |= l.getText().contains(irene.getName()) && l.getText().contains(mara.getName());
        }
        assertTrue(joined);
        runAlone(d.expedition, mara, party);
        assertFalse(d.expedition.isActive());
        assertEquals(ScoutStatus.IDLE, irene.getStatus());
    }

    @Test
    public void aPairBringsBackMore() {
        int alone = 0;
        int together = 0;
        for (long seed = 0; seed < 60; seed++) {
            Scout a = TestScouts.custom("mara", 6, 0, 0, T0);
            Expedition solo = engine.dispatch(a, content.zone("almacen"), seed, T0).expedition;
            long t = T0;
            while (solo.isActive()) {
                t += 10 * 60_000;
                alone += total(engine.advance(solo, a, List.of(), t).getDelivered());
            }
            Scout b = TestScouts.custom("mara", 6, 0, 0, T0);
            Scout c = TestScouts.custom("irene", 6, 0, 0, T0);
            ExpeditionEngine.Party party = new ExpeditionEngine.Party(c, null);
            Expedition pair = engine.dispatch(b, content.zone("almacen"), seed, Mission.explore(), party, T0).expedition;
            t = T0;
            while (pair.isActive()) {
                t += 10 * 60_000;
                together += total(engine.advance(pair, b, party, List.of(),
                        ExpeditionEngine.DeathContext.EMPTY, t).getDelivered());
            }
        }
        assertTrue(together > alone);
    }

    @Test
    public void thePartnersSkillsCountToo() {
        // A nurse partner unlocks the nurse's options for a lead who isn't one.
        EventDef wounded = content.event("herido");
        EventDef.Option treat = null;
        for (EventDef.Option o : wounded.getOptions()) {
            if ("tratar".equals(o.getId())) {
                treat = o;
            }
        }
        Scout mara = TestScouts.starter("mara", T0);
        Scout nurse = TestScouts.person("nurse", 5, 0, 0, T0);
        assertFalse(engine.isAvailable(mara, ExpeditionEngine.Party.ALONE, treat));
        assertTrue(engine.isAvailable(mara, new ExpeditionEngine.Party(nurse, null), treat));
    }

    @Test
    public void sometimesThePartnerCarriesTheLeadHome() {
        int carried = 0;
        int died = 0;
        for (long seed = 0; seed < 400 && (carried == 0 || died == 0); seed++) {
            // Badly hurt and stressed: warned, so an ordered lethal outcome can kill.
            Scout lead = TestScouts.custom("mara", 1, 90, 0, T0);
            com.estatica.domain.scouts.ScoutRules rules = engine.rules();
            rules.injure(lead, Injury.SERIOUS);
            Scout partner = TestScouts.custom("irene", 9, 0, 0, T0);
            ExpeditionEngine.Party party = new ExpeditionEngine.Party(partner, "friends");
            Expedition e = engine.dispatch(lead, content.zone("hospital"), seed, Mission.explore(), party, T0).expedition;
            long t = T0;
            while (e.isActive() && t < T0 + 24 * HOUR) {
                t += 30_000;
                engine.advance(e, lead, party, List.of(), ExpeditionEngine.DeathContext.EMPTY, t);
                if (e.isAwaitingDecision()) {
                    EventDef ev = engine.pendingEvent(e);
                    int risky = 0;
                    for (int i = 0; i < ev.getOptions().size(); i++) {
                        if (ev.getOptions().get(i).getRisk() == EventDef.Option.Risk.RISKY
                                && engine.isAvailable(lead, party, ev.getOptions().get(i))) {
                            risky = i;
                        }
                    }
                    engine.choose(e, risky, t);
                }
            }
            if (!lead.isAlive()) {
                died++;
                assertEquals("the partner still comes home", ScoutStatus.IDLE, partner.getStatus());
            } else if (lead.getInjury() == Injury.CRITICAL && e.isAborted()) {
                carried++;
            }
        }
        assertTrue("carried home at least once", carried > 0);
    }
}
