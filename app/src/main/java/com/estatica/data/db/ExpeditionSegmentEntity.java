package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

/** Generated once at dispatch; never updated. */
@Entity(tableName = "expedition_segments",
        primaryKeys = {"expedition_id", "idx"},
        foreignKeys = @ForeignKey(entity = ExpeditionEntity.class, parentColumns = "id",
                childColumns = "expedition_id", onDelete = ForeignKey.CASCADE))
public class ExpeditionSegmentEntity {

    @NonNull
    @ColumnInfo(name = "expedition_id")
    public String expeditionId = "";

    public int idx;

    @NonNull
    public String type = "";

    @ColumnInfo(name = "duration_millis")
    public long durationMillis;

    @Nullable
    @ColumnInfo(name = "event_id")
    public String eventId;
}
