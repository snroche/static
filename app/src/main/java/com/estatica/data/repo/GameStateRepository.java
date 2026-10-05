package com.estatica.data.repo;

import androidx.annotation.WorkerThread;

import com.estatica.core.GameClock;
import com.estatica.core.GameRandom;
import com.estatica.core.Ids;
import com.estatica.data.db.GameStateDao;
import com.estatica.data.db.GameStateEntity;
import com.estatica.domain.state.GameState;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Global save state. All methods block: call them from the game executor. */
@Singleton
public final class GameStateRepository {

    private final GameStateDao dao;
    private final GameClock clock;

    @Inject
    public GameStateRepository(GameStateDao dao, GameClock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    /** Loads the save, creating a new one (with a fresh seed) on first launch. */
    @WorkerThread
    public GameState loadOrCreate() {
        GameStateEntity e = dao.get();
        boolean created = false;
        if (e == null) {
            long now = clock.nowMillis();
            e = new GameStateEntity();
            e.operatorUuid = Ids.newId();
            e.rngSeed = GameRandom.newSeed();
            e.rngState = e.rngSeed;
            e.createdAtMillis = now;
            e.lastSeenAtMillis = now;
            dao.upsert(e);
            created = true;
        }
        return new GameState(e.operatorUuid, e.rngSeed, e.rngState, e.createdAtMillis,
                e.lastSeenAtMillis, created);
    }

    @WorkerThread
    public void saveRngState(long rngState) {
        dao.updateRngState(rngState);
    }

    @WorkerThread
    public void saveContentVersion(int version) {
        dao.updateContentVersion(version);
    }

    @WorkerThread
    public void touchLastSeen() {
        dao.updateLastSeen(clock.nowMillis());
    }
}
