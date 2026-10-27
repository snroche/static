package com.estatica.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface ExpeditionDao {

    @Query("SELECT * FROM expeditions WHERE status = 'ACTIVE' ORDER BY started_at_millis")
    List<ExpeditionEntity> active();

    @Query("SELECT * FROM expeditions WHERE id IN (:ids)")
    List<ExpeditionEntity> byIds(List<String> ids);

    @Query("SELECT * FROM expedition_segments WHERE expedition_id = :expeditionId ORDER BY idx")
    List<ExpeditionSegmentEntity> segments(String expeditionId);

    @Query("SELECT * FROM expedition_loot WHERE expedition_id = :expeditionId")
    List<ExpeditionLootEntity> loot(String expeditionId);

    @Upsert
    void upsert(ExpeditionEntity expedition);

    @Insert
    void insertSegments(List<ExpeditionSegmentEntity> segments);

    @Query("DELETE FROM expedition_segments WHERE expedition_id = :expeditionId")
    void clearSegments(String expeditionId);

    @Query("DELETE FROM expedition_loot WHERE expedition_id = :expeditionId")
    void clearLoot(String expeditionId);

    @Insert
    void insertLoot(List<ExpeditionLootEntity> loot);

    @Query("SELECT * FROM expedition_items WHERE expedition_id = :expeditionId ORDER BY idx")
    List<ExpeditionItemEntity> items(String expeditionId);

    @Query("DELETE FROM expedition_items WHERE expedition_id = :expeditionId")
    void clearItems(String expeditionId);

    @Insert
    void insertItems(List<ExpeditionItemEntity> items);
}
