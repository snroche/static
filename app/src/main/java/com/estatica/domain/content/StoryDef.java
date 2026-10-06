package com.estatica.domain.content;

import java.util.List;
import java.util.Map;

/**
 * Narrative content from {@code story.json}: La Voz chapters (GDD §8), personal missions
 * (GDD §5.2) and daily / weekly goals (GDD §9). Filled by Gson.
 */
public final class StoryDef {

    private List<Chapter> chapters;
    private List<PersonalMission> personalMissions;
    private Objectives objectives;

    public List<Chapter> getChapters() {
        return chapters;
    }

    public List<PersonalMission> getPersonalMissions() {
        return personalMissions;
    }

    public Objectives getObjectives() {
        return objectives;
    }

    /** The chapter a fragment belongs to, or null. */
    public Chapter chapterOf(String fragmentId) {
        for (Chapter c : chapters) {
            for (Fragment f : c.fragments) {
                if (f.id.equals(fragmentId)) {
                    return c;
                }
            }
        }
        return null;
    }

    public PersonalMission personalMission(String id) {
        for (PersonalMission m : personalMissions) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    /** A chapter of La Voz: collect every fragment to hear the finale. */
    public static final class Chapter {
        private String id;
        private String title;
        private List<Fragment> fragments;
        /** La Voz speaking at the end. */
        private List<String> finale;
        private String closing;

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public List<Fragment> getFragments() {
            return fragments;
        }

        public List<String> getFinale() {
            return finale;
        }

        public String getClosing() {
            return closing;
        }
    }

    /** A piece of the plot. Found through a transmission, an event outcome or a mission. */
    public static final class Fragment {
        private String id;
        /** What the Diary keeps. */
        private String text;
        /** Where to look (shown once found; a hint for the reader of the content). */
        private String source;

        public String getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public String getSource() {
            return source;
        }
    }

    /** A scout's own arc: offered at high bond, played as an expedition (GDD §5.2). */
    public static final class PersonalMission {
        private String id;
        private String scout;
        private String title;
        private String zone;
        /** Situation that replaces the first one of the expedition. */
        private String event;
        private int rewardBond;
        private int rewardStressRelief;
        private String rewardTrait;
        private String memory;
        /** What the scout says once it's done (in their voice). */
        private List<String> doneLines;

        public String getId() {
            return id;
        }

        public String getScout() {
            return scout;
        }

        public String getTitle() {
            return title;
        }

        public String getZone() {
            return zone;
        }

        public String getEvent() {
            return event;
        }

        public int getRewardBond() {
            return rewardBond;
        }

        public int getRewardStressRelief() {
            return rewardStressRelief;
        }

        public String getRewardTrait() {
            return rewardTrait;
        }

        public String getMemory() {
            return memory;
        }

        public List<String> getDoneLines() {
            return doneLines;
        }

        /** Scout flag set when accepted (through its offer dialogue). */
        public String offeredFlag() {
            return "personal:" + id + ":offered";
        }

        public String doneFlag() {
            return "personal:" + id + ":done";
        }
    }

    /** Daily and weekly goals (GDD §9). Counters: decode, expeditions, sweeps. */
    public static final class Objectives {
        private List<Goal> daily;
        private Goal weekly;

        public List<Goal> getDaily() {
            return daily;
        }

        public Goal getWeekly() {
            return weekly;
        }
    }

    public static final class Goal {
        private String id;
        /** decode, expeditions or sweeps. */
        private String counter;
        private int target;
        private String label;
        private Map<String, Integer> reward;

        public String getId() {
            return id;
        }

        public String getCounter() {
            return counter;
        }

        public int getTarget() {
            return target;
        }

        public String getLabel() {
            return label;
        }

        public Map<String, Integer> getReward() {
            return reward != null ? reward : Map.of();
        }
    }
}
