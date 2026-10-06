package com.estatica.domain.content;

import java.util.Map;

/**
 * Equipment a scout carries (GDD §3, §6.5), from {@code gear.json}. Its id is also the id of
 * the resource that counts how many are in stock. Filled by Gson.
 */
public final class GearDef {

    private String id;
    private String name;
    private String description;
    private Map<String, Integer> statBonus;
    private double lootMultiplier = 1.0;
    /** Multiplies report noise for expeditions carrying it. */
    private double noiseFactor = 1.0;
    /** Used up the first time it stops an injury from getting worse. */
    private boolean absorbsInjury;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int statBonus(String statKey) {
        if (statBonus == null) {
            return 0;
        }
        Integer bonus = statBonus.get(statKey);
        return bonus != null ? bonus : 0;
    }

    public Map<String, Integer> getStatBonus() {
        return statBonus;
    }

    public double getLootMultiplier() {
        return lootMultiplier;
    }

    public double getNoiseFactor() {
        return noiseFactor;
    }

    public boolean isAbsorbsInjury() {
        return absorbsInjury;
    }
}
