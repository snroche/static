package com.estatica.domain.content;

import java.util.List;
import java.util.Map;

/**
 * Who a scout is: name, voice, stats, traits, tastes, story. Authored ones come from
 * {@code scouts.json} (starters) and {@code survivors.json} (legacy saves); generated ones
 * are built by {@code PersonaGenerator} and stored in the save. Filled by Gson.
 */
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
    /** Background id from {@code people.json} (nullable for starters). */
    private String background;
    /** "F" or "M". Null in saves from before 0.7.0: PeopleDef.genderOf decides from the name. */
    private String gender;

    public String getGender() {
        return gender;
    }

    public ScoutTemplate() {
    }

    /** A generated person. Its id is the rescue key, not a content id. */
    public static ScoutTemplate generated(String id, String name, String voice, String bio, String background,
                                          Map<String, int[]> stats, List<String> traits, String hiddenTrait,
                                          List<String> likes, List<String> dislikes,
                                          List<String> rescueLines, List<Habit> habits) {
        return generated(id, name, voice, bio, background, stats, traits, hiddenTrait, likes, dislikes,
                rescueLines, habits, null);
    }

    public static ScoutTemplate generated(String id, String name, String voice, String bio, String background,
                                          Map<String, int[]> stats, List<String> traits, String hiddenTrait,
                                          List<String> likes, List<String> dislikes,
                                          List<String> rescueLines, List<Habit> habits, String gender) {
        ScoutTemplate t = new ScoutTemplate();
        t.gender = gender;
        t.id = id;
        t.name = name;
        t.voice = voice;
        t.bio = bio;
        t.background = background;
        t.stats = stats;
        t.traits = traits;
        t.hiddenTrait = hiddenTrait;
        t.likes = likes;
        t.dislikes = dislikes;
        t.rescueLines = rescueLines;
        t.habits = habits;
        return t;
    }

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

    public String getBackground() {
        return background;
    }

    public static final class Habit {
        private String text;
        private int minBondLevel = 1;

        public Habit() {
        }

        public Habit(String text, int minBondLevel) {
            this.text = text;
            this.minBondLevel = minBondLevel;
        }

        public String getText() {
            return text;
        }

        public int getMinBondLevel() {
            return minBondLevel;
        }
    }
}
