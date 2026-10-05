package com.estatica.domain.content;

import java.util.List;

/** A zone from {@code zones.json}. Filled by Gson. */
public final class ZoneDef {

    private String id;
    private String name;
    private String band;
    private String description;
    /** 1 (calm) to 3 (dangerous); raises check difficulty. */
    private int danger;
    /** {@code [min, max]} total expedition duration. */
    private int[] durationMinutes;
    /** {@code [min, max]} number of situations. */
    private int[] situations;
    /** {@code [min, max]} number of finds. */
    private int[] finds;
    /** Event ids that can happen here. */
    private List<String> events;
    private List<LootRoll> loot;
    /** Personal objects that may turn up on a find. */
    private List<ItemDrop> items;
    /** Neutral transit report lines (voice styling is applied on top). */
    private List<String> transit;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBand() {
        return band;
    }

    public String getDescription() {
        return description;
    }

    public int getDanger() {
        return danger;
    }

    public int[] getDurationMinutes() {
        return durationMinutes;
    }

    public int[] getSituations() {
        return situations;
    }

    public int[] getFinds() {
        return finds;
    }

    public List<String> getEvents() {
        return events;
    }

    public List<LootRoll> getLoot() {
        return loot;
    }

    public List<ItemDrop> getItems() {
        return items != null ? items : List.of();
    }

    public List<String> getTransit() {
        return transit;
    }
}
