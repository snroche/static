package com.estatica.domain.scouts;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A scout's life outside missions (GDD §5.2–5.5): bond with the operator, bunker task,
 * story flags and the timestamps that let all of it be computed by catch-up.
 */
public final class ScoutLife {

    private int bond;
    /** Task id, or null. */
    private String taskId;
    private final Set<String> flags;
    private long lastTalkAtMillis;
    private long lastInitiatedAtMillis;
    private long lastDecayAtMillis;
    /** Fractional task XP not applied yet. */
    private double taskXpCarry;
    /** Fractional task output (workshop) not produced yet. */
    private double taskOutputCarry;
    /** 0 while alive. */
    private long diedAtMillis;
    /** Zone where they died, or null. */
    private String diedZoneId;
    /** Fractional stress recovery not applied yet (so frequent catch-ups lose nothing). */
    private double stressCarry;
    /** Equipped gear piece id, or null (older saves: a gear.json id, converted on load). */
    private String gearId;
    /** Equipped pieces by slot, attached on load. */
    private final java.util.Map<com.estatica.domain.content.GearDef.Slot, com.estatica.domain.bunker.GearPiece> gear =
            new java.util.EnumMap<>(com.estatica.domain.content.GearDef.Slot.class);

    public ScoutLife(int bond, String taskId, Set<String> flags, long lastTalkAtMillis,
                     long lastInitiatedAtMillis, long lastDecayAtMillis, double taskXpCarry,
                     double taskOutputCarry, long diedAtMillis, String diedZoneId,
                     double stressCarry, String gearId) {
        this.bond = bond;
        this.taskId = taskId;
        this.flags = new LinkedHashSet<>(flags);
        this.lastTalkAtMillis = lastTalkAtMillis;
        this.lastInitiatedAtMillis = lastInitiatedAtMillis;
        this.lastDecayAtMillis = lastDecayAtMillis;
        this.taskXpCarry = taskXpCarry;
        this.taskOutputCarry = taskOutputCarry;
        this.diedAtMillis = diedAtMillis;
        this.diedZoneId = diedZoneId;
        this.stressCarry = stressCarry;
        this.gearId = gearId;
    }

    public String getGearId() {
        return gearId;
    }

    /** Legacy column (one piece); slots live on the pieces' location now. */
    public void setGearId(String gearId) {
        this.gearId = gearId;
    }

    /** Everything worn, in slot order. */
    public java.util.List<com.estatica.domain.bunker.GearPiece> getEquipped() {
        return new java.util.ArrayList<>(gear.values());
    }

    public com.estatica.domain.bunker.GearPiece getGear(com.estatica.domain.content.GearDef.Slot slot) {
        return gear.get(slot);
    }

    /** Puts a piece in a slot. @return what was there, or null */
    public com.estatica.domain.bunker.GearPiece wear(com.estatica.domain.content.GearDef.Slot slot, com.estatica.domain.bunker.GearPiece piece) {
        return piece == null ? gear.remove(slot) : gear.put(slot, piece);
    }

    /** Takes everything off. @return what they had on */
    public java.util.List<com.estatica.domain.bunker.GearPiece> takeAllOff() {
        java.util.List<com.estatica.domain.bunker.GearPiece> out = new java.util.ArrayList<>(gear.values());
        gear.clear();
        return out;
    }

    public double getStressCarry() {
        return stressCarry;
    }

    public void setStressCarry(double carry) {
        this.stressCarry = carry;
    }

    /** A new scout: no bond yet, the neglect clock starts now. */
    public static ScoutLife fresh(long nowMillis) {
        return new ScoutLife(0, null, Set.of(), nowMillis, 0, nowMillis, 0, 0, 0, null, 0, null);
    }

    public int getBond() {
        return bond;
    }

    public void setBond(int bond) {
        this.bond = bond;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Set<String> getFlags() {
        return Collections.unmodifiableSet(flags);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    /** @return true if it was new */
    public boolean addFlag(String flag) {
        return flags.add(flag);
    }

    /** @return true if it was there */
    public boolean removeFlag(String flag) {
        return flags.remove(flag);
    }

    public long getLastTalkAtMillis() {
        return lastTalkAtMillis;
    }

    public void setLastTalkAtMillis(long millis) {
        this.lastTalkAtMillis = millis;
    }

    public long getLastInitiatedAtMillis() {
        return lastInitiatedAtMillis;
    }

    public void setLastInitiatedAtMillis(long millis) {
        this.lastInitiatedAtMillis = millis;
    }

    public long getLastDecayAtMillis() {
        return lastDecayAtMillis;
    }

    public void setLastDecayAtMillis(long millis) {
        this.lastDecayAtMillis = millis;
    }

    public double getTaskXpCarry() {
        return taskXpCarry;
    }

    public void setTaskXpCarry(double carry) {
        this.taskXpCarry = carry;
    }

    public double getTaskOutputCarry() {
        return taskOutputCarry;
    }

    public void setTaskOutputCarry(double carry) {
        this.taskOutputCarry = carry;
    }

    public long getDiedAtMillis() {
        return diedAtMillis;
    }

    public String getDiedZoneId() {
        return diedZoneId;
    }

    public void markDied(long atMillis, String zoneId) {
        this.diedAtMillis = atMillis;
        this.diedZoneId = zoneId;
        this.taskId = null;
    }
}
