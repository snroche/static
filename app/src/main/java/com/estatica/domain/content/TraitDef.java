package com.estatica.domain.content;

import java.util.Map;

/**
 * A trait from {@code traits.json}. Every modifier has a neutral default, so a trait only
 * lists what it changes. Filled by Gson.
 */
public final class TraitDef {

    /** How a scout leans when deciding alone. */
    public enum Bias { NONE, RISKY, CAUTIOUS }

    /** Where a trait comes from; only affects how it is presented. */
    public enum Kind { PERSONALITY, SPECIALIZATION, SCAR }

    private String id;
    private String name;
    private String description;
    /** Stat key to flat bonus applied to checks with that stat. */
    private Map<String, Integer> statBonus;
    /** Added to every check chance (e.g. -0.05). */
    private double checkBonus;
    private double lootMultiplier = 1.0;
    private double durationMultiplier = 1.0;
    private double stressMultiplier = 1.0;
    /** Below this trust the scout may ignore orders. 0 = never. */
    private int disobeyBelowTrust;
    private Bias bias = Bias.NONE;
    private Kind kind = Kind.PERSONALITY;
    /** Multiplies healing of every other scout while this one works in the infirmary. */
    private double healOthersMultiplier = 1.0;
    /** Traits that rub this one the wrong way: sharing a task builds friction. */
    private java.util.List<String> clashes;
    /** Generation: can be someone's hidden trait (has a confession dialogue). */
    private boolean hideable;
    /** Generation: voices this trait leans toward. */
    private java.util.List<String> voices;
    /** Generation: tastes and habits this trait brings. */
    private java.util.List<String> likes;
    private java.util.List<String> dislikes;
    private java.util.List<ScoutTemplate.Habit> habits;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int statBonus(String statKey) {
        if (statBonus == null) {
            return 0;
        }
        Integer bonus = statBonus.get(statKey);
        return bonus != null ? bonus : 0;
    }

    public Map<String, Integer> getStatBonus() {
        return statBonus;
    }

    public double getCheckBonus() {
        return checkBonus;
    }

    public double getLootMultiplier() {
        return lootMultiplier;
    }

    public double getDurationMultiplier() {
        return durationMultiplier;
    }

    public double getStressMultiplier() {
        return stressMultiplier;
    }

    public int getDisobeyBelowTrust() {
        return disobeyBelowTrust;
    }

    public Bias getBias() {
        return bias != null ? bias : Bias.NONE;
    }

    public Kind getKind() {
        return kind != null ? kind : Kind.PERSONALITY;
    }

    public double getHealOthersMultiplier() {
        return healOthersMultiplier;
    }

    public java.util.List<String> getClashes() {
        return clashes != null ? clashes : java.util.List.of();
    }

    public boolean isHideable() {
        return hideable;
    }

    public java.util.List<String> getVoices() {
        return voices != null ? voices : java.util.List.of();
    }

    public java.util.List<String> getLikes() {
        return likes != null ? likes : java.util.List.of();
    }

    public java.util.List<String> getDislikes() {
        return dislikes != null ? dislikes : java.util.List.of();
    }

    public java.util.List<ScoutTemplate.Habit> getHabits() {
        return habits != null ? habits : java.util.List.of();
    }

    /** True if either trait lists the other as a clash. */
    public boolean clashesWith(TraitDef other) {
        return getClashes().contains(other.getId()) || other.getClashes().contains(getId());
    }
}
