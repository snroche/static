package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

@Entity(tableName = "scout_traits",
        primaryKeys = {"scout_id", "trait_id"},
        foreignKeys = @ForeignKey(entity = ScoutEntity.class, parentColumns = "id",
                childColumns = "scout_id", onDelete = ForeignKey.CASCADE))
public class ScoutTraitEntity {

    @NonNull
    @ColumnInfo(name = "scout_id")
    public String scoutId = "";

    @NonNull
    @ColumnInfo(name = "trait_id")
    public String traitId = "";

    public boolean hidden;

    public boolean revealed;

    /** Keeps traits in template order. */
    public int position;
}
