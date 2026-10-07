package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * DB v7: one piece of equipment. {@code location} is "bunker", "scout:&lt;id&gt;" (equipped)
 * or "zone:&lt;id&gt;" (lost where its owner died, recoverable).
 */
@Entity(tableName = "gear_pieces")
public class GearEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "gear_id")
    public String gearId = "";

    @NonNull
    public String rarity = "";

    public int condition;

    @NonNull
    public String location = "";

    @Nullable
    @ColumnInfo(name = "original_owner_id")
    public String originalOwnerId;
}
