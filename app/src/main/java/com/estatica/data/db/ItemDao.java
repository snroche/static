package com.estatica.data.db;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface ItemDao {

    @Query("SELECT * FROM items ORDER BY acquired_at_millis")
    List<ItemEntity> all();

    @Upsert
    void upsert(List<ItemEntity> items);

    @Query("SELECT * FROM gear_pieces")
    List<GearEntity> allGear();

    @Upsert
    void upsertGear(List<GearEntity> pieces);

    @Query("DELETE FROM gear_pieces WHERE id IN (:ids)")
    void deleteGear(List<String> ids);
}
