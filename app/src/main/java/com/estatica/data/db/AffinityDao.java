package com.estatica.data.db;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface AffinityDao {

    @Query("SELECT * FROM affinity")
    List<AffinityEntity> all();

    @Upsert
    void upsert(List<AffinityEntity> values);
}
