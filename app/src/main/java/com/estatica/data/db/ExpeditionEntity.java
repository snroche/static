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
}
