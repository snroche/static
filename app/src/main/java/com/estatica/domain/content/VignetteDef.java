package com.estatica.domain.content;

import java.util.List;

/**
 * A small moment between two people in the bunker ({@code vignettes.json}): narration and a
 * line or two in their own voices, with a nudge to their stress and how they get on.
 * {@code {a}} and {@code {b}} are the two. Filled by Gson.
 */
public final class VignetteDef {

    private String id;
    /** "friends", "rivals" or null (anyone). "friends" includes inseparables. */
    private String requires;
    /** {a} must have any of these traits (visible), or empty. */
    private List<String> requiresTrait;
    /** Only at night (device clock). */
    private boolean night;
    private int weight = 1;
    private List<Line> lines;
    /** Applied to both. */
    private int stress;
    /** Between the two. */
    private double affinity;

    public String getId() {
        return id;
    }

    public String getRequires() {
        return requires;
    }

    public List<String> getRequiresTrait() {
        return requiresTrait != null ? requiresTrait : List.of();
    }

    public boolean isNight() {
        return night;
    }

    public int getWeight() {
        return Math.max(1, weight);
    }

    public List<Line> getLines() {
        return lines != null ? lines : List.of();
    }

    public int getStress() {
        return stress;
    }

    public double getAffinity() {
        return affinity;
    }

    public static final class Line {
        /** "a", "b", or null for narration. */
        private String who;
        private String text;

        public String getWho() {
            return who;
        }

        public String getText() {
            return text;
        }
    }
}
