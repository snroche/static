package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Single-row table with the global save state. */
@Entity(tableName = "game_state")
public class GameStateEntity {

    public static final int SINGLETON_ID = 1;

    @PrimaryKey
    public int id = SINGLETON_ID;

    @NonNull
    @ColumnInfo(name = "operator_uuid")
    public String operatorUuid = "";

    @ColumnInfo(name = "rng_seed")
    public long rngSeed;

    @ColumnInfo(name = "rng_state")
    public long rngState;

    @ColumnInfo(name = "created_at_millis")
    public long createdAtMillis;

    @ColumnInfo(name = "last_seen_at_millis")
    public long lastSeenAtMillis;

    /** balance.json contentVersion this save was last written with. Added in DB v2. */
    @ColumnInfo(name = "content_version", defaultValue = "1")
    public int contentVersion = 1;
}
