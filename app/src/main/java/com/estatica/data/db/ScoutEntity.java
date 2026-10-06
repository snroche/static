package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scouts")
public class ScoutEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @Nullable
    @ColumnInfo(name = "template_id")
    public String templateId;

    @NonNull
    public String name = "";

    @NonNull
    @ColumnInfo(name = "voice_id")
    public String voiceId = "";

    public int stress;

    public int trust;

    @NonNull
    public String injury = "";

    @NonNull
    public String status = "";

    @ColumnInfo(name = "recovered_at_millis")
    public long recoveredAtMillis;

    @ColumnInfo(name = "heal_progress_hours")
    public double healProgressHours;

    /** Roster order (recruitment order). */
    @ColumnInfo(name = "sort_order")
    public int sortOrder;

    // ---- DB v3: life in the bunker ----

    @ColumnInfo(defaultValue = "0")
    public int bond;

    @Nullable
    @ColumnInfo(name = "task_id")
    public String taskId;

    @ColumnInfo(name = "last_talk_at_millis", defaultValue = "0")
    public long lastTalkAtMillis;

    @ColumnInfo(name = "last_initiated_at_millis", defaultValue = "0")
    public long lastInitiatedAtMillis;

    @ColumnInfo(name = "last_decay_at_millis", defaultValue = "0")
    public long lastDecayAtMillis;

    @ColumnInfo(name = "task_xp_carry", defaultValue = "0")
    public double taskXpCarry;

    @ColumnInfo(name = "task_output_carry", defaultValue = "0")
    public double taskOutputCarry;

    @ColumnInfo(name = "died_at_millis", defaultValue = "0")
    public long diedAtMillis;

    @Nullable
    @ColumnInfo(name = "died_zone_id")
    public String diedZoneId;

    @ColumnInfo(name = "stress_carry", defaultValue = "0")
    public double stressCarry;

    /** DB v5: equipped gear (resource id). */
    @Nullable
    @ColumnInfo(name = "gear_id")
    public String gearId;

    /** DB v6: generated person (JSON of a ScoutTemplate); null for authored scouts. */
    @Nullable
    public String persona;
}
