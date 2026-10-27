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
    /** Where it shows: {@link #SYSTEM_THREAD}, an expedition ({@code exp:id}) or a chat ({@code chat:scoutId}). */
    private final String thread;
    /** Sound to play when it shows up ({@link Cue}), or null. Not saved: only for the moment it arrives. */
    private String cue;

    /** Sounds a line can carry. */
    public static final class Cue {
        /** A rare find, a project finished: something to be glad about. */
        public static final String FIND = "FIND";
        /** A gift they loved, a favor returned. */
        public static final String WARM = "WARM";
        /** Closer than before: a bond level. */
        public static final String BOND = "BOND";
        /** The storm right on top of them. */
        public static final String THUNDER = "THUNDER";
        /** Something out there that isn't like the rest. */
        public static final String EERIE = "EERIE";

        private Cue() {
        }
    }

    public static final String SYSTEM_THREAD = "system";
    public static final String EXPEDITION_PREFIX = "exp:";
    public static final String CHAT_PREFIX = "chat:";

    /** Departures, returns and calls for orders: what EXPEDITIONS → ALL shows. */
    public static final String SUMMARY_SUFFIX = "|sum";

    public static String summaryThread(String expeditionId) {
        return EXPEDITION_PREFIX + expeditionId + SUMMARY_SUFFIX;
    }

    public static boolean isSummary(String thread) {
        return thread.startsWith(EXPEDITION_PREFIX) && thread.endsWith(SUMMARY_SUFFIX);
    }

    public static String expeditionThread(String expeditionId) {
        return EXPEDITION_PREFIX + expeditionId;
    }

    public static String chatThread(String scoutId) {
        return CHAT_PREFIX + scoutId;
    }

    public LogLine(long seq, String id, long timestampMillis, LogChannel channel,
                   String speaker, String text, String scoutId) {
        this(seq, id, timestampMillis, channel, speaker, text, scoutId, SYSTEM_THREAD);
    }

    public LogLine(long seq, String id, long timestampMillis, LogChannel channel,
                   String speaker, String text, String scoutId, String thread) {
        this.thread = thread != null ? thread : SYSTEM_THREAD;
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
        return new LogLine(newSeq, id, timestampMillis, channel, speaker, text, scoutId, thread).cued(cue);
    }

    public LogLine withThread(String newThread) {
        return new LogLine(seq, id, timestampMillis, channel, speaker, text, scoutId, newThread).cued(cue);
    }

    /** The same line, playing {@code cue} when it shows up. */
    public LogLine withCue(String cue) {
        return new LogLine(seq, id, timestampMillis, channel, speaker, text, scoutId, thread).cued(cue);
    }

    private LogLine cued(String cue) {
        this.cue = cue;
        return this;
    }

    /** Nullable: sound for when it shows up. */
    public String getCue() {
        return cue;
    }

    public String getThread() {
        return thread;
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
