package com.estatica.domain.content;

import java.util.List;
import java.util.Map;

/**
 * A short conversation from {@code dialogues.json} (GDD §5.3): the scout says a few lines,
 * the operator picks an answer, the scout replies. Longer arcs chain through flags.
 * Filled by Gson.
 */
public final class DialogueDef {

    public enum Kind {
        /** Started by the operator ([HABLAR]). */
        TALK,
        /** Started by the scout ("got a minute?"). */
        INITIATED,
        /** The scout asks for advice; answers grant a specialization. Scout-initiated. */
        GUIDANCE,
        /** About a fallen companion, days later. Scout-initiated. */
        GRIEF
    }

    private String id;
    private Kind kind = Kind.TALK;
    /** Template id of the scout, or null for anyone. */
    private String scout;
    private int minBondLevel;
    /** Only below this bond level (shy, early talks). Null for no limit. */
    private Integer maxBondLevel;
    /** Trust range (0–100), each end nullable: trust-based talks. */
    private Integer minTrust;
    private Integer maxTrust;
    /** Stress range (0–100), each end nullable. */
    private Integer minStress;
    private Integer maxStress;
    /** Romance meter range (0–100), each end nullable. Any romance condition: adults only. */
    private Integer minRomance;
    private Integer maxRomance;
    /** true: only while together with the operator; false: only while not. Null: either. */
    private Boolean together;
    /** Bond high enough for their openness: flirting is on the table. */
    private boolean romanceReady;
    /** Jealousy (0–100) at least this. */
    private Integer minJealousy;
    /** Together, and the operator hasn't paid them attention in a while. */
    private boolean requiresNeglect;
    /** GRIEF about someone who was the operator's partner. */
    private boolean requiresDeadPartner;
    /** Only when the operator is a woman (true) or a man (false). Null: either. */
    private Boolean operatorFemale;
    /** Only in this weather outside (WeatherRules.Weather names). Empty: any. */
    private List<String> weather;
    /** true: only after dark; false: only by day. Null: either. */
    private Boolean night;

    public List<String> getWeather() {
        return weather != null ? weather : List.of();
    }

    public Boolean getNight() {
        return night;
    }

    public Integer getMinRomance() {
        return minRomance;
    }

    public Integer getMaxRomance() {
        return maxRomance;
    }

    public Boolean getTogether() {
        return together;
    }

    public boolean isRomanceReady() {
        return romanceReady;
    }

    public Integer getMinJealousy() {
        return minJealousy;
    }

    public boolean isRequiresNeglect() {
        return requiresNeglect;
    }

    public boolean isRequiresDeadPartner() {
        return requiresDeadPartner;
    }

    public Boolean getOperatorFemale() {
        return operatorFemale;
    }

    /** Any romance condition at all (these are adults-only). */
    public boolean isRomantic() {
        return minRomance != null || maxRomance != null || together != null || romanceReady
                || minJealousy != null || requiresNeglect;
    }
    private List<String> requiresFlags;
    private List<String> forbidsFlags;
    /** Stat key to minimum value. */
    private Map<String, Integer> requiresStats;
    /** GRIEF: hours since the most recent death. */
    private double minHoursSinceDeath;
    /** Any of these traits, visible or revealed (personality dialogues). */
    private List<String> requiresTrait;
    /** This trait, still hidden: the confession that reveals it. */
    private String requiresHiddenTrait;
    /** Background id from people.json. */
    private String requiresBackground;
    /** "friend", "inseparable" or "rival": needs such a companion ({other}). Null for none. */
    private String requiresRelation;
    private boolean repeatable;
    /** Higher goes first among eligible dialogues. */
    private int priority;
    /** Scout lines ({name}, {dead}). */
    private List<String> lines;
    private List<Choice> choices;

    public String getId() {
        return id;
    }

    public Kind getKind() {
        return kind != null ? kind : Kind.TALK;
    }

    /** True for kinds the scout starts on their own. */
    public boolean isScoutInitiated() {
        return getKind() != Kind.TALK;
    }

    public String getScout() {
        return scout;
    }

    public int getMinBondLevel() {
        return minBondLevel;
    }

    public Integer getMaxBondLevel() {
        return maxBondLevel;
    }

    public Integer getMinTrust() {
        return minTrust;
    }

    public Integer getMaxTrust() {
        return maxTrust;
    }

    public Integer getMinStress() {
        return minStress;
    }

    public Integer getMaxStress() {
        return maxStress;
    }

    public List<String> getRequiresFlags() {
        return requiresFlags != null ? requiresFlags : List.of();
    }

    public List<String> getForbidsFlags() {
        return forbidsFlags != null ? forbidsFlags : List.of();
    }

    public Map<String, Integer> getRequiresStats() {
        return requiresStats != null ? requiresStats : Map.of();
    }

    public double getMinHoursSinceDeath() {
        return minHoursSinceDeath;
    }

    public List<String> getRequiresTrait() {
        return requiresTrait != null ? requiresTrait : List.of();
    }

    public String getRequiresHiddenTrait() {
        return requiresHiddenTrait;
    }

    public String getRequiresBackground() {
        return requiresBackground;
    }

    public String getRequiresRelation() {
        return requiresRelation;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public int getPriority() {
        return priority;
    }

    public List<String> getLines() {
        return lines;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public static final class Choice {
        /** What the operator says (button). */
        private String label;
        /** Scout reply lines. */
        private List<String> reply;
        private int bond;
        private int trust;
        private int stress;
        /** Affinity change with {other} (relationship dialogues). */
        private int affinity;
        private List<String> setFlags;
        /** Nullable: what the scout keeps from this answer. */
        private String memory;
        /** Nullable: trait gained (specializations). */
        private String addTrait;
        private boolean revealHidden;
        /** Romance meter change (scaled by their openness when positive). */
        private int romance;
        /** Become a couple. */
        private boolean startRomance;
        /** End it. */
        private boolean endRomance;
        /** "Only you": every other romance ends. */
        private boolean exclusive;
        /** Something they hand over (a favor between friends): resource id to amount. */
        private Map<String, Integer> resources;

        public Map<String, Integer> getResources() {
            return resources != null ? resources : Map.of();
        }

        public int getRomance() {
            return romance;
        }

        public boolean isStartRomance() {
            return startRomance;
        }

        public boolean isEndRomance() {
            return endRomance;
        }

        public boolean isExclusive() {
            return exclusive;
        }

        public String getLabel() {
            return label;
        }

        public List<String> getReply() {
            return reply != null ? reply : List.of();
        }

        public int getBond() {
            return bond;
        }

        public int getAffinity() {
            return affinity;
        }

        public int getTrust() {
            return trust;
        }

        public int getStress() {
            return stress;
        }

        public List<String> getSetFlags() {
            return setFlags != null ? setFlags : List.of();
        }

        public String getMemory() {
            return memory;
        }

        public String getAddTrait() {
            return addTrait;
        }

        public boolean isRevealHidden() {
            return revealHidden;
        }
    }
}
