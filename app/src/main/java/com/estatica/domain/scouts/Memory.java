package com.estatica.domain.scouts;

import com.estatica.core.Ids;

import java.util.Objects;

/**
 * Something a scout remembers (GDD §5.4). Feeds dialogue, reactions and their last
 * transmission.
 */
public final class Memory {

    public enum Type {
        /** A situation that marked them (from event content). */
        EVENT,
        /** An order from the operator that went notably well or badly. */
        ORDER,
        INJURY,
        /** Something said in a conversation with the operator. */
        TALK,
        /** A gift they received (they carry it). */
        GIFT,
        /** A fallen companion. */
        GRIEF,
        /** Their final transmission. */
        LAST_WORDS
    }

    private final String id;
    private final String scoutId;
    private final Type type;
    private final String text;
    /** Nullable content id it came from (event, zone…). */
    private final String refId;
    private final long createdAtMillis;

    public Memory(String id, String scoutId, Type type, String text, String refId, long createdAtMillis) {
        this.id = Objects.requireNonNull(id);
        this.scoutId = Objects.requireNonNull(scoutId);
        this.type = Objects.requireNonNull(type);
        this.text = Objects.requireNonNull(text);
        this.refId = refId;
        this.createdAtMillis = createdAtMillis;
    }

    public static Memory create(String scoutId, Type type, String text, String refId, long at) {
        return new Memory(Ids.newId(), scoutId, type, text, refId, at);
    }

    public String getId() {
        return id;
    }

    public String getScoutId() {
        return scoutId;
    }

    public Type getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public String getRefId() {
        return refId;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }
}
