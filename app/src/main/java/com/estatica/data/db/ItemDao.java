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
}
