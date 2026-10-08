package com.estatica.domain.expedition;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.domain.content.EventDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.KeyItemDef;
import com.estatica.domain.scouts.Scout;

import org.junit.Test;

import java.util.List;

/** Key items: found in specific places, carried home, never offered again once owned. */
public class KeyItemsTest {

    private static final long T0 = 1_000_000_000_000L;
    private final GameContent content = TestContent.get();

    @Test
    public void theCodebookComesHomeWithTheScout() {
        ExpeditionEngine engine = new ExpeditionEngine(content);
        boolean delivered = false;
        for (long seed = 0; seed < 300 && !delivered; seed++) {
            Scout s = TestScouts.custom("irene", 9, 0, 60, T0);
            Expedition e = engine.dispatch(s, content.zone("comisaria"), seed, T0).expedition;
            long t = T0;
            while (e.isActive()) {
                t += 60_000;
                ExpeditionResult r = engine.advance(e, s, List.of(), t);
                if (e.isAwaitingDecision()) {
                    engine.choose(e, 0, t);
                }
                for (String item : r.getDeliveredItems()) {
                    delivered |= item.equals(KeyItemDef.CARRIED_PREFIX + "military_codebook");
                }
            }
        }
        assertTrue(delivered);
    }

    @Test
    public void ownedKeysStopTheirSituationsFromComingUp() {
        ExpeditionEngine engine = new ExpeditionEngine(content);
        engine.setKeyOwnership(k -> true);
        for (long seed = 0; seed < 200; seed++) {
            Scout s = TestScouts.custom("irene", 5, 0, 0, T0);
            Expedition e = engine.dispatch(s, content.zone("comisaria"), seed, T0).expedition;
            for (Segment seg : e.getSegments()) {
                assertFalse("seed " + seed, "armeria".equals(seg.getEventId()) || "celda".equals(seg.getEventId()));
            }
        }
    }
}
