package com.estatica.domain.content;

import java.util.List;
import java.util.Map;

/**
 * How someone pays back a gift they loved, in their own way (people.json backgrounds and
 * the hand-made crew in scouts.json): a cook cooks, a mechanic fixes, a musician plays.
 * Filled by Gson.
 */
public final class ThanksDef {

    /** What they say when they hand it over ({name}, {other}, {gear}). */
    private List<String> lines;
    /** What the operator sees happen ({name}, {other}, {gear}). */
    private String text;
    /** Resource id to amount, into the stores. */
    private Map<String, Integer> resources;
    /** Stress change for everyone home (negative eases it). */
    private int crewStress;
    /** Injury levels taken off the worst-hurt person home. */
    private int heal;
    /** Condition points back on the most worn piece of gear in the bunker. */
    private int mend;
    /** Stat key they teach everyone home a little of. */
    private String teach;

    public List<String> getLines() {
        return lines != null ? lines : List.of();
    }

    public String getText() {
        return text;
    }

    public Map<String, Integer> getResources() {
        return resources != null ? resources : Map.of();
    }

    public int getCrewStress() {
        return crewStress;
    }

    public int getHeal() {
        return heal;
    }

    public int getMend() {
        return mend;
    }

    public String getTeach() {
        return teach;
    }
}
