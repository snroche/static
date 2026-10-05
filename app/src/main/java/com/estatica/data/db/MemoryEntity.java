package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A scout's memory (GDD §5.4). Kept even after death: it feeds the Diary. */
@Entity(tableName = "memories", indices = {@Index("scout_id")})
public class MemoryEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "scout_id")
    public String scoutId = "";

    @NonNull
    public String type = "";

    @NonNull
    public String text = "";

    @Nullable
    @ColumnInfo(name = "ref_id")
    public String refId;

    @ColumnInfo(name = "created_at_millis")
    public long createdAtMillis;
}
