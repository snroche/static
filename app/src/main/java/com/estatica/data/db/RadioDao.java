package com.estatica.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface RadioDao {

    @Query("SELECT * FROM game_flags")
    List<RadioEntities.GameFlag> flags();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertFlag(RadioEntities.GameFlag flag);

    @Query("SELECT * FROM known_zones")
    List<RadioEntities.KnownZone> knownZones();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertKnownZone(RadioEntities.KnownZone zone);

    @Query("SELECT signal_key FROM caught_signals")
    List<String> caughtSignals();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertCaughtSignal(RadioEntities.CaughtSignal signal);

    @Query("SELECT * FROM transmissions WHERE status = 'PENDING' ORDER BY received_at_millis")
    List<RadioEntities.TransmissionRow> pendingTransmissions();

    @Query("SELECT DISTINCT source_id FROM transmissions")
    List<String> receivedTransmissionSources();

    @Upsert
    void upsertTransmission(RadioEntities.TransmissionRow transmission);

    @Query("SELECT * FROM intel")
    List<RadioEntities.Intel> intel();

    @Insert
    void insertIntel(RadioEntities.Intel intel);

    @Query("DELETE FROM intel WHERE id = :id")
    void deleteIntel(String id);

    @Query("SELECT * FROM relays")
    List<RadioEntities.RelayRow> relays();

    @Upsert
    void upsertRelay(RadioEntities.RelayRow relay);

    @Query("SELECT * FROM rescue_targets")
    List<RadioEntities.RescueTarget> rescueTargets();

    @Upsert
    void upsertRescueTarget(RadioEntities.RescueTarget target);

    @Query("DELETE FROM rescue_targets WHERE survivor_id = :survivorId")
    void deleteRescueTarget(String survivorId);

    @Query("SELECT * FROM lore ORDER BY found_at_millis")
    List<RadioEntities.Lore> lore();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertLore(RadioEntities.Lore lore);

    @Query("SELECT * FROM modules")
    List<RadioEntities.Module> modules();

    @Upsert
    void upsertModule(RadioEntities.Module module);
}
