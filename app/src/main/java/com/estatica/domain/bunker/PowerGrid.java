package com.estatica.domain.bunker;

import com.estatica.domain.content.EnergyDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splitting the generator's watts between consumers (GDD §6.4). The operator asks for an
 * allocation; if there isn't enough (blackout, night), the lowest-priority consumers are cut
 * first. Pure.
 */
public final class PowerGrid {

    private PowerGrid() {
    }

    public static final class State {
        public final int available;
        public final Map<String, Integer> effective;
        public final int used;
        /** Consumers that got less than requested. */
        public final List<String> cut;
        public final boolean night;
        /** Filters below what the air needs: everyone gets stressed. */
        public final boolean filtersShort;

        State(int available, Map<String, Integer> effective, int used, List<String> cut, boolean night,
              boolean filtersShort) {
            this.available = available;
            this.effective = Collections.unmodifiableMap(effective);
            this.used = used;
            this.cut = Collections.unmodifiableList(cut);
            this.night = night;
            this.filtersShort = filtersShort;
        }

        public int units(String consumer) {
            return effective.getOrDefault(consumer, 0);
        }

        public int spare() {
            return Math.max(0, available - used);
        }
    }

    public static State compute(EnergyDef energy, Map<String, Integer> requested, int generatorLevel,
                                int blackoutWatts, boolean night) {
        int available = Math.max(0, energy.watts(generatorLevel) - blackoutWatts);
        Map<String, Integer> effective = new LinkedHashMap<>();
        int used = 0;
        for (EnergyDef.Consumer c : energy.getConsumers()) {
            int units = Math.max(0, Math.min(c.getMax(), requested.getOrDefault(c.getId(), c.getStart())));
            effective.put(c.getId(), units);
            used += units;
        }
        List<EnergyDef.Consumer> byPriority = new ArrayList<>(energy.getConsumers());
        byPriority.sort(Comparator.comparingInt(EnergyDef.Consumer::getPriority));
        List<String> cut = new ArrayList<>();
        for (EnergyDef.Consumer c : byPriority) {
            while (used > available && effective.get(c.getId()) > 0) {
                effective.put(c.getId(), effective.get(c.getId()) - 1);
                used--;
                if (!cut.contains(c.getId())) {
                    cut.add(c.getId());
                }
            }
        }
        EnergyDef.Consumer filters = energy.consumer("filters");
        int needed = filters.getMin() + (night ? filters.getNightExtra() : 0);
        return new State(available, effective, used, cut, night, effective.get("filters") < needed);
    }

    // ---- What each consumer's power does (placeholders, tuned here rather than in JSON
    // because they are curves, not single numbers) ----

    /** Report noise multiplier; unpowered radio is worse than nothing. */
    public static double radioNoiseFactor(int units) {
        return Math.max(0.3, 1.2 - 0.15 * units);
    }

    /** Without power the antenna only reaches the short band. */
    public static boolean antennaOn(int units) {
        return units > 0;
    }

    /** Workshop speed multiplier; 0 pauses crafting. */
    public static double workshopSpeed(int units) {
        return units <= 0 ? 0 : 0.5 + 0.5 * units;
    }

    /** Healing multiplier from the infirmary module (level) and its power. */
    public static double infirmaryHeal(int units, int level) {
        return 1.0 + 0.2 * (level - 1) + 0.25 * units;
    }

    /** Stress relief per hour from a powered infirmary. */
    public static double infirmaryRelief(int units) {
        return 0.5 * units;
    }
}
