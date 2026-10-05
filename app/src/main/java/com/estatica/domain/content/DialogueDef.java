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
        /** Started by the scout ("¿tenés un minuto?"). */
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
    private List<String> requiresFlags;
    private List<String> forbidsFlags;
    /** Stat key to minimum value. */
    private Map<String, Integer> requiresStats;
    /** GRIEF: hours since the most recent death. */
    private double minHoursSinceDeath;
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
        private List<String> setFlags;
        /** Nullable: what the scout keeps from this answer. */
        private String memory;
        /** Nullable: trait gained (specializations). */
        private String addTrait;
        private boolean revealHidden;

        public String getLabel() {
            return label;
        }

        public List<String> getReply() {
            return reply != null ? reply : List.of();
        }

        public int getBond() {
            return bond;
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
