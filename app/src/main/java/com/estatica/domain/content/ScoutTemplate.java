package com.estatica.domain.content;

import java.util.List;
import java.util.Map;

/** A scout definition from {@code scouts.json}. Filled by Gson. */
public final class ScoutTemplate {

    private String id;
    private String name;
    private String voice;
    private String bio;
    /** Stat key to {@code [base, ceiling]}. */
    private Map<String, int[]> stats;
    private List<String> traits;
    private String hiddenTrait;
    /** Item ids or tags they love. Never shown until discovered. */
    private List<String> likes;
    /** Item ids or tags they dislike. */
    private List<String> dislikes;
    /** Survivors only: band where they can be found (null for starters). */
    private String band;
    /** Survivors only: trust when they join (they don't know the operator yet). */
    private Integer initialTrust;
    /** Survivors only: what they say when rescued. */
    private List<String> rescueLines;
    /** Little things the operator notices as the bond grows. */
    private List<Habit> habits;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVoice() {
        return voice;
    }

    public String getBio() {
        return bio;
    }

    public Map<String, int[]> getStats() {
        return stats;
    }

    public List<String> getTraits() {
        return traits;
    }

    /** Nullable. Revealed with high bond (Phase 2). */
    public String getHiddenTrait() {
        return hiddenTrait;
    }

    public List<String> getLikes() {
        return likes != null ? likes : List.of();
    }

    public List<String> getDislikes() {
        return dislikes != null ? dislikes : List.of();
    }

    public String getBand() {
        return band;
    }

    /** Null means the balance default. */
    public Integer getInitialTrust() {
        return initialTrust;
    }

    public List<String> getRescueLines() {
        return rescueLines != null ? rescueLines : List.of();
    }

    public List<Habit> getHabits() {
        return habits != null ? habits : List.of();
    }

    public static final class Habit {
        private String text;
        private int minBondLevel = 1;

        public String getText() {
            return text;
        }

        public int getMinBondLevel() {
            return minBondLevel;
        }
    }
}
