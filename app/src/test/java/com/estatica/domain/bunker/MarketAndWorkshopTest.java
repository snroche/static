package com.estatica.domain.bunker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.RecipeDef;
import com.estatica.domain.content.ZoneDef;

import org.junit.Test;

import java.util.Map;

/** LA FERIA's prices, and the workshop after the rework. */
public class MarketAndWorkshopTest {

    private final GameContent content = TestContent.get();
    private final MarketRules market = new MarketRules(content, 42);
    private final WorkshopRules workshop = new WorkshopRules(content);

    @Test
    public void theSameDayIsTheSameMarket() {
        MarketRules.Day a = market.day(20_000, r -> true, 50);
        MarketRules.Day b = market.day(20_000, r -> true, 50);
        assertEquals(a.wanted, b.wanted);
        assertEquals(a.scarce, b.scarce);
        assertEquals(a.offers.size(), b.offers.size());
        assertEquals(2, a.wanted.size());
        assertEquals(1, a.scarce.size());
        for (String g : a.wanted) {
            assertFalse("wanted and short of the same thing", a.scarce.contains(g));
        }
    }

    @Test
    public void sellingALotDrivesThePriceDown() {
        MarketRules.Day day = market.day(20_001, r -> true, 50);
        int first = market.sellPrice("components", day, 50, 0);
        int later = market.sellPrice("components", day, 50, 10);
        assertTrue(later < first);
        assertTrue("there's a floor", market.sellPrice("components", day, 50, 1000) >= 1);
        assertTrue("buying costs more than selling pays",
                market.buyPrice("components", day, 50) > market.sellPrice("components", day, 50, 0));
    }

    @Test
    public void goodStandingMeansBetterPrices() {
        MarketRules.Day day = market.day(20_002, r -> true, 50);
        assertTrue(market.buyPrice("medicine", day, 100) < market.buyPrice("medicine", day, 0));
        assertTrue(market.sellPrice("medicine", day, 100, 0) > market.sellPrice("medicine", day, 0, 0));
        assertEquals(100, market.afterTrade(100));
    }

    @Test
    public void theCourierTakesACut() {
        assertEquals((int) Math.ceil(100 * content.getBalance().getMarket().getCourierFee()), market.courierFee(100));
    }

    @Test
    public void onlyUnknownPlansAreOnOffer() {
        MarketRules.Day none = market.day(20_003, r -> false, 50);
        for (MarketRules.Offer o : none.offers) {
            assertTrue("no blueprints when you know them all", o.kind != MarketRules.Offer.Kind.BLUEPRINT);
        }
        MarketRules.Day some = market.day(20_003, r -> !r.isKnown(), 50);
        boolean plans = false;
        for (MarketRules.Offer o : some.offers) {
            plans |= o.kind == MarketRules.Offer.Kind.BLUEPRINT;
            if (o.kind == MarketRules.Offer.Kind.GEAR) {
                assertTrue("rides are built, never sold", content.gear(o.id).isFindable());
            }
        }
        assertTrue(plans);
    }

    @Test
    public void laFeriaIsOnTheDialAndOnTheMap() {
        String station = content.getBalance().getMarket().getStation();
        assertTrue(content.getSignals().getStations().stream().anyMatch(s -> s.getId().equals(station)));
        ZoneDef feria = content.zone(content.getBalance().getMarket().getZone());
        assertNotNull(feria);
        assertTrue("found by its station, not stray signals", feria.isOffAir());
        assertNotNull(content.resource("chips"));
        for (String good : content.getBalance().getMarket().getGoods().keySet()) {
            assertNotNull(good, content.resource(good));
        }
    }

    @Test
    public void nobodyAtTheBenchNothingGetsMade() {
        assertEquals(0, workshop.speed(0), 0);
        assertTrue(workshop.speed(8) > workshop.speed(4));
        RecipeDef truck = content.recipe("camioneta");
        assertTrue("the truck takes real skill", truck.getMinCraft() >= 7);
        assertFalse(workshop.canMake(truck, truck.getMinCraft() - 1));
        assertTrue(workshop.canMake(truck, truck.getMinCraft()));
        assertFalse(workshop.canMake(content.recipe("parts"), 0));
    }

    @Test
    public void mostPlansStartHidden() {
        int known = 0;
        for (RecipeDef r : content.getRecipes().values()) {
            known += r.isKnown() ? 1 : 0;
            assertNotNull(r.getId(), content.getBalance().band(r.getFindBand()));
        }
        assertTrue(content.recipe("relay_kit").isKnown());
        assertTrue(content.recipe("parts").isKnown());
        assertTrue("the rest are blueprints", known <= 3);
    }

    @Test
    public void salvageAndUpgrades() {
        GearPiece common = GearPiece.fresh("gear_linterna", GearPiece.Rarity.COMMON);
        GearPiece rare = GearPiece.fresh("gear_linterna", GearPiece.Rarity.RARE);
        Map<String, Integer> back = workshop.salvage(common);
        assertFalse(back.isEmpty());
        int commonTotal = back.values().stream().mapToInt(Integer::intValue).sum();
        int rareTotal = workshop.salvage(rare).values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(rareTotal > commonTotal);
        GearPiece truck = GearPiece.fresh("gear_camioneta", GearPiece.Rarity.COMMON);
        assertTrue("a truck gives back more than a flashlight",
                workshop.salvage(truck).values().stream().mapToInt(Integer::intValue).sum() > commonTotal);
        assertTrue(workshop.upgradeCraft(common) > 0);
        assertTrue(workshop.upgradeCraft(GearPiece.fresh("gear_linterna", GearPiece.Rarity.UNCOMMON))
                > workshop.upgradeCraft(common));
        assertEquals("rare is the top", -1, workshop.upgradeCraft(rare));
        assertTrue(workshop.upgradeCost(rare).isEmpty());
        assertEquals(4, workshop.capacity(1));
    }
}
