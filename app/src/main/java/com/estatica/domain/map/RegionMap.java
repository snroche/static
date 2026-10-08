package com.estatica.domain.map;

import com.estatica.core.GameRandom;
import com.estatica.domain.content.BandDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.ZoneDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The region around the bunker: rings by band (frequency = distance, GDD §4.4) split into
 * eight directions. Every zone sits in one sector, placed from the game seed, so each game's
 * map is different but stable. Scouting a sector reveals the zones in it.
 */
public final class RegionMap {

    /** Sectors on the innermost ring; each ring farther out has {@link #MORE_PER_RING} more. */
    public static final int DIRECTIONS = 8;
    public static final int MORE_PER_RING = 4;

    /** Directions on a ring: farther rings cover more ground, so they're split finer (8, 12, 16…). */
    public static int directionsOn(int ring) {
        return DIRECTIONS + MORE_PER_RING * ring;
    }

    /** One cell of the map: a band ring and a direction. */
    public static final class Sector {
        public final String id;
        public final String bandId;
        public final int bandIndex;
        /** Ring on the map (0 = closest), counting only bands that have sectors. */
        public final int ring;
        /** 0 = north, clockwise, out of {@link #count}. */
        public final int direction;
        public final int count;

        Sector(String bandId, int bandIndex, int ring, int direction, int count) {
            this.id = bandId + ":" + direction;
            this.bandId = bandId;
            this.bandIndex = bandIndex;
            this.ring = ring;
            this.direction = direction;
            this.count = count;
        }

        /** Center of the sector, as a fraction of a turn clockwise from north. */
        public double turn() {
            return (double) direction / count;
        }
    }

    /** Where a zone is drawn: ring position (0 inner edge, 1 outer edge) and angle (0–1 of a turn). */
    public static final class Place {
        public final String sectorId;
        public final double ring;
        public final double turn;

        Place(String sectorId, double ring, double turn) {
            this.sectorId = sectorId;
            this.ring = ring;
            this.turn = turn;
        }
    }

    private final Map<String, Sector> sectors = new LinkedHashMap<>();
    private final Map<String, Place> places = new LinkedHashMap<>();
    private final Map<String, List<String>> zonesBySector = new LinkedHashMap<>();

    public RegionMap(GameContent content, long gameSeed) {
        List<BandDef> bands = content.getBalance().getBands();
        Map<String, Integer> countOf = new LinkedHashMap<>();
        int ringIndex = 0;
        for (int b = 0; b < bands.size(); b++) {
            BandDef band = bands.get(b);
            if (content.wilds(band.getId()) == null) {
                continue; // No scouting ground on this band (yet): no sectors.
            }
            int count = directionsOn(ringIndex);
            countOf.put(band.getId(), count);
            for (int d = 0; d < count; d++) {
                Sector s = new Sector(band.getId(), b, ringIndex, d, count);
                sectors.put(s.id, s);
                zonesBySector.put(s.id, new ArrayList<>());
            }
            ringIndex++;
        }
        // Each band's zones go to different directions first (shuffled per game), so scouting
        // close to home isn't mostly empty. String.hashCode is fixed by the language spec.
        Map<String, Integer> directionOf = new LinkedHashMap<>();
        for (BandDef band : bands) {
            List<String> ids = new ArrayList<>();
            for (ZoneDef z : content.getZones().values()) {
                if (band.getId().equals(z.getBand())) {
                    ids.add(z.getId());
                }
            }
            Collections.sort(ids);
            int count = countOf.getOrDefault(band.getId(), DIRECTIONS);
            List<Integer> dirs = new ArrayList<>();
            for (int d = 0; d < count; d++) {
                dirs.add(d);
            }
            GameRandom shuffle = GameRandom.fromSeed(gameSeed ^ ((long) band.getId().hashCode() << 20));
            for (int i = dirs.size() - 1; i > 0; i--) {
                Collections.swap(dirs, i, shuffle.nextInt(i + 1));
            }
            for (int i = 0; i < ids.size(); i++) {
                directionOf.put(ids.get(i), dirs.get(i % count));
            }
        }
        for (ZoneDef z : content.getZones().values()) {
            GameRandom rng = GameRandom.fromSeed(gameSeed ^ ((long) z.getId().hashCode() << 16));
            int direction = directionOf.get(z.getId());
            int count = countOf.getOrDefault(z.getBand(), DIRECTIONS);
            String sectorId = z.getBand() + ":" + direction;
            double ring = 0.25 + rng.nextDouble() * 0.5;
            // Sectors are centered on their direction: keep places well inside the wedge.
            double turn = (direction - 0.3 + rng.nextDouble() * 0.6) / count;
            turn = turn < 0 ? turn + 1 : turn;
            places.put(z.getId(), new Place(sectorId, ring, turn));
            List<String> in = zonesBySector.get(sectorId);
            if (in != null) {
                in.add(z.getId());
            }
        }
    }

    public List<Sector> sectors() {
        return new ArrayList<>(sectors.values());
    }

    public Sector sector(String id) {
        return sectors.get(id);
    }

    public List<String> zonesIn(String sectorId) {
        List<String> in = zonesBySector.get(sectorId);
        return in != null ? Collections.unmodifiableList(in) : List.of();
    }

    /** Null for zones on bands without sectors. */
    public Place placeOf(String zoneId) {
        return places.get(zoneId);
    }
}
