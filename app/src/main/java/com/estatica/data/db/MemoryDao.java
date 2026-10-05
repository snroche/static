package com.estatica.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MemoryDao {

    @Insert
    void insertAll(List<MemoryEntity> memories);

    /** Oldest first. */
    @Query("SELECT * FROM memories WHERE scout_id = :scoutId ORDER BY created_at_millis, rowid")
    List<MemoryEntity> forScout(String scoutId);
}
