package com.estatica.domain.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.ZoneDef;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** The region map: every place sits in exactly one sector, stable per seed, different per game. */
public class RegionMapTest {

    private final GameContent content = TestContent.get();

    @Test
    public void everyPlaceIsInOneSectorOfItsBand() {
        RegionMap map = new RegionMap(content, 42);
        int placed = 0;
        for (RegionMap.Sector s : map.sectors()) {
            for (String zoneId : map.zonesIn(s.id)) {
                assertEquals(s.bandId, content.zone(zoneId).getBand());
                assertEquals(s.id, map.placeOf(zoneId).sectorId);
                placed++;
            }
        }
        int expected = 0;
        for (ZoneDef z : content.getZones().values()) {
            expected += content.wilds(z.getBand()) != null ? 1 : 0;
        }
        assertEquals(expected, placed);
        assertEquals("every place can be found", content.getZones().size(), placed);
    }

    @Test
    public void scoutingGroundsAreNotPlaces() {
        ZoneDef outskirts = content.wilds("short");
        assertNotNull(outskirts);
        assertTrue(outskirts.isWilds());
        assertFalse(content.getZones().containsKey(outskirts.getId()));
        assertEquals(outskirts, content.zone(outskirts.getId()));
    }

    @Test
    public void sameSeedSameMapOtherSeedOtherMap() {
        RegionMap a = new RegionMap(content, 7);
        RegionMap b = new RegionMap(content, 7);
        int differences = 0;
        for (String zoneId : content.getZones().keySet()) {
            assertEquals(a.placeOf(zoneId).sectorId, b.placeOf(zoneId).sectorId);
            assertEquals(a.placeOf(zoneId).turn, b.placeOf(zoneId).turn, 0);
            for (long seed = 100; seed < 110; seed++) {
                differences += new RegionMap(content, seed).placeOf(zoneId).sectorId.equals(a.placeOf(zoneId).sectorId) ? 0 : 1;
            }
        }
        assertTrue(differences > 0);
    }

    @Test
    public void closeToHomePlacesAreSpreadOut() {
        for (long seed = 0; seed < 50; seed++) {
            RegionMap map = new RegionMap(content, seed);
            Set<String> sectors = new HashSet<>();
            int shortZones = 0;
            for (ZoneDef z : content.getZones().values()) {
                if ("short".equals(z.getBand())) {
                    shortZones++;
                    sectors.add(map.placeOf(z.getId()).sectorId);
                }
            }
            assertEquals("one place per direction", Math.min(shortZones, RegionMap.directionsOn(0)), sectors.size());
        }
    }

    @Test
    public void placesSitInsideTheirSectorWedge() {
        for (long seed = 0; seed < 20; seed++) {
            RegionMap map = new RegionMap(content, seed);
            for (String zoneId : content.getZones().keySet()) {
                RegionMap.Place p = map.placeOf(zoneId);
                RegionMap.Sector sector = map.sector(p.sectorId);
                double center = sector.turn();
                double offset = Math.abs(p.turn - center);
                offset = Math.min(offset, 1 - offset); // north wraps around
                assertTrue(zoneId, p.ring > 0 && p.ring < 1);
                assertTrue(zoneId + " " + offset, offset < 0.5 / sector.count);
            }
        }
    }

    @Test
    public void fartherRingsAreSplitFiner() {
        RegionMap map = new RegionMap(content, 1);
        java.util.Map<String, Integer> perBand = new java.util.HashMap<>();
        for (RegionMap.Sector s : map.sectors()) {
            perBand.merge(s.bandId, 1, Integer::sum);
        }
        assertEquals(8, (int) perBand.get("short"));
        assertEquals(12, (int) perBand.get("medium"));
        assertEquals(16, (int) perBand.get("long"));
    }
}
