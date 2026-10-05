package com.estatica.data.db;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface ResourceDao {

    @Query("SELECT * FROM resources")
    List<ResourceEntity> all();

    // No "INSERT ... ON CONFLICT DO UPDATE": that needs SQLite 3.24 (Android 11+).
    @Query("INSERT OR IGNORE INTO resources (resource_id, amount) VALUES (:resourceId, 0)")
    void ensure(String resourceId);

    @Query("UPDATE resources SET amount = MAX(0, amount + :delta) WHERE resource_id = :resourceId")
    void increment(String resourceId, int delta);

    @Transaction
    default void add(String resourceId, int delta) {
        ensure(resourceId);
        increment(resourceId, delta);
    }
}
