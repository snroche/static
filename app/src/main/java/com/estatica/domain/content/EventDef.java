package com.estatica.domain.content;

import java.util.List;

/** A situation from {@code events.json}: intro, 2–4 options, outcomes. Filled by Gson. */
public final class EventDef {

    private String id;
    /** Lines spoken by the scout when the situation starts. {@code {name}} available. */
    private List<String> intro;
    private List<Option> options;
    /** The key item this situation exists to give: it stops showing up once you have it. */
    private String grantsKey;

    public String getGrantsKey() {
        return grantsKey;
    }

    /** Only for someone with any of these traits (visible ones). */
    private List<String> requiresTrait;
    /** Only for someone with this background (people.json). */
    private String requiresBackground;

    public List<String> getRequiresTrait() {
        return requiresTrait != null ? requiresTrait : List.of();
    }

    public String getRequiresBackground() {
        return requiresBackground;
    }

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

        /** Only for someone with any of these traits (visible ones). */
        private List<String> requiresTrait;
        /** Only for someone with this background (people.json). */
        private String requiresBackground;

        public List<String> getRequiresTrait() {
            return requiresTrait != null ? requiresTrait : List.of();
        }

        public String getRequiresBackground() {
            return requiresBackground;
        }

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
        /** Key item found (carried home, yours on return). */
        private String keyItem;

        public String getKeyItem() {
            return keyItem;
        }

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
