package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "expeditions", indices = {@Index("scout_id"), @Index("status")})
public class ExpeditionEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "scout_id")
    public String scoutId = "";

    @NonNull
    @ColumnInfo(name = "zone_id")
    public String zoneId = "";

    public long seed;

    @ColumnInfo(name = "started_at_millis")
    public long startedAtMillis;

    @ColumnInfo(name = "rng_state")
    public long rngState;

    @NonNull
    public String status = "";

    @ColumnInfo(name = "segment_index")
    public int segmentIndex;

    @ColumnInfo(name = "cursor_at_millis")
    public long cursorAtMillis;

    @ColumnInfo(name = "pending_since_millis")
    public long pendingSinceMillis;

    @ColumnInfo(name = "chosen_option")
    public int chosenOption;

    @ColumnInfo(name = "chosen_at_millis")
    public long chosenAtMillis;

    /** DB v3: the pending situation includes "come home". */
    @ColumnInfo(name = "retreat_offered", defaultValue = "0")
    public boolean retreatOffered;

    // ---- DB v4: missions and signal quality ----

    @NonNull
    @ColumnInfo(name = "mission_type", defaultValue = "'EXPLORE'")
    public String missionType = "EXPLORE";

    @androidx.annotation.Nullable
    @ColumnInfo(name = "mission_target")
    public String missionTarget;

    @ColumnInfo(name = "signal_noise", defaultValue = "0")
    public double signalNoise;

    @ColumnInfo(name = "order_delay_millis", defaultValue = "0")
    public long orderDelayMillis;

    @ColumnInfo(name = "cache_intel", defaultValue = "0")
    public boolean cacheIntel;

    @ColumnInfo(name = "danger_intel", defaultValue = "0")
    public boolean dangerIntel;

    @ColumnInfo(defaultValue = "0")
    public boolean aborted;

    @ColumnInfo(name = "lost_hints", defaultValue = "0")
    public int lostHints;

    /** DB v11: how much was left to find there (1 = untouched). */
    @ColumnInfo(name = "loot_factor", defaultValue = "1")
    public double lootFactor = 1;

    /** DB v8: second scout on a pair expedition. */
    @androidx.annotation.Nullable
    @ColumnInfo(name = "partner_id")
    public String partnerId;
}
