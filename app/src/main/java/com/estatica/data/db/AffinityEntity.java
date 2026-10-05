package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

/** Hidden closeness between two scouts; {@code scout_a} &lt; {@code scout_b}. */
@Entity(tableName = "affinity", primaryKeys = {"scout_a", "scout_b"})
public class AffinityEntity {

    @NonNull
    @ColumnInfo(name = "scout_a")
    public String scoutA = "";

    @NonNull
    @ColumnInfo(name = "scout_b")
    public String scoutB = "";

    public double value;
}
