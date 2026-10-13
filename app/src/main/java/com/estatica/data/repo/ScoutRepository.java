package com.estatica.data.repo;

import androidx.annotation.WorkerThread;

import com.estatica.data.db.ScoutDao;
import com.estatica.data.db.ScoutEntity;
import com.estatica.data.db.ScoutFlagEntity;
import com.estatica.data.db.ScoutStatEntity;
import com.estatica.data.db.ScoutTraitEntity;
import com.estatica.domain.scouts.Injury;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutLife;
import com.estatica.domain.scouts.ScoutStatus;
import com.estatica.domain.scouts.ScoutTrait;
import com.estatica.domain.scouts.Stat;
import com.estatica.domain.scouts.StatType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Scout persistence. Blocking: call from the game executor, inside a transaction when saving. */
@Singleton
public final class ScoutRepository {

    private final ScoutDao dao;

    @Inject
    public ScoutRepository(ScoutDao dao) {
        this.dao = dao;
    }

    /** All scouts (dead ones too), in roster order. */
    @WorkerThread
    public List<Scout> loadAll() {
        Map<String, Map<StatType, Stat>> stats = new HashMap<>();
        for (ScoutStatEntity e : dao.allStats()) {
            stats.computeIfAbsent(e.scoutId, k -> new EnumMap<>(StatType.class))
                    .put(StatType.fromKey(e.stat), new Stat(e.value, e.ceiling, e.xp));
        }
        Map<String, Set<String>> flags = new HashMap<>();
        for (ScoutFlagEntity e : dao.allFlags()) {
            flags.computeIfAbsent(e.scoutId, k -> new LinkedHashSet<>()).add(e.flag);
        }
        Map<String, List<ScoutTrait>> traits = new HashMap<>();
        for (ScoutTraitEntity e : dao.allTraits()) {
            traits.computeIfAbsent(e.scoutId, k -> new ArrayList<>())
                    .add(new ScoutTrait(e.traitId, e.hidden, e.revealed));
        }
        List<Scout> scouts = new ArrayList<>();
        for (ScoutEntity e : dao.all()) {
            ScoutLife life = new ScoutLife(e.bond, e.taskId, flags.getOrDefault(e.id, Set.of()),
                    e.lastTalkAtMillis, e.lastInitiatedAtMillis, e.lastDecayAtMillis,
                    e.taskXpCarry, e.taskOutputCarry, e.diedAtMillis, e.diedZoneId, e.stressCarry, e.gearId);
            Scout scout = new Scout(e.id, e.templateId, e.name, e.voiceId,
                    stats.getOrDefault(e.id, Map.of()),
                    traits.getOrDefault(e.id, List.of()),
                    e.stress, e.trust, Injury.valueOf(e.injury), ScoutStatus.valueOf(e.status),
                    e.recoveredAtMillis, e.healProgressHours, life);
            scout.setPersona(PersonaJson.read(e.persona));
            scouts.add(scout);
        }
        return scouts;
    }

    @WorkerThread
    public void insertNew(Scout scout) {
        ScoutEntity e = new ScoutEntity();
        e.id = scout.getId();
        e.templateId = scout.getTemplateId();
        e.name = scout.getName();
        e.voiceId = scout.getVoiceId();
        e.persona = PersonaJson.write(scout.getPersona());
        e.sortOrder = dao.maxSortOrder() + 1;
        e.stress = scout.getStress();
        e.trust = scout.getTrust();
        e.injury = scout.getInjury().name();
        e.status = scout.getStatus().name();
        e.recoveredAtMillis = scout.getRecoveredAtMillis();
        e.healProgressHours = scout.getHealProgressHours();
        dao.insert(e);
        saveLife(scout);
        saveStatsAndTraits(scout);
    }

    @WorkerThread
    public void save(Scout scout) {
        dao.updateState(scout.getId(), scout.getStress(), scout.getTrust(),
                scout.getInjury().name(), scout.getStatus().name(),
                scout.getRecoveredAtMillis(), scout.getHealProgressHours());
        saveLife(scout);
        saveStatsAndTraits(scout);
    }

    private void saveLife(Scout scout) {
        ScoutLife life = scout.getLife();
        dao.updateLife(scout.getId(), life.getBond(), life.getTaskId(), life.getLastTalkAtMillis(),
                life.getLastInitiatedAtMillis(), life.getLastDecayAtMillis(), life.getTaskXpCarry(),
                life.getTaskOutputCarry(), life.getDiedAtMillis(), life.getDiedZoneId(),
                life.getStressCarry(), life.getGearId());
        // Rewritten whole, so a flag that was removed (a romance that ended) stays removed.
        dao.deleteFlags(scout.getId());
        List<ScoutFlagEntity> flags = new ArrayList<>();
        for (String flag : life.getFlags()) {
            ScoutFlagEntity f = new ScoutFlagEntity();
            f.scoutId = scout.getId();
            f.flag = flag;
            flags.add(f);
        }
        if (!flags.isEmpty()) {
            dao.insertFlags(flags);
        }
    }

    private void saveStatsAndTraits(Scout scout) {
        List<ScoutStatEntity> stats = new ArrayList<>();
        for (Map.Entry<StatType, Stat> entry : scout.getStats().entrySet()) {
            ScoutStatEntity s = new ScoutStatEntity();
            s.scoutId = scout.getId();
            s.stat = entry.getKey().key();
            s.value = entry.getValue().getValue();
            s.ceiling = entry.getValue().getCeiling();
            s.xp = entry.getValue().getXp();
            stats.add(s);
        }
        dao.upsertStats(stats);

        List<ScoutTraitEntity> traits = new ArrayList<>();
        List<ScoutTrait> scoutTraits = scout.getTraits();
        for (int i = 0; i < scoutTraits.size(); i++) {
            ScoutTraitEntity t = new ScoutTraitEntity();
            t.scoutId = scout.getId();
            t.traitId = scoutTraits.get(i).getTraitId();
            t.hidden = scoutTraits.get(i).isHidden();
            t.revealed = scoutTraits.get(i).isRevealed();
            t.position = i;
            traits.add(t);
        }
        if (!traits.isEmpty()) {
            dao.upsertTraits(traits);
        }
    }
}
