package com.estatica.domain.expedition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.core.GameRandom;
import com.estatica.domain.content.ZoneDef;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TimelineGeneratorTest {

    private static final long MINUTE = 60_000L;

    @Test
    public void sameSeedSameTimeline() {
        ZoneDef zone = TestContent.get().zone("escuela");
        List<Segment> a = TimelineGenerator.generate(zone, 1.0, GameRandom.fromSeed(5));
        List<Segment> b = TimelineGenerator.generate(zone, 1.0, GameRandom.fromSeed(5));
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).getType(), b.get(i).getType());
            assertEquals(a.get(i).getDurationMillis(), b.get(i).getDurationMillis());
            assertEquals(a.get(i).getEventId(), b.get(i).getEventId());
        }
    }

    @Test
    public void shapeAndDurationRespectZoneForManySeeds() {
        for (ZoneDef zone : TestContent.get().getZones().values()) {
            for (long seed = 0; seed < 300; seed++) {
                List<Segment> segments = TimelineGenerator.generate(zone, 1.0, GameRandom.fromSeed(seed));
                assertEquals(Segment.Type.TRANSIT, segments.get(0).getType());
                assertEquals(Segment.Type.RETURN, segments.get(segments.size() - 1).getType());

                long total = TimelineGenerator.plannedDuration(segments);
                assertTrue(total >= zone.getDurationMinutes()[0] * MINUTE);
                assertTrue(total <= zone.getDurationMinutes()[1] * MINUTE);

                Set<String> events = new HashSet<>();
                int situations = 0;
                for (Segment s : segments) {
                    assertTrue(s.getDurationMillis() >= 0);
                    if (s.getType() == Segment.Type.SITUATION) {
                        situations++;
                        assertTrue("events don't repeat", events.add(s.getEventId()));
                        assertTrue(zone.getEvents().contains(s.getEventId()));
                    }
                }
                assertTrue(situations >= zone.getSituations()[0]);
                assertTrue(situations <= zone.getSituations()[1]);
            }
        }
    }

    @Test
    public void fastTraitShortensExpedition() {
        ZoneDef zone = TestContent.get().zone("estacion");
        long normal = TimelineGenerator.plannedDuration(
                TimelineGenerator.generate(zone, 1.0, GameRandom.fromSeed(9)));
        long fast = TimelineGenerator.plannedDuration(
                TimelineGenerator.generate(zone, 0.8, GameRandom.fromSeed(9)));
        assertEquals(Math.round(normal * 0.8), fast, 1);
    }
}
