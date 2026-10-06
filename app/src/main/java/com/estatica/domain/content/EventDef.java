package com.estatica.domain.content;

import java.util.List;

/** A situation from {@code events.json}: intro, 2–4 options, outcomes. Filled by Gson. */
public final class EventDef {

    private String id;
    /** Lines spoken by the scout when the situation starts. {@code {name}} available. */
    private List<String> intro;
    private List<Option> options;

    public String getId() {
        return id;
    }

    public List<String> getIntro() {
        return intro;
    }

    public List<Option> getOptions() {
        return options;
    }

    public static final class Option {
        public enum Risk { SAFE, RISKY }

        private String id;
        /** Operator's order, shown on the button. */
        private String label;
        private String stat;
        private int difficulty;
        private Risk risk = Risk.SAFE;
        private Outcome success;
        private Outcome failure;

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public String getStat() {
            return stat;
        }

        public int getDifficulty() {
            return difficulty;
        }

        public Risk getRisk() {
            return risk != null ? risk : Risk.SAFE;
        }

        public Outcome getSuccess() {
            return success;
        }

        public Outcome getFailure() {
            return failure;
        }
    }

    public static final class Outcome {
        private List<String> text;
        private int stress;
        /** "none", "light", "serious", "critical". */
        private String injury;
        /** Can kill a scout who was warned beforehand (GDD §5.5). */
        private boolean lethal;
        private List<LootRoll> loot;
        /** Nullable: a memory the scout keeps from this moment. */
        private String memory;
        /** Nullable: a La Voz fragment found here (GDD §8). */
        private String plot;

        public List<String> getText() {
            return text;
        }

        public int getStress() {
            return stress;
        }

        public String getInjury() {
            return injury;
        }

        public boolean isLethal() {
            return lethal;
        }

        public List<LootRoll> getLoot() {
            return loot;
        }

        public String getMemory() {
            return memory;
        }

        public String getPlot() {
            return plot;
        }
    }
}
