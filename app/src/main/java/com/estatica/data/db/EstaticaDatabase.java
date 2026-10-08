package com.estatica.data.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/**
 * Schema history lives in {@code app/schemas/}. Bumping {@code version} requires a
 * {@link androidx.room.migration.Migration} in {@link Migrations} (see docs/VERSIONING.md).
 */
@Database(
        entities = {
                LogLineEntity.class, GameStateEntity.class,
                ScoutEntity.class, ScoutStatEntity.class, ScoutTraitEntity.class,
                MemoryEntity.class,
                ExpeditionEntity.class, ExpeditionSegmentEntity.class, ExpeditionLootEntity.class,
                ResourceEntity.class,
                ScoutFlagEntity.class, ConversationEntity.class, ItemEntity.class,
                AffinityEntity.class, ExpeditionItemEntity.class,
                RadioEntities.GameFlag.class, RadioEntities.KnownZone.class,
                RadioEntities.CaughtSignal.class, RadioEntities.TransmissionRow.class,
                RadioEntities.Intel.class, RadioEntities.RelayRow.class,
                RadioEntities.RescueTarget.class, RadioEntities.Lore.class,
                RadioEntities.Module.class,
                BaseEntities.Power.class, BaseEntities.Value.class, BaseEntities.CraftJobRow.class,
                GearEntity.class},
        version = 10,
        exportSchema = true)
public abstract class EstaticaDatabase extends RoomDatabase {

    public static final String NAME = "estatica.db";

    public abstract LogDao logDao();

    public abstract GameStateDao gameStateDao();

    public abstract ScoutDao scoutDao();

    public abstract MemoryDao memoryDao();

    public abstract ExpeditionDao expeditionDao();

    public abstract ResourceDao resourceDao();

    public abstract ConversationDao conversationDao();

    public abstract ItemDao itemDao();

    public abstract AffinityDao affinityDao();

    public abstract RadioDao radioDao();

    public abstract BaseDao baseDao();
}
