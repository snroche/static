package com.estatica.data.db;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface ConversationDao {

    @Query("SELECT * FROM conversations WHERE status = 'PENDING' ORDER BY started_at_millis")
    List<ConversationEntity> pending();

    @Upsert
    void upsert(ConversationEntity conversation);
}
