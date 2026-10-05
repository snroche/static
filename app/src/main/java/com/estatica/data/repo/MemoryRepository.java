package com.estatica.data.repo;

import androidx.annotation.WorkerThread;

import com.estatica.data.db.MemoryDao;
import com.estatica.data.db.MemoryEntity;
import com.estatica.domain.scouts.Memory;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Scout memories (GDD §5.4). Blocking: call from the game executor. */
@Singleton
public final class MemoryRepository {

    private final MemoryDao dao;

    @Inject
    public MemoryRepository(MemoryDao dao) {
        this.dao = dao;
    }

    /** Oldest first. */
    @WorkerThread
    public List<Memory> forScout(String scoutId) {
        List<Memory> out = new ArrayList<>();
        for (MemoryEntity e : dao.forScout(scoutId)) {
            out.add(new Memory(e.id, e.scoutId, Memory.Type.valueOf(e.type), e.text, e.refId,
                    e.createdAtMillis));
        }
        return out;
    }

    @WorkerThread
    public void insert(List<Memory> memories) {
        if (memories.isEmpty()) {
            return;
        }
        List<MemoryEntity> entities = new ArrayList<>(memories.size());
        for (Memory m : memories) {
            MemoryEntity e = new MemoryEntity();
            e.id = m.getId();
            e.scoutId = m.getScoutId();
            e.type = m.getType().name();
            e.text = m.getText();
            e.refId = m.getRefId();
            e.createdAtMillis = m.getCreatedAtMillis();
            entities.add(e);
        }
        dao.insertAll(entities);
    }
}
