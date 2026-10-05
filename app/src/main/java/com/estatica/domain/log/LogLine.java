package com.estatica.domain.log;

import com.estatica.core.Ids;

import java.util.Objects;

/**
 * One immutable line of the radio log. {@code seq} is the local, monotonically increasing
 * order (0 until persisted); {@code id} is the global UUID.
 */
public final class LogLine {

    public static final long UNSAVED = 0L;

    private final long seq;
    private final String id;
    private final long timestampMillis;
    private final LogChannel channel;
    private final String speaker;
    private final String text;
    private final String scoutId;

    public LogLine(long seq, String id, long timestampMillis, LogChannel channel,
                   String speaker, String text, String scoutId) {
        this.seq = seq;
        this.id = Objects.requireNonNull(id);
        this.timestampMillis = timestampMillis;
        this.channel = Objects.requireNonNull(channel);
        this.speaker = speaker;
        this.text = Objects.requireNonNull(text);
        this.scoutId = scoutId;
    }

    /** New line not yet persisted. */
    public static LogLine draft(long timestampMillis, LogChannel channel, String speaker, String text) {
        return draft(timestampMillis, channel, speaker, text, null);
    }

    /** New line not yet persisted, about a scout. */
    public static LogLine draft(long timestampMillis, LogChannel channel, String speaker,
                                String text, String scoutId) {
        return new LogLine(UNSAVED, Ids.newId(), timestampMillis, channel, speaker, text, scoutId);
    }

    public LogLine withSeq(long newSeq) {
        return new LogLine(newSeq, id, timestampMillis, channel, speaker, text, scoutId);
    }

    public long getSeq() {
        return seq;
    }

    public String getId() {
        return id;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public LogChannel getChannel() {
        return channel;
    }

    /** Nullable: only RADIO lines have a speaker. */
    public String getSpeaker() {
        return speaker;
    }

    public String getText() {
        return text;
    }

    /** Nullable: the scout this line is about, if any. */
    public String getScoutId() {
        return scoutId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LogLine)) return false;
        LogLine other = (LogLine) o;
        return seq == other.seq
                && timestampMillis == other.timestampMillis
                && id.equals(other.id)
                && channel == other.channel
                && Objects.equals(speaker, other.speaker)
                && text.equals(other.text)
                && Objects.equals(scoutId, other.scoutId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(seq, id, timestampMillis, channel, speaker, text, scoutId);
    }
}
