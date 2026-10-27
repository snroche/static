package com.estatica.domain.expedition;

import com.estatica.domain.log.LogLine;
import com.estatica.domain.scouts.Memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything an engine call produced, to be persisted by the caller. */
public final class ExpeditionResult {

    private final List<LogLine> lines = new ArrayList<>();
    private final List<Memory> memories = new ArrayList<>();
    private final Map<String, Integer> delivered = new LinkedHashMap<>();
    private final List<String> deliveredItems = new ArrayList<>();
    private final List<String> plotFragments = new ArrayList<>();
    private boolean situationArrived;
    private boolean returned;
    private boolean scoutDied;
    private boolean missionCompleted;

    void line(LogLine line) {
        lines.add(line);
    }

    void memory(Memory memory) {
        memories.add(memory);
    }

    void deliver(Map<String, Integer> loot) {
        for (Map.Entry<String, Integer> e : loot.entrySet()) {
            delivered.merge(e.getKey(), e.getValue(), Integer::sum);
        }
    }

    void deliverItems(List<String> items) {
        deliveredItems.addAll(items);
    }

    /** Personal objects (item ids) brought back to the bunker. */
    public List<String> getDeliveredItems() {
        return Collections.unmodifiableList(deliveredItems);
    }

    private final List<String> worldFlags = new ArrayList<>();

    void world(String flag) {
        worldFlags.add(flag);
    }

    /** Bunker-wide flags set out there (earlier choices, once-only situations had). */
    public List<String> getWorldFlags() {
        return Collections.unmodifiableList(worldFlags);
    }

    private final Map<String, Integer> spent = new LinkedHashMap<>();

    void spend(String resource, int amount) {
        spent.merge(resource, amount, Integer::sum);
    }

    /** Paid out of the bunker's stock out there (what they carried didn't cover it). */
    public Map<String, Integer> getSpent() {
        return Collections.unmodifiableMap(spent);
    }

    void plot(String fragmentId) {
        plotFragments.add(fragmentId);
    }

    /** La Voz fragments found on this leg (GDD §8). */
    public List<String> getPlotFragments() {
        return Collections.unmodifiableList(plotFragments);
    }

    void markSituationArrived() {
        situationArrived = true;
    }

    void markReturned() {
        returned = true;
    }

    void markMissionCompleted() {
        missionCompleted = true;
    }

    /** Returned without being cut short: rescue / relay mission fulfilled. */
    public boolean isMissionCompleted() {
        return missionCompleted;
    }

    void markScoutDied() {
        scoutDied = true;
    }

    /** In chronological order for this expedition. */
    public List<LogLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public List<Memory> getMemories() {
        return Collections.unmodifiableList(memories);
    }

    /** Resources brought back to the bunker. */
    public Map<String, Integer> getDelivered() {
        return Collections.unmodifiableMap(delivered);
    }

    public boolean isSituationArrived() {
        return situationArrived;
    }

    public boolean isReturned() {
        return returned;
    }

    public boolean isScoutDied() {
        return scoutDied;
    }

    public boolean isEmpty() {
        return lines.isEmpty() && memories.isEmpty() && delivered.isEmpty() && deliveredItems.isEmpty();
    }
}
