package com.estatica.domain.expedition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persistent state of one expedition. Advanced only by {@link ExpeditionEngine}, which
 * resolves due segments in order (catch-up). Has its own RNG state, so outcomes don't
 * depend on what else happened in the game.
 */
public final class Expedition {

    public enum Status { ACTIVE, COMPLETED, LOST }

    public static final long NONE = -1L;
    public static final int NO_CHOICE = -1;

    private final String id;
    private final String scoutId;
    private final String zoneId;
    private final long seed;
    private final long startedAtMillis;
    private final List<Segment> segments;
    private long rngState;
    private Status status;
    private int index;
    /** When the current segment started (previous one resolved). */
    private long cursorAtMillis;
    /** When the current situation reached the operator, or NONE. */
    private long pendingSinceMillis;
    private int chosenOption;
    private long chosenAtMillis;
    private final Map<String, Integer> carriedLoot;
    /** Personal objects found (item ids), delivered on return. */
    private final List<String> carriedItems;
    /** The pending situation includes "come home" (GDD §5.5). */
    private boolean retreatOffered;
    private final Mission mission;
    /** Cut short (critical injury, coming home): the mission is not fulfilled. */
    private boolean aborted;
    /** Bit i set: the odds hint of option i was lost in the static. */
    private int lostHints;

    public Expedition(String id, String scoutId, String zoneId, long seed, long startedAtMillis,
                      List<Segment> segments, long rngState, Status status, int index,
                      long cursorAtMillis, long pendingSinceMillis, int chosenOption,
                      long chosenAtMillis, Map<String, Integer> carriedLoot,
                      List<String> carriedItems, boolean retreatOffered, Mission mission,
                      boolean aborted, int lostHints) {
        this.id = Objects.requireNonNull(id);
        this.scoutId = Objects.requireNonNull(scoutId);
        this.zoneId = Objects.requireNonNull(zoneId);
        this.seed = seed;
        this.startedAtMillis = startedAtMillis;
        this.segments = Collections.unmodifiableList(segments);
        this.rngState = rngState;
        this.status = Objects.requireNonNull(status);
        this.index = index;
        this.cursorAtMillis = cursorAtMillis;
        this.pendingSinceMillis = pendingSinceMillis;
        this.chosenOption = chosenOption;
        this.chosenAtMillis = chosenAtMillis;
        this.carriedLoot = new LinkedHashMap<>(carriedLoot);
        this.carriedItems = new java.util.ArrayList<>(carriedItems);
        this.retreatOffered = retreatOffered;
        this.mission = Objects.requireNonNull(mission);
        this.aborted = aborted;
        this.lostHints = lostHints;
    }

    public Mission getMission() {
        return mission;
    }

    public boolean isAborted() {
        return aborted;
    }

    public int getLostHints() {
        return lostHints;
    }

    /** Whether the operator heard the odds hint for this option. */
    public boolean heardHint(int option) {
        return (lostHints & (1 << option)) == 0;
    }

    void markHintLost(int option) {
        lostHints |= 1 << option;
    }

    public List<String> getCarriedItems() {
        return Collections.unmodifiableList(carriedItems);
    }

    void addItem(String itemId) {
        carriedItems.add(itemId);
    }

    void clearItems() {
        carriedItems.clear();
    }

    public boolean isRetreatOffered() {
        return retreatOffered;
    }

    void setRetreatOffered(boolean offered) {
        this.retreatOffered = offered;
    }

    public String getId() {
        return id;
    }

    public String getScoutId() {
        return scoutId;
    }

    public String getZoneId() {
        return zoneId;
    }

    public long getSeed() {
        return seed;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public long getRngState() {
        return rngState;
    }

    void setRngState(long rngState) {
        this.rngState = rngState;
    }

    public Status getStatus() {
        return status;
    }

    void setStatus(Status status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public int getIndex() {
        return index;
    }

    public Segment currentSegment() {
        return segments.get(index);
    }

    public long getCursorAtMillis() {
        return cursorAtMillis;
    }

    public long getPendingSinceMillis() {
        return pendingSinceMillis;
    }

    /** A situation is waiting for the operator. */
    public boolean isAwaitingDecision() {
        return isActive() && pendingSinceMillis != NONE && chosenOption == NO_CHOICE;
    }

    public int getChosenOption() {
        return chosenOption;
    }

    public long getChosenAtMillis() {
        return chosenAtMillis;
    }

    public Map<String, Integer> getCarriedLoot() {
        return Collections.unmodifiableMap(carriedLoot);
    }

    void markPending(long atMillis) {
        pendingSinceMillis = atMillis;
    }

    void setChoice(int option, long atMillis) {
        chosenOption = option;
        chosenAtMillis = atMillis;
    }

    /** Resolves the current segment at {@code atMillis} and moves to the next one. */
    void advance(long atMillis) {
        retreatOffered = false;
        lostHints = 0;
        cursorAtMillis = atMillis;
        pendingSinceMillis = NONE;
        chosenOption = NO_CHOICE;
        chosenAtMillis = NONE;
        index++;
    }

    /** Skips straight to the RETURN segment (e.g. critically injured). */
    void abortToReturn(long atMillis) {
        retreatOffered = false;
        lostHints = 0;
        aborted = true;
        cursorAtMillis = atMillis;
        pendingSinceMillis = NONE;
        chosenOption = NO_CHOICE;
        chosenAtMillis = NONE;
        index = segments.size() - 1;
    }

    void addLoot(String resource, int amount) {
        carriedLoot.merge(resource, amount, Integer::sum);
    }

    void clearLoot() {
        carriedLoot.clear();
    }
}
