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
                AffinityEntity.class, ExpeditionItemEntity.class},
        version = 3,
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
}
