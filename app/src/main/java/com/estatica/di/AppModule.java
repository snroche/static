package com.estatica.di;

import android.content.Context;

import androidx.room.Room;

import com.estatica.BuildConfig;
import com.estatica.core.GameClock;
import com.estatica.core.SystemGameClock;
import com.estatica.data.db.AffinityDao;
import com.estatica.data.db.BaseDao;
import com.estatica.data.db.ConversationDao;
import com.estatica.data.db.EstaticaDatabase;
import com.estatica.data.db.ItemDao;
import com.estatica.data.db.ExpeditionDao;
import com.estatica.data.db.GameStateDao;
import com.estatica.data.db.LogDao;
import com.estatica.data.db.MemoryDao;
import com.estatica.data.db.RadioDao;
import com.estatica.data.db.Migrations;
import com.estatica.data.db.ResourceDao;
import com.estatica.data.db.ScoutDao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public final class AppModule {

    public static final String APP_VERSION = "appVersion";

    private AppModule() {
    }

    @Provides
    @Singleton
    static EstaticaDatabase provideDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(context, EstaticaDatabase.class, EstaticaDatabase.NAME)
                .addMigrations(Migrations.ALL)
                .build();
    }

    @Provides
    static LogDao provideLogDao(EstaticaDatabase db) {
        return db.logDao();
    }

    @Provides
    static GameStateDao provideGameStateDao(EstaticaDatabase db) {
        return db.gameStateDao();
    }

    @Provides
    static ScoutDao provideScoutDao(EstaticaDatabase db) {
        return db.scoutDao();
    }

    @Provides
    static MemoryDao provideMemoryDao(EstaticaDatabase db) {
        return db.memoryDao();
    }

    @Provides
    static ExpeditionDao provideExpeditionDao(EstaticaDatabase db) {
        return db.expeditionDao();
    }

    @Provides
    static ResourceDao provideResourceDao(EstaticaDatabase db) {
        return db.resourceDao();
    }

    @Provides
    static ConversationDao provideConversationDao(EstaticaDatabase db) {
        return db.conversationDao();
    }

    @Provides
    static ItemDao provideItemDao(EstaticaDatabase db) {
        return db.itemDao();
    }

    @Provides
    static AffinityDao provideAffinityDao(EstaticaDatabase db) {
        return db.affinityDao();
    }

    @Provides
    static RadioDao provideRadioDao(EstaticaDatabase db) {
        return db.radioDao();
    }

    @Provides
    static BaseDao provideBaseDao(EstaticaDatabase db) {
        return db.baseDao();
    }

    @Provides
    @Singleton
    static GameClock provideClock() {
        return new SystemGameClock();
    }

    /**
     * The game executor. Single-threaded on purpose: all game logic and Room access run
     * here, in order, so game state needs no locking.
     */
    @Provides
    @Singleton
    static ExecutorService provideGameExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "estatica-game");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    @Provides
    @Named(APP_VERSION)
    static String provideAppVersion() {
        return BuildConfig.VERSION_NAME;
    }
}
