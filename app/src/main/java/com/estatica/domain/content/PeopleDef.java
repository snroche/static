package com.estatica.domain.content;

import java.util.List;

/**
 * Pools for generated survivors ({@code people.json}): names and backgrounds. Traits add
 * their own pools (likes, habits, voices) in {@code traits.json}. Filled by Gson.
 */
public final class PeopleDef {

    private List<String> names;
    /** Used instead of a name now and then ("Flaco", "Turca"). */
    private List<String> nicknames;
    private List<Background> backgrounds;

    public List<String> getNames() {
        return names != null ? names : List.of();
    }

    public List<String> getNicknames() {
        return nicknames != null ? nicknames : List.of();
    }

    public List<Background> getBackgrounds() {
        return backgrounds != null ? backgrounds : List.of();
    }

    public Background background(String id) {
        for (Background b : getBackgrounds()) {
            if (b.getId().equals(id)) {
                return b;
            }
        }
        return null;
    }

    /** What someone did before the Silence: shapes stats, voice, tastes and their story. */
    public static final class Background {
        private String id;
        private String name;
        /** Bio variants; one is picked. */
        private List<String> bios;
        /** Stat keys that get a bonus. */
        private String primaryStat;
        private String secondaryStat;
        /** Voices that fit (a teenager doesn't talk like a grandmother). */
        private List<String> voices;
        private List<String> likes;
        private List<String> dislikes;
        private List<ScoutTemplate.Habit> habits;
        /** Variants of what they say when rescued; one is picked. */
        private List<List<String>> rescueLines;

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public List<String> getBios() {
            return bios != null ? bios : List.of();
        }

        public String getPrimaryStat() {
            return primaryStat;
        }

        public String getSecondaryStat() {
            return secondaryStat;
        }

        public List<String> getVoices() {
            return voices != null ? voices : List.of();
        }

        public List<String> getLikes() {
            return likes != null ? likes : List.of();
        }

        public List<String> getDislikes() {
            return dislikes != null ? dislikes : List.of();
        }

        public List<ScoutTemplate.Habit> getHabits() {
            return habits != null ? habits : List.of();
        }

        public List<List<String>> getRescueLines() {
            return rescueLines != null ? rescueLines : List.of();
        }
    }
}
