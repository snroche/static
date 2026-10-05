package com.estatica.data.repo;

import androidx.annotation.WorkerThread;

import com.estatica.data.db.ResourceDao;
import com.estatica.data.db.ResourceEntity;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Bunker resource stock. Blocking: call from the game executor. */
@Singleton
public final class ResourceRepository {

    private final ResourceDao dao;

    @Inject
    public ResourceRepository(ResourceDao dao) {
        this.dao = dao;
    }

    @WorkerThread
    public Map<String, Integer> all() {
        Map<String, Integer> out = new HashMap<>();
        for (ResourceEntity e : dao.all()) {
            out.put(e.resourceId, e.amount);
        }
        return out;
    }

    @WorkerThread
    public void add(Map<String, Integer> delta) {
        for (Map.Entry<String, Integer> e : delta.entrySet()) {
            dao.add(e.getKey(), e.getValue());
        }
    }
}
