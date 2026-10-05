package com.estatica.domain.expedition;

import java.util.Objects;

/**
 * One step of an expedition timeline. Durations are relative: a segment becomes due
 * {@code durationMillis} after the previous one resolved, so time spent waiting on a
 * decision shifts everything after it.
 */
public final class Segment {

    public enum Type { TRANSIT, FIND, SITUATION, RETURN }

    private final Type type;
    private final long durationMillis;
    /** Event id for SITUATION segments, null otherwise. */
    private final String eventId;

    public Segment(Type type, long durationMillis, String eventId) {
        this.type = Objects.requireNonNull(type);
        this.durationMillis = durationMillis;
        this.eventId = eventId;
        if ((type == Type.SITUATION) != (eventId != null)) {
            throw new IllegalArgumentException("eventId is required only for SITUATION segments");
        }
    }

    public Type getType() {
        return type;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public String getEventId() {
        return eventId;
    }
}
