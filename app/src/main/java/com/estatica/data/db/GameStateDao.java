package com.estatica.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface GameStateDao {

    @Query("SELECT * FROM game_state WHERE id = 1")
    GameStateEntity get();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(GameStateEntity state);

    @Query("UPDATE game_state SET rng_state = :rngState WHERE id = 1")
    void updateRngState(long rngState);

    @Query("UPDATE game_state SET last_seen_at_millis = :millis WHERE id = 1")
    void updateLastSeen(long millis);

    @Query("UPDATE game_state SET content_version = :version WHERE id = 1")
    void updateContentVersion(int version);
}
