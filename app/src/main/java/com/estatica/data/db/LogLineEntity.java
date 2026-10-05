package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "log_lines", indices = {@Index(value = "uuid", unique = true)})
public class LogLineEntity {

    @PrimaryKey(autoGenerate = true)
    public long seq;

    @NonNull
    public String uuid = "";

    @ColumnInfo(name = "timestamp_millis")
    public long timestampMillis;

    @NonNull
    public String channel = "";

    @Nullable
    public String speaker;

    @NonNull
    public String text = "";

    /** Added in DB v2. */
    @Nullable
    @ColumnInfo(name = "scout_id")
    public String scoutId;
}
