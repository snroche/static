package com.estatica.domain.bunker;

import com.estatica.core.Ids;

import java.util.Objects;

/**
 * One piece of equipment: what it is (gear.json), how good (rarity), how worn (condition)
 * and where it is. Same places as personal items: the bunker, carried by a scout, or lost in
 * the zone where its owner died (recoverable, GDD §5.5).
 */
public final class GearPiece {

    public enum Rarity {
        COMMON(1.0), UNCOMMON(1.5), RARE(2.0);

        /** How strong its effect is compared to a common one. */
        public final double power;

        Rarity(double power) {
            this.power = power;
        }

        public static Rarity fromKey(String key) {
            for (Rarity r : values()) {
                if (r.name().equalsIgnoreCase(key)) {
                    return r;
                }
            }
            return COMMON;
        }
    }

    public static final int MAX_CONDITION = 100;
    public static final String BUNKER = "bunker";
    /** Used up (a first-aid kit that did its job). Deleted on save. */
    public static final String GONE = "gone";
    private static final String SCOUT = "scout:";
    private static final String ZONE = "zone:";

    private final String id;
    private final String gearId;
    private final Rarity rarity;
    private int condition;
    private String location;
    /** Scout it belonged to before being lost, or null. */
    private String originalOwnerId;

    public GearPiece(String id, String gearId, Rarity rarity, int condition, String location, String originalOwnerId) {
        this.id = Objects.requireNonNull(id);
        this.gearId = Objects.requireNonNull(gearId);
        this.rarity = Objects.requireNonNull(rarity);
        this.condition = Math.max(0, Math.min(MAX_CONDITION, condition));
        this.location = Objects.requireNonNull(location);
        this.originalOwnerId = originalOwnerId;
    }

    public static GearPiece fresh(String gearId, Rarity rarity) {
        return new GearPiece(Ids.newId(), gearId, rarity, MAX_CONDITION, BUNKER, null);
    }

    public static String carriedBy(String scoutId) {
        return SCOUT + scoutId;
    }

    public static String lostIn(String zoneId) {
        return ZONE + zoneId;
    }

    public String getId() {
        return id;
    }

    /** gear.json id (also the old resource id). */
    public String getGearId() {
        return gearId;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public int getCondition() {
        return condition;
    }

    public boolean isBroken() {
        return condition <= 0;
    }

    /** @return the condition left */
    public int wear(int amount) {
        condition = Math.max(0, condition - Math.max(0, amount));
        return condition;
    }

    public void repair() {
        condition = MAX_CONDITION;
    }

    /** Some work on it, short of a full repair. */
    public void mend(int amount) {
        condition = Math.min(MAX_CONDITION, condition + Math.max(0, amount));
    }

    public String getLocation() {
        return location;
    }

    public void moveTo(String location) {
        this.location = Objects.requireNonNull(location);
    }

    public boolean isInBunker() {
        return BUNKER.equals(location);
    }

    public boolean isGone() {
        return GONE.equals(location);
    }

    public String getOriginalOwnerId() {
        return originalOwnerId;
    }

    public void setOriginalOwnerId(String scoutId) {
        this.originalOwnerId = scoutId;
    }
}
