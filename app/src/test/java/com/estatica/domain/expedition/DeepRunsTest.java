package com.estatica.domain.expedition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.domain.content.Balance;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.ZoneDef;

import org.junit.Test;

/** Past the map's edge: kept off the map, open with the new antenna tiers, harder by tier. */
public class DeepRunsTest {

    private final GameContent content = TestContent.get();

    @Test
    public void offTheMapAndNeverRunDry() {
        assertTrue(content.getDeepRuns().size() >= 3);
        for (ZoneDef z : content.getDeepRuns()) {
            assertTrue(z.isEndless());
            assertEquals("deep", z.getBand());
            assertFalse("not a place on the map", content.getZones().containsKey(z.getId()));
            assertEquals(z, content.zone(z.getId()));
            for (String e : z.getEvents()) {
                assertTrue(z.getId() + ": " + e, content.getEvents().containsKey(e));
            }
        }
    }

    @Test
    public void tiersNeedTheNewAntennaLevels() {
        Balance balance = content.getBalance();
        int maxAntenna = balance.module("antenna").getMaxLevel();
        int[] needs = balance.getDeepRuns().getTierAntenna();
        assertTrue("past the deep band's own level", needs[0] > balance.band("deep").getUnlockAntennaLevel());
        for (int n : needs) {
            assertTrue(n <= maxAntenna);
        }
        assertEquals(maxAntenna, needs[needs.length - 1]);
    }

    @Test
    public void harderTiersTakeLongerAndPayMore() {
        Balance.DeepRuns d = content.getBalance().getDeepRuns();
        assertEquals(1.0, d.durationFactor(1), 1e-9);
        assertTrue(d.durationFactor(3) > d.durationFactor(2));
        assertTrue(d.lootFactor(3) > d.lootFactor(2));
        assertTrue(d.oddsPenalty(3) > d.oddsPenalty(1));
        Mission tier2 = new Mission(Mission.Type.DEEP, "2", 0, 0, false, false);
        assertEquals(2, tier2.getTier());
        assertEquals(0, Mission.explore().getTier());
    }
}
