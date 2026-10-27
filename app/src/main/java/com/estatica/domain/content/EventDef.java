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
    /** Bands where it can come up in any zone (common situations), on top of zone lists. */
    private List<String> bands;

    public List<String> getBands() {
        return bands != null ? bands : List.of();
    }

    /** Only in this weather when they set out (WeatherRules.Weather names). Empty: any. */
    private List<String> weather;
    /** true: only after dark; false: only by day. Null: either. */
    private Boolean night;
    /** Chance it's even in the cards on a given trip (rare finds). 1 = always. */
    private double chance = 1.0;
    /** Comes up once in the whole game, at most. */
    private boolean once;
    /** Bunker-wide flags it needs, or can't have (set by earlier choices). */
    private List<String> requiresWorld;
    private List<String> forbidsWorld;
    /** Sound when it comes up (LogLine.Cue), or null. */
    private String cue;

    public List<String> getWeather() {
        return weather != null ? weather : List.of();
    }

    public Boolean getNight() {
        return night;
    }

    public double getChance() {
        return chance <= 0 ? 1.0 : chance;
    }

    public boolean isOnce() {
        return once;
    }

    public List<String> getRequiresWorld() {
        return requiresWorld != null ? requiresWorld : List.of();
    }

    public List<String> getForbidsWorld() {
        return forbidsWorld != null ? forbidsWorld : List.of();
    }

    public String getCue() {
        return cue;
    }

    /** Rare, once-only, or tied to weather, the dark or an earlier choice: worth a sound. */
    public boolean isSpecial() {
        return getChance() < 1.0 || once || !getWeather().isEmpty() || night != null || !getRequiresWorld().isEmpty();
    }

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
        /**
         * Time it costs (positive) or saves (negative), as a share of the trip's planned length,
         * so it scales with the band: 0.15 is a bit longer out there. A saving needs success;
         * a cost is paid either way.
         */
        private double time;

        public double getTime() {
            return time;
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
        /** A piece of gear found (carried home). */
        private GearFind gear;
        /** A part for a workshop project (recipes.json → parts), carried home. */
        private String part;
        /** A personal object (items.json), carried home. */
        private String item;
        /** Bunker-wide flags this sets, for what comes later. */
        private List<String> setWorld;
        /** Bond with whoever was out there (good calls they'll remember). */
        private int bond;
        /** Sound (LogLine.Cue) when it lands, or null. */
        private String cue;

        public GearFind getGear() {
            return gear;
        }

        public String getPart() {
            return part;
        }

        public String getItem() {
            return item;
        }

        public List<String> getSetWorld() {
            return setWorld != null ? setWorld : List.of();
        }

        public int getBond() {
            return bond;
        }

        public String getCue() {
            return cue;
        }

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

    /** Gear found on the spot, in the shape it was found in. */
    public static final class GearFind {
        private String id;
        private String rarity = "COMMON";
        /** 1–100. */
        private int condition = 100;

        public String getId() {
            return id;
        }

        public String getRarity() {
            return rarity != null ? rarity : "COMMON";
        }

        public int getCondition() {
            return condition <= 0 ? 100 : Math.min(100, condition);
        }
    }
}
