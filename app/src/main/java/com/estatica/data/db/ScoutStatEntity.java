package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

@Entity(tableName = "scout_stats",
        primaryKeys = {"scout_id", "stat"},
        foreignKeys = @ForeignKey(entity = ScoutEntity.class, parentColumns = "id",
                childColumns = "scout_id", onDelete = ForeignKey.CASCADE))
public class ScoutStatEntity {

    @NonNull
    @ColumnInfo(name = "scout_id")
    public String scoutId = "";

    /** StatType key. */
    @NonNull
    public String stat = "";

    public int value;

    public int ceiling;

    public int xp;
}
