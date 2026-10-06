package com.estatica.data.repo;

import androidx.annotation.WorkerThread;

import com.estatica.data.db.BaseDao;
import com.estatica.data.db.BaseEntities;
import com.estatica.domain.bunker.CraftJob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Energy, bunker values and workshop jobs. Blocking: call from the game executor. */
@Singleton
public final class BaseRepository {

    private final BaseDao dao;

    @Inject
    public BaseRepository(BaseDao dao) {
        this.dao = dao;
    }

    @WorkerThread
    public Map<String, Integer> power() {
        Map<String, Integer> out = new HashMap<>();
        for (BaseEntities.Power p : dao.power()) {
            out.put(p.consumerId, p.units);
        }
        return out;
    }

    @WorkerThread
    public void savePower(String consumerId, int units) {
        BaseEntities.Power p = new BaseEntities.Power();
        p.consumerId = consumerId;
        p.units = units;
        dao.upsertPower(p);
    }

    @WorkerThread
    public Map<String, Double> values() {
        Map<String, Double> out = new HashMap<>();
        for (BaseEntities.Value v : dao.values()) {
            out.put(v.key, v.value);
        }
        return out;
    }

    @WorkerThread
    public void saveValue(String key, double value) {
        BaseEntities.Value v = new BaseEntities.Value();
        v.key = key;
        v.value = value;
        dao.upsertValue(v);
    }

    @WorkerThread
    public List<CraftJob> craftJobs() {
        List<CraftJob> out = new ArrayList<>();
        for (BaseEntities.CraftJobRow r : dao.craftJobs()) {
            out.add(new CraftJob(r.id, r.recipeId, r.progressMinutes, r.startedAtMillis));
        }
        return out;
    }

    @WorkerThread
    public void save(CraftJob job) {
        BaseEntities.CraftJobRow r = new BaseEntities.CraftJobRow();
        r.id = job.getId();
        r.recipeId = job.getRecipeId();
        r.progressMinutes = job.getProgressMinutes();
        r.startedAtMillis = job.getStartedAtMillis();
        dao.upsertCraftJob(r);
    }

    @WorkerThread
    public void delete(CraftJob job) {
        dao.deleteCraftJob(job.getId());
    }
}
