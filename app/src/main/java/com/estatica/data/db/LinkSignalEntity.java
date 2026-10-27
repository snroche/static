package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * DB v14: codes exchanged with other bunkers (GDD §12). Incoming ones keep each code from being
 * taken twice and hold wanderers while they wait; outgoing ones can be shown again.
 */
@Entity(tableName = "link_signals")
public class LinkSignalEntity {

    public static final String IN = "IN";
    public static final String OUT = "OUT";
    /** Taken, or sent. */
    public static final String DONE = "DONE";
    /** A wanderer still on the road. */
    public static final String WAITING = "WAITING";

    /** The packet's id. */
    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    public String direction = IN;

    /** LinkPacket.Type name. */
    @NonNull
    public String type = "";

    @NonNull
    public String callsign = "";

    /** The code as made or received. */
    @NonNull
    public String code = "";

    @NonNull
    public String status = DONE;

    /** Where a wanderer waits. */
    @Nullable
    @ColumnInfo(name = "zone_id")
    public String zoneId;

    /** One line for lists ("Drop · 10 scrap"). */
    @NonNull
    public String label = "";

    @ColumnInfo(name = "at_millis")
    public long atMillis;
}
