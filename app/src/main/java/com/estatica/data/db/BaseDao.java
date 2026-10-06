package com.estatica.data.db;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface BaseDao {

    @Query("SELECT * FROM power")
    List<BaseEntities.Power> power();

    @Upsert
    void upsertPower(BaseEntities.Power power);

    @Query("SELECT * FROM bunker_state")
    List<BaseEntities.Value> values();

    @Upsert
    void upsertValue(BaseEntities.Value value);

    @Query("SELECT * FROM craft_jobs ORDER BY started_at_millis")
    List<BaseEntities.CraftJobRow> craftJobs();

    @Upsert
    void upsertCraftJob(BaseEntities.CraftJobRow job);

    @Query("DELETE FROM craft_jobs WHERE id = :id")
    void deleteCraftJob(String id);
}
