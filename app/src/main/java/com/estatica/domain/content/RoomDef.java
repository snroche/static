package com.estatica.domain.content;

import java.util.List;
import java.util.Map;

/**
 * A room of the bunker ({@code balance.json → rooms}). Some are open from the start; the
 * rest are sealed or collapsed and get restored as projects: the resources up front, then
 * work hours from whoever is on the Restore task. A room holds module upgrades and unlocks
 * tasks. Its box on the floor plan is {@code x, y, w, h} on a small grid.
 */
public final class RoomDef {

    private String id;
    private String name;
    private String description;
    /** Shown while sealed: what's in the way. */
    private String sealed;
    private boolean open;
    private int x;
    private int y;
    private int w = 1;
    private int h = 1;
    /** Rooms that must be open before this one can be restored. */
    private List<String> requires;
    private Map<String, Integer> cost;
    /** Work hours (one person working one hour = 1). */
    private double hours;
    /** Module ids upgraded here. */
    private List<String> modules;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getSealed() {
        return sealed;
    }

    /** Open from the first day. */
    public boolean isOpen() {
        return open;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getW() {
        return w;
    }

    public int getH() {
        return h;
    }

    public List<String> getRequires() {
        return requires != null ? requires : List.of();
    }

    public Map<String, Integer> getCost() {
        return cost != null ? cost : Map.of();
    }

    public double getHours() {
        return hours;
    }

    public List<String> getModules() {
        return modules != null ? modules : List.of();
    }
}
