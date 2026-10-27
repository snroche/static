package com.estatica.data.db;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface LinkDao {

    @Query("SELECT * FROM link_signals ORDER BY at_millis")
    List<LinkSignalEntity> all();

    @Upsert
    void upsert(LinkSignalEntity signal);

    @Query("DELETE FROM link_signals WHERE id = :id")
    void delete(String id);
}
