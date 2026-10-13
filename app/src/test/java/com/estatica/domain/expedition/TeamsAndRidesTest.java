package com.estatica.domain.expedition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.domain.bunker.GearPiece;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.GearDef;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutRules;
import com.estatica.domain.scouts.ScoutStatus;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Teams of three, and rides: bikes, motorbikes, trucks. */
public class TeamsAndRidesTest {

    private static final long T0 = 1_000_000_000_000L;
    private static final long HOUR = 3_600_000L;
    private final GameContent content = TestContent.get();
    private final ExpeditionEngine engine = new ExpeditionEngine(content);
    private final ScoutRules rules = engine.rules();

    private static Scout ride(Scout s, String gearId) {
        s.getLife().wear(GearDef.Slot.RIDE, GearPiece.fresh(gearId, GearPiece.Rarity.COMMON));
        return s;
    }

    @Test
    public void threeLeaveAndThreeComeBack() {
        Scout mara = TestScouts.custom("mara", 6, 0, 0, T0);
        Scout irene = TestScouts.custom("irene", 6, 0, 0, T0);
        Scout third = TestScouts.custom("irene", 6, 0, 0, T0);
        assertNotEquals(irene.getId(), third.getId());
        ExpeditionEngine.Party party = new ExpeditionEngine.Party(List.of(irene, third), Map.of());
        ExpeditionEngine.Dispatch d = engine.dispatch(mara, content.zone("almacen"), 5, Mission.explore(), party, T0);
        assertEquals(irene.getId(), d.expedition.getPartnerId());
        assertEquals(third.getId(), d.expedition.getThirdId());
        assertTrue(d.expedition.involves(third.getId()));
        assertEquals(ScoutStatus.AWAY, third.getStatus());
        long t = T0;
        while (d.expedition.isActive() && t < T0 + 48 * HOUR) {
            t += 10 * 60_000;
            engine.advance(d.expedition, mara, party, List.of(), ExpeditionEngine.DeathContext.EMPTY, t);
        }
        assertFalse(d.expedition.isActive());
        assertEquals(ScoutStatus.IDLE, irene.getStatus());
        assertEquals(ScoutStatus.IDLE, third.getStatus());
    }

    @Test
    public void ridesOnlyCountWhenEveryoneHasASeat() {
        double[] walk = {1.0, 1.1};
        // One bike, two people: they walk at the slower one's pace.
        assertEquals(1.1, ScoutRules.teamPace(walk, new double[]{0.75}, new int[]{1}), 1e-9);
        // Two bikes: both ride.
        assertEquals(0.75, ScoutRules.teamPace(walk, new double[]{0.75, 0.75}, new int[]{1, 1}), 1e-9);
        // A motorbike seats two; with a bike along, the slower ride sets the pace.
        assertEquals(0.75, ScoutRules.teamPace(walk, new double[]{0.5, 0.75}, new int[]{2, 1}), 1e-9);
        assertEquals(0.5, ScoutRules.teamPace(walk, new double[]{0.5}, new int[]{2}), 1e-9);
        // Nobody: normal.
        assertEquals(1.0, ScoutRules.teamPace(new double[0], new double[0], new int[0]), 1e-9);
    }

    @Test
    public void aTruckTakesThreeAndParkedEnginesDont() {
        Scout mara = ride(TestScouts.custom("mara", 6, 0, 0, T0), "gear_camioneta");
        Scout irene = TestScouts.custom("irene", 6, 0, 0, T0);
        Scout third = TestScouts.custom("irene", 6, 0, 0, T0);
        List<Scout> team = List.of(mara, irene, third);
        double driving = rules.teamPace(team, Set.of());
        assertEquals(content.gear("gear_camioneta").getDurationMultiplier(), driving, 1e-9);
        // No fuel: the truck stays parked, everyone walks.
        double walking = rules.teamPace(team, Set.of(rules.ride(mara).getId()));
        assertTrue(walking > driving);
        assertTrue("the bed brings more home", rules.rideLootMultiplier(mara) > 1.0);
    }

    @Test
    public void ridesArentWornOnFootAndNeverDropAtRandom() {
        Scout mara = TestScouts.custom("mara", 6, 0, 0, T0);
        double onFoot = rules.durationMultiplier(mara);
        ride(mara, "gear_bici");
        assertEquals("a bike isn't boots", onFoot, rules.durationMultiplier(mara), 1e-9);
        for (String id : new String[]{"gear_bici", "gear_moto", "gear_camioneta"}) {
            GearDef def = content.gear(id);
            assertTrue(id, def.isRide());
            assertFalse(id + " must be built, not found", def.isFindable());
            assertTrue(id + " has a recipe", content.getRecipes().values().stream()
                    .anyMatch(r -> id.equals(r.getOutput())));
        }
        assertTrue("the motorbike burns fuel", content.gear("gear_moto").getFuelPerHour() > 0);
        assertEquals("the bike doesn't", 0.0, content.gear("gear_bici").getFuelPerHour(), 0);
    }
}
