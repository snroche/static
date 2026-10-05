package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One personal object. {@code location} is "bunker", "scout:&lt;id&gt;" (a gift they carry)
 * or "zone:&lt;id&gt;" (lost where its owner died, recoverable).
 */
@Entity(tableName = "items", indices = {@Index("location")})
public class ItemEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "item_id")
    public String itemId = "";

    @NonNull
    public String location = "";

    /** Scout it belonged to before being lost, if any. */
    @Nullable
    @ColumnInfo(name = "original_owner_id")
    public String originalOwnerId;

    @ColumnInfo(name = "acquired_at_millis")
    public long acquiredAtMillis;
}
