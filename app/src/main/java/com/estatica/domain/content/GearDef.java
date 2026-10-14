package com.estatica.domain.content;

import java.util.Map;

/**
 * Equipment a scout carries (GDD §3, §6.5), from {@code gear.json}. Its id is also the id of
 * the resource that counts how many are in stock. Filled by Gson.
 */
public final class GearDef {

    /** Where it's worn: one piece per slot. RIDE: a bike or a vehicle, shared with the team. */
    public enum Slot { HEAD, NECK, BODY, HANDS, BELT, BACK, FEET, RIDE }

    /** RIDE: how many it carries (rider included). */
    private int seats = 1;
    /** RIDE: fuel burned per hour on the road (0: pedal power). */
    private double fuelPerHour;
    /** Turns up in random finds out there (vehicles don't: they're built). */
    private boolean findable = true;
    /** Out there, stress taken is multiplied by this (a canteen, a poncho): 1 = no change. */
    private double stressFactor = 1.0;

    public double getStressFactor() {
        return stressFactor;
    }

    /** Repairs cost this many times the usual (a truck isn't a flashlight). */
    private int repairScale = 1;

    public int getRepairScale() {
        return Math.max(1, repairScale);
    }

    public int getSeats() {
        return seats;
    }

    public double getFuelPerHour() {
        return fuelPerHour;
    }

    public boolean isFindable() {
        return findable && getSlot() != Slot.RIDE;
    }

    public boolean isRide() {
        return getSlot() == Slot.RIDE;
    }

    private Slot slot;
    /** Shorter expeditions (boots); 1 = no change. */
    private double durationMultiplier = 1.0;

    public Slot getSlot() {
        return slot != null ? slot : Slot.BODY;
    }

    public double getDurationMultiplier() {
        return durationMultiplier;
    }

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
