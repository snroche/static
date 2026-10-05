package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Conversations with scouts: pending ones and the history. */
@Entity(tableName = "conversations", indices = {@Index("scout_id"), @Index("status")})
public class ConversationEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "scout_id")
    public String scoutId = "";

    @NonNull
    @ColumnInfo(name = "dialogue_id")
    public String dialogueId = "";

    @ColumnInfo(name = "started_at_millis")
    public long startedAtMillis;

    public boolean initiated;

    @NonNull
    public String status = "";
}
