package com.estatica.data.repo;

import androidx.annotation.WorkerThread;

import com.estatica.core.Ids;
import com.estatica.data.db.RadioDao;
import com.estatica.data.db.RadioEntities;
import com.estatica.domain.radio.Relay;
import com.estatica.domain.radio.Transmission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Persistence for the radio and the bunker modules. Blocking: call from the game executor,
 * inside a transaction when saving.
 */
@Singleton
public final class RadioRepository {

    private final RadioDao dao;

    @Inject
    public RadioRepository(RadioDao dao) {
        this.dao = dao;
    }

    // ---- Flags, zones, caught signals ----

    @WorkerThread
    public Set<String> flags() {
        Set<String> out = new LinkedHashSet<>();
        for (RadioEntities.GameFlag f : dao.flags()) {
            out.add(f.flag);
        }
        return out;
    }

    @WorkerThread
    public void addFlag(String flag, long now) {
        RadioEntities.GameFlag f = new RadioEntities.GameFlag();
        f.flag = flag;
        f.setAtMillis = now;
        dao.insertFlag(f);
    }

    @WorkerThread
    public Set<String> knownZones() {
        Set<String> out = new LinkedHashSet<>();
        for (RadioEntities.KnownZone z : dao.knownZones()) {
            out.add(z.zoneId);
        }
        return out;
    }

    @WorkerThread
    public void addKnownZone(String zoneId, long now) {
        RadioEntities.KnownZone z = new RadioEntities.KnownZone();
        z.zoneId = zoneId;
        z.discoveredAtMillis = now;
        dao.insertKnownZone(z);
    }

    @WorkerThread
    public Set<String> caughtSignals() {
        return new LinkedHashSet<>(dao.caughtSignals());
    }

    @WorkerThread
    public void addCaughtSignal(String key, long now) {
        RadioEntities.CaughtSignal c = new RadioEntities.CaughtSignal();
        c.signalKey = key;
        c.caughtAtMillis = now;
        dao.insertCaughtSignal(c);
    }

    // ---- Transmissions ----

    @WorkerThread
    public List<Transmission> pendingTransmissions() {
        List<Transmission> out = new ArrayList<>();
        for (RadioEntities.TransmissionRow r : dao.pendingTransmissions()) {
            out.add(new Transmission(r.id, r.sourceId, Transmission.Kind.valueOf(r.kind), r.plainText,
                    r.shift, r.bandId, r.zoneId, r.receivedAtMillis, Transmission.Status.valueOf(r.status)));
        }
        return out;
    }

    @WorkerThread
    public Set<String> receivedTransmissionSources() {
        return new LinkedHashSet<>(dao.receivedTransmissionSources());
    }

    @WorkerThread
    public void save(Transmission t) {
        RadioEntities.TransmissionRow r = new RadioEntities.TransmissionRow();
        r.id = t.getId();
        r.sourceId = t.getSourceId();
        r.kind = t.getKind().name();
        r.plainText = t.getPlainText();
        r.shift = t.getShift();
        r.bandId = t.getBandId();
        r.zoneId = t.getZoneId();
        r.receivedAtMillis = t.getReceivedAtMillis();
        r.status = t.getStatus().name();
        dao.upsertTransmission(r);
    }

    // ---- Intel ----

    /** Zone id to intel types (CACHE, DANGER) with their row ids. */
    @WorkerThread
    public Map<String, Map<String, String>> intel() {
        Map<String, Map<String, String>> out = new HashMap<>();
        for (RadioEntities.Intel i : dao.intel()) {
            out.computeIfAbsent(i.zoneId, k -> new HashMap<>()).put(i.type, i.id);
        }
        return out;
    }

    @WorkerThread
    public String addIntel(String zoneId, String type, long now) {
        RadioEntities.Intel i = new RadioEntities.Intel();
        i.id = Ids.newId();
        i.zoneId = zoneId;
        i.type = type;
        i.createdAtMillis = now;
        dao.insertIntel(i);
        return i.id;
    }

    @WorkerThread
    public void useIntel(String id) {
        dao.deleteIntel(id);
    }

    // ---- Relays ----

    @WorkerThread
    public Map<String, Relay> relays() {
        Map<String, Relay> out = new HashMap<>();
        for (RadioEntities.RelayRow r : dao.relays()) {
            out.put(r.zoneId, new Relay(r.zoneId, r.condition, r.lastDecayAtMillis));
        }
        return out;
    }

    @WorkerThread
    public void save(Relay relay) {
        RadioEntities.RelayRow r = new RadioEntities.RelayRow();
        r.zoneId = relay.getZoneId();
        r.condition = relay.getCondition();
        r.lastDecayAtMillis = relay.getLastDecayAtMillis();
        dao.upsertRelay(r);
    }

    // ---- Rescues ----

    /** Survivor id to zone id. */
    @WorkerThread
    public Map<String, String> rescueTargets() {
        Map<String, String> out = new HashMap<>();
        for (RadioEntities.RescueTarget t : dao.rescueTargets()) {
            out.put(t.survivorId, t.zoneId);
        }
        return out;
    }

    @WorkerThread
    public void addRescueTarget(String survivorId, String zoneId, long now) {
        RadioEntities.RescueTarget t = new RadioEntities.RescueTarget();
        t.survivorId = survivorId;
        t.zoneId = zoneId;
        t.foundAtMillis = now;
        dao.upsertRescueTarget(t);
    }

    @WorkerThread
    public void removeRescueTarget(String survivorId) {
        dao.deleteRescueTarget(survivorId);
    }

    // ---- Lore ----

    /** Source id to text, in discovery order. */
    @WorkerThread
    public Map<String, String> lore() {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (RadioEntities.Lore l : dao.lore()) {
            out.put(l.sourceId, l.text);
        }
        return out;
    }

    @WorkerThread
    public void addLore(String sourceId, String text, long now) {
        RadioEntities.Lore l = new RadioEntities.Lore();
        l.id = sourceId;
        l.sourceId = sourceId;
        l.text = text;
        l.foundAtMillis = now;
        dao.insertLore(l);
    }

    // ---- Modules ----

    @WorkerThread
    public Map<String, Integer> moduleLevels() {
        Map<String, Integer> out = new HashMap<>();
        for (RadioEntities.Module m : dao.modules()) {
            out.put(m.moduleId, m.level);
        }
        return out;
    }

    @WorkerThread
    public void saveModule(String moduleId, int level) {
        RadioEntities.Module m = new RadioEntities.Module();
        m.moduleId = moduleId;
        m.level = level;
        dao.upsertModule(m);
    }
}
