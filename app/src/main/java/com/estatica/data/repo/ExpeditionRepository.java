package com.estatica.data.repo;

import androidx.annotation.WorkerThread;

import com.estatica.data.db.ExpeditionDao;
import com.estatica.data.db.ExpeditionEntity;
import com.estatica.data.db.ExpeditionItemEntity;
import com.estatica.data.db.ExpeditionLootEntity;
import com.estatica.data.db.ExpeditionSegmentEntity;
import com.estatica.domain.expedition.Expedition;
import com.estatica.domain.expedition.Mission;
import com.estatica.domain.expedition.Segment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Expedition persistence. Blocking: call from the game executor, inside a transaction when saving. */
@Singleton
public final class ExpeditionRepository {

    private final ExpeditionDao dao;

    @Inject
    public ExpeditionRepository(ExpeditionDao dao) {
        this.dao = dao;
    }

    /** Who went on each of these expeditions: id to {lead, partner, third (both nullable)}. */
    @WorkerThread
    public Map<String, String[]> teams(List<String> ids) {
        Map<String, String[]> out = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            for (ExpeditionEntity e : dao.byIds(ids)) {
                out.put(e.id, new String[]{e.scoutId, e.partnerId, e.thirdId});
            }
        }
        return out;
    }

    @WorkerThread
    public List<Expedition> loadActive() {
        List<Expedition> out = new ArrayList<>();
        for (ExpeditionEntity e : dao.active()) {
            List<Segment> segments = new ArrayList<>();
            for (ExpeditionSegmentEntity s : dao.segments(e.id)) {
                segments.add(new Segment(Segment.Type.valueOf(s.type), s.durationMillis, s.eventId));
            }
            Map<String, Integer> loot = new LinkedHashMap<>();
            for (ExpeditionLootEntity l : dao.loot(e.id)) {
                loot.put(l.resourceId, l.amount);
            }
            List<String> items = new ArrayList<>();
            for (ExpeditionItemEntity i : dao.items(e.id)) {
                items.add(i.itemId);
            }
            Expedition expedition = new Expedition(e.id, e.scoutId, e.zoneId, e.seed, e.startedAtMillis, segments,
                    e.rngState, Expedition.Status.valueOf(e.status), e.segmentIndex,
                    e.cursorAtMillis, e.pendingSinceMillis, e.chosenOption, e.chosenAtMillis, loot,
                    items, e.retreatOffered,
                    new Mission(Mission.Type.valueOf(e.missionType), e.missionTarget, e.signalNoise,
                            e.orderDelayMillis, e.cacheIntel, e.dangerIntel, e.lootFactor),
                    e.aborted, e.lostHints);
            expedition.setPartnerId(e.partnerId);
            expedition.setThirdId(e.thirdId);
            out.add(expedition);
        }
        return out;
    }

    @WorkerThread
    public void insertNew(Expedition expedition) {
        save(expedition);
        List<ExpeditionSegmentEntity> segments = new ArrayList<>();
        List<Segment> source = expedition.getSegments();
        for (int i = 0; i < source.size(); i++) {
            ExpeditionSegmentEntity s = new ExpeditionSegmentEntity();
            s.expeditionId = expedition.getId();
            s.idx = i;
            s.type = source.get(i).getType().name();
            s.durationMillis = source.get(i).getDurationMillis();
            s.eventId = source.get(i).getEventId();
            segments.add(s);
        }
        dao.insertSegments(segments);
    }

    @WorkerThread
    public void save(Expedition expedition) {
        ExpeditionEntity e = new ExpeditionEntity();
        e.id = expedition.getId();
        e.scoutId = expedition.getScoutId();
        e.zoneId = expedition.getZoneId();
        e.seed = expedition.getSeed();
        e.startedAtMillis = expedition.getStartedAtMillis();
        e.rngState = expedition.getRngState();
        e.status = expedition.getStatus().name();
        e.segmentIndex = expedition.getIndex();
        e.cursorAtMillis = expedition.getCursorAtMillis();
        e.pendingSinceMillis = expedition.getPendingSinceMillis();
        e.chosenOption = expedition.getChosenOption();
        e.chosenAtMillis = expedition.getChosenAtMillis();
        e.retreatOffered = expedition.isRetreatOffered();
        Mission mission = expedition.getMission();
        e.missionType = mission.getType().name();
        e.missionTarget = mission.getTarget();
        e.signalNoise = mission.getNoise();
        e.orderDelayMillis = mission.getOrderDelayMillis();
        e.cacheIntel = mission.hasCacheIntel();
        e.dangerIntel = mission.hasDangerIntel();
        e.lootFactor = mission.getLootFactor();
        e.aborted = expedition.isAborted();
        e.lostHints = expedition.getLostHints();
        e.partnerId = expedition.getPartnerId();
        e.thirdId = expedition.getThirdId();
        dao.upsert(e);

        dao.clearItems(expedition.getId());
        List<ExpeditionItemEntity> items = new ArrayList<>();
        List<String> carried = expedition.getCarriedItems();
        for (int i = 0; i < carried.size(); i++) {
            ExpeditionItemEntity item = new ExpeditionItemEntity();
            item.expeditionId = expedition.getId();
            item.idx = i;
            item.itemId = carried.get(i);
            items.add(item);
        }
        if (!items.isEmpty()) {
            dao.insertItems(items);
        }

        dao.clearLoot(expedition.getId());
        List<ExpeditionLootEntity> loot = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : expedition.getCarriedLoot().entrySet()) {
            ExpeditionLootEntity l = new ExpeditionLootEntity();
            l.expeditionId = expedition.getId();
            l.resourceId = entry.getKey();
            l.amount = entry.getValue();
            loot.add(l);
        }
        if (!loot.isEmpty()) {
            dao.insertLoot(loot);
        }
    }
}
