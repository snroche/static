package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Bunker stock of one resource. */
@Entity(tableName = "resources")
public class ResourceEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "resource_id")
    public String resourceId = "";

    public int amount;
}
