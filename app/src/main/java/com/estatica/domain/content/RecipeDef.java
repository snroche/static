package com.estatica.domain.content;

import java.util.Map;

/** A workshop recipe (GDD §6.5) from {@code recipes.json}. Filled by Gson. */
public final class RecipeDef {

    private String id;
    private String name;
    private Map<String, Integer> inputs;
    /** Resource produced (materials, kits, parts or gear). */
    private String output;
    private int amount = 1;
    /** Minutes at normal speed. */
    private double minutes;
    private int minWorkshopLevel = 1;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Integer> getInputs() {
        return inputs;
    }

    public String getOutput() {
        return output;
    }

    public int getAmount() {
        return amount;
    }

    public double getMinutes() {
        return minutes;
    }

    public int getMinWorkshopLevel() {
        return minWorkshopLevel;
    }
}
