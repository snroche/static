package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** DB v4 tables for the radio, relays, rescues and bunker modules (Phase 3+). */
public final class RadioEntities {

    private RadioEntities() {
    }

    /** One-off global flags (Morse table found, unique transmissions received…). */
    @Entity(tableName = "game_flags")
    public static class GameFlag {
        @PrimaryKey
        @NonNull
        public String flag = "";

        @ColumnInfo(name = "set_at_millis")
        public long setAtMillis;
    }

    @Entity(tableName = "known_zones")
    public static class KnownZone {
        @PrimaryKey
        @NonNull
        @ColumnInfo(name = "zone_id")
        public String zoneId = "";

        @ColumnInfo(name = "discovered_at_millis")
        public long discoveredAtMillis;
    }

    /** A dial signal already caught in its window (key: band@window#slot). */
    @Entity(tableName = "caught_signals")
    public static class CaughtSignal {
        @PrimaryKey
        @NonNull
        @ColumnInfo(name = "signal_key")
        public String signalKey = "";

        @ColumnInfo(name = "caught_at_millis")
        public long caughtAtMillis;
    }

    @Entity(tableName = "transmissions", indices = {@Index("status")})
    public static class TransmissionRow {
        @PrimaryKey
        @NonNull
        public String id = "";

        @NonNull
        @ColumnInfo(name = "source_id")
        public String sourceId = "";

        @NonNull
        public String kind = "";

        @NonNull
        @ColumnInfo(name = "plain_text")
        public String plainText = "";

        public int shift;

        @Nullable
        @ColumnInfo(name = "band_id")
        public String bandId;

        @Nullable
        @ColumnInfo(name = "zone_id")
        public String zoneId;

        @ColumnInfo(name = "received_at_millis")
        public long receivedAtMillis;

        /** DB v12: keyword, or the cipher text for pad and book schemes. */
        @androidx.annotation.Nullable
        @ColumnInfo(name = "cipher_key")
        public String cipherKey;

        @NonNull
        public String status = "";
    }

    /** Decoded intel about a zone, used by the next expedition there. */
    @Entity(tableName = "intel", indices = {@Index("zone_id")})
    public static class Intel {
        @PrimaryKey
        @NonNull
        public String id = "";

        @NonNull
        @ColumnInfo(name = "zone_id")
        public String zoneId = "";

        /** CACHE or DANGER. */
        @NonNull
        public String type = "";

        @ColumnInfo(name = "created_at_millis")
        public long createdAtMillis;
    }

    @Entity(tableName = "relays")
    public static class RelayRow {
        @PrimaryKey
        @NonNull
        @ColumnInfo(name = "zone_id")
        public String zoneId = "";

        public double condition;

        @ColumnInfo(name = "last_decay_at_millis")
        public long lastDecayAtMillis;
    }

    /** A survivor heard on the dial, waiting to be rescued. */
    @Entity(tableName = "rescue_targets")
    public static class RescueTarget {
        @PrimaryKey
        @NonNull
        @ColumnInfo(name = "survivor_id")
        public String survivorId = "";

        @NonNull
        @ColumnInfo(name = "zone_id")
        public String zoneId = "";

        @ColumnInfo(name = "found_at_millis")
        public long foundAtMillis;

        /** DB v6: who they are (JSON); null for hand-made survivors from older saves. */
        @androidx.annotation.Nullable
        public String persona;
    }

    /** Lore fragments for the Diary. */
    @Entity(tableName = "lore")
    public static class Lore {
        @PrimaryKey
        @NonNull
        public String id = "";

        @NonNull
        @ColumnInfo(name = "source_id")
        public String sourceId = "";

        @NonNull
        public String text = "";

        @ColumnInfo(name = "found_at_millis")
        public long foundAtMillis;
    }

    @Entity(tableName = "modules")
    public static class Module {
        @PrimaryKey
        @NonNull
        @ColumnInfo(name = "module_id")
        public String moduleId = "";

        public int level;
    }
}
