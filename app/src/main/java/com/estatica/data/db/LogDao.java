package com.estatica.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LogDao {

    /** Returns the generated seq of each row, in the same order. */
    @Insert
    List<Long> insertAll(List<LogLineEntity> lines);

    /** Newest first. */
    @Query("SELECT * FROM log_lines ORDER BY seq DESC LIMIT :limit")
    List<LogLineEntity> latest(int limit);

    /** Newest first, strictly older than {@code beforeSeq}. */
    @Query("SELECT * FROM log_lines WHERE seq < :beforeSeq ORDER BY seq DESC LIMIT :limit")
    List<LogLineEntity> before(long beforeSeq, int limit);
}
