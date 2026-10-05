package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

/** Story flags of a scout: dialogues had, discovered tastes, specialization… */
@Entity(tableName = "scout_flags",
        primaryKeys = {"scout_id", "flag"},
        foreignKeys = @ForeignKey(entity = ScoutEntity.class, parentColumns = "id",
                childColumns = "scout_id", onDelete = ForeignKey.CASCADE))
public class ScoutFlagEntity {

    @NonNull
    @ColumnInfo(name = "scout_id")
    public String scoutId = "";

    @NonNull
    public String flag = "";
}
