package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

/** Personal objects found during an expedition, in order. */
@Entity(tableName = "expedition_items",
        primaryKeys = {"expedition_id", "idx"},
        foreignKeys = @ForeignKey(entity = ExpeditionEntity.class, parentColumns = "id",
                childColumns = "expedition_id", onDelete = ForeignKey.CASCADE))
public class ExpeditionItemEntity {

    @NonNull
    @ColumnInfo(name = "expedition_id")
    public String expeditionId = "";

    public int idx;

    @NonNull
    @ColumnInfo(name = "item_id")
    public String itemId = "";
}
