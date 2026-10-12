package com.estatica.domain.expedition;

import com.estatica.core.GameRandom;
import com.estatica.domain.content.ZoneDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds an expedition timeline from a seed: out-bound transit, a shuffled middle of
 * finds, situations and transits, and the return. Same seed + inputs → same timeline.
 */
public final class TimelineGenerator {

    private static final long MINUTE = 60_000L;
    private static final long MIN_TOTAL_MILLIS = 30_000L;

    private TimelineGenerator() {
    }

    /**
     * @param durationMultiplier from the scout's traits (e.g. Corredor &lt; 1)
     */
    public static List<Segment> generate(ZoneDef zone, double durationMultiplier, GameRandom rng) {
        return generate(zone, durationMultiplier, rng, id -> true);
    }

    /** @param eventFits filters the zone's situations by who is going (event requirements) */
    public static List<Segment> generate(ZoneDef zone, double durationMultiplier, GameRandom rng,
                                         java.util.function.Predicate<String> eventFits) {
        return generate(zone, durationMultiplier, rng, zone.getEvents(), eventFits, id -> false);
    }

    /**
     * @param events  candidate situations (the zone's own plus common ones for its band)
     * @param recent  situations seen lately: they only come up once the fresh ones run out
     */
    public static List<Segment> generate(ZoneDef zone, double durationMultiplier, GameRandom rng,
                                         List<String> events, java.util.function.Predicate<String> eventFits,
                                         java.util.function.Predicate<String> recent) {
        int[] range = zone.getDurationMinutes();
        long total = Math.max(MIN_TOTAL_MILLIS, Math.round(
                rng.nextIntInclusive(range[0], range[1]) * MINUTE * durationMultiplier));

        List<String> eventPool = new ArrayList<>();
        for (String id : events) {
            if (eventFits.test(id)) {
                eventPool.add(id);
            }
        }
        shuffle(eventPool, rng);
        // Stable: fresh ones first, each group still shuffled.
        List<String> fresh = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (String id : eventPool) {
            (recent.test(id) ? stale : fresh).add(id);
        }
        eventPool.clear();
        eventPool.addAll(fresh);
        eventPool.addAll(stale);
        int situations = Math.min(eventPool.size(),
                rng.nextIntInclusive(zone.getSituations()[0], zone.getSituations()[1]));
        int finds = rng.nextIntInclusive(zone.getFinds()[0], zone.getFinds()[1]);
        int transits = 1 + rng.nextInt(2);

        List<Segment.Type> middle = new ArrayList<>();
        for (int i = 0; i < situations; i++) middle.add(Segment.Type.SITUATION);
        for (int i = 0; i < finds; i++) middle.add(Segment.Type.FIND);
        for (int i = 0; i < transits; i++) middle.add(Segment.Type.TRANSIT);
        shuffle(middle, rng);

        List<Segment.Type> types = new ArrayList<>(middle.size() + 2);
        types.add(Segment.Type.TRANSIT);
        types.addAll(middle);
        types.add(Segment.Type.RETURN);

        int[] weights = new int[types.size()];
        int weightSum = 0;
        for (int i = 0; i < weights.length; i++) {
            weights[i] = 1 + rng.nextInt(3);
            weightSum += weights[i];
        }

        List<Segment> segments = new ArrayList<>(types.size());
        long assigned = 0;
        int eventCursor = 0;
        for (int i = 0; i < types.size(); i++) {
            long duration = i == types.size() - 1
                    ? total - assigned
                    : total * weights[i] / weightSum;
            assigned += duration;
            Segment.Type type = types.get(i);
            String eventId = type == Segment.Type.SITUATION ? eventPool.get(eventCursor++) : null;
            segments.add(new Segment(type, duration, eventId));
        }
        return segments;
    }

    /** Total planned duration, ignoring time spent waiting on decisions. */
    public static long plannedDuration(List<Segment> segments) {
        long total = 0;
        for (Segment segment : segments) {
            total += segment.getDurationMillis();
        }
        return total;
    }

    private static <T> void shuffle(List<T> list, GameRandom rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }
}
