package com.estatica.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface ScoutDao {

    @Query("SELECT * FROM scouts ORDER BY sort_order")
    List<ScoutEntity> all();

    @Query("SELECT * FROM scout_stats")
    List<ScoutStatEntity> allStats();

    @Query("SELECT * FROM scout_traits ORDER BY position")
    List<ScoutTraitEntity> allTraits();

    @Query("SELECT * FROM scout_flags")
    List<ScoutFlagEntity> allFlags();

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM scouts")
    int maxSortOrder();

    @Insert
    void insert(ScoutEntity scout);

    @Query("UPDATE scouts SET stress = :stress, trust = :trust, injury = :injury, status = :status, "
            + "recovered_at_millis = :recoveredAt, heal_progress_hours = :healProgress WHERE id = :id")
    void updateState(String id, int stress, int trust, String injury, String status,
                     long recoveredAt, double healProgress);

    @Query("UPDATE scouts SET bond = :bond, task_id = :taskId, last_talk_at_millis = :lastTalk, "
            + "last_initiated_at_millis = :lastInitiated, last_decay_at_millis = :lastDecay, "
            + "task_xp_carry = :xpCarry, task_output_carry = :outputCarry, "
            + "died_at_millis = :diedAt, died_zone_id = :diedZone, stress_carry = :stressCarry "
            + "WHERE id = :id")
    void updateLife(String id, int bond, String taskId, long lastTalk, long lastInitiated,
                    long lastDecay, double xpCarry, double outputCarry, long diedAt, String diedZone,
                    double stressCarry);

    @Upsert
    void upsertStats(List<ScoutStatEntity> stats);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertTraits(List<ScoutTraitEntity> traits);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertFlags(List<ScoutFlagEntity> flags);
}
