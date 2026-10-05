package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

/** What a scout carries during an expedition (delivered on return, lost on death). */
@Entity(tableName = "expedition_loot",
        primaryKeys = {"expedition_id", "resource_id"},
        foreignKeys = @ForeignKey(entity = ExpeditionEntity.class, parentColumns = "id",
                childColumns = "expedition_id", onDelete = ForeignKey.CASCADE))
public class ExpeditionLootEntity {

    @NonNull
    @ColumnInfo(name = "expedition_id")
    public String expeditionId = "";

    @NonNull
    @ColumnInfo(name = "resource_id")
    public String resourceId = "";

    public int amount;
}
