package com.estatica.domain.bunker;

import com.estatica.core.Ids;

import java.util.Objects;

/**
 * One personal object and where it is: in the bunker inventory, carried by a scout (a gift),
 * or lost in a zone where its owner died (GDD §5.5, recoverable).
 */
public final class ItemInstance {

    public static final String BUNKER = "bunker";
    private static final String SCOUT = "scout:";
    private static final String ZONE = "zone:";

    private final String id;
    private final String itemId;
    private String location;
    /** Scout it belonged to before being lost, or null. */
    private String originalOwnerId;
    private final long acquiredAtMillis;

    public ItemInstance(String id, String itemId, String location, String originalOwnerId,
                        long acquiredAtMillis) {
        this.id = Objects.requireNonNull(id);
        this.itemId = Objects.requireNonNull(itemId);
        this.location = Objects.requireNonNull(location);
        this.originalOwnerId = originalOwnerId;
        this.acquiredAtMillis = acquiredAtMillis;
    }

    public static ItemInstance found(String itemId, long now) {
        return new ItemInstance(Ids.newId(), itemId, BUNKER, null, now);
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

    public String getItemId() {
        return itemId;
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

    public String getOriginalOwnerId() {
        return originalOwnerId;
    }

    public void setOriginalOwnerId(String scoutId) {
        this.originalOwnerId = scoutId;
    }

    public long getAcquiredAtMillis() {
        return acquiredAtMillis;
    }
}
