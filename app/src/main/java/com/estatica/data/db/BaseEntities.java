package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** DB v5 tables for energy, bunker events and the workshop (Phase 4). */
public final class BaseEntities {

    private BaseEntities() {
    }

    /** Watts the operator assigned to a consumer. */
    @Entity(tableName = "power")
    public static class Power {
        @PrimaryKey
        @NonNull
        @ColumnInfo(name = "consumer_id")
        public String consumerId = "";

        public int units;
    }

    /** Numeric bunker values: energy reserve, last time events were checked… */
    @Entity(tableName = "bunker_state")
    public static class Value {
        @PrimaryKey
        @NonNull
        public String key = "";

        public double value;
    }

    @Entity(tableName = "craft_jobs")
    public static class CraftJobRow {
        @PrimaryKey
        @NonNull
        public String id = "";

        @NonNull
        @ColumnInfo(name = "recipe_id")
        public String recipeId = "";

        @ColumnInfo(name = "progress_minutes")
        public double progressMinutes;

        @ColumnInfo(name = "started_at_millis")
        public long startedAtMillis;
    }
}
