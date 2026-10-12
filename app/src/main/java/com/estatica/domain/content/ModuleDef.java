package com.estatica.domain.content;

import java.util.List;
import java.util.Map;

/** A bunker module (GDD §7) and what each level costs. Filled by Gson. */
public final class ModuleDef {

    private String id;
    /** What each level above the first adds (battery bank: reserve; pantry: food saved; barricades: defense). */
    private double perLevel;

    public double getPerLevel() {
        return perLevel;
    }
    private String name;
    private String description;
    /** What each level does, level 1 first. */
    private List<String> levels;

    /** What it does at a level (1-based), or null. */
    public String levelText(int level) {
        return levels != null && level >= 1 && level <= levels.size() ? levels.get(level - 1) : null;
    }
    private int startLevel = 1;
    /** Cost to reach level i + 2 (index 0 = upgrade from 1 to 2). Resource id to amount. */
    private List<Map<String, Integer>> upgradeCosts;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getStartLevel() {
        return startLevel;
    }

    public int getMaxLevel() {
        return startLevel + (upgradeCosts != null ? upgradeCosts.size() : 0);
    }

    /** Cost to go from {@code level} to {@code level + 1}, or null at max level. */
    public Map<String, Integer> costFrom(int level) {
        int index = level - startLevel;
        if (upgradeCosts == null || index < 0 || index >= upgradeCosts.size()) {
            return null;
        }
        return upgradeCosts.get(index);
    }
}
