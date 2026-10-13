package com.estatica.domain.scouts;

import com.estatica.domain.content.ScoutTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A scout's mutable game state. Mutated only by domain rules on the game executor.
 * Timestamps let idle recovery be computed with catch-up instead of ticking.
 */
public final class Scout {

    private final String id;
    private final String templateId;
    private final String name;
    private final String voiceId;
    private final Map<StatType, Stat> stats;
    private final List<ScoutTrait> traits;
    private int stress;
    private int trust;
    private Injury injury;
    private ScoutStatus status;
    /** Last time idle recovery (stress, injuries) was applied. */
    private long recoveredAtMillis;
    /** Leftover healing progress (hours) toward the next injury level. */
    private double healProgressHours;
    private final ScoutLife life;
    /** Generated people carry who they are; authored ones resolve it from content. */
    private ScoutTemplate persona;

    public Scout(String id, String templateId, String name, String voiceId,
                 Map<StatType, Stat> stats, List<ScoutTrait> traits, int stress, int trust,
                 Injury injury, ScoutStatus status, long recoveredAtMillis,
                 double healProgressHours) {
        this(id, templateId, name, voiceId, stats, traits, stress, trust, injury, status,
                recoveredAtMillis, healProgressHours, ScoutLife.fresh(recoveredAtMillis));
    }

    public Scout(String id, String templateId, String name, String voiceId,
                 Map<StatType, Stat> stats, List<ScoutTrait> traits, int stress, int trust,
                 Injury injury, ScoutStatus status, long recoveredAtMillis,
                 double healProgressHours, ScoutLife life) {
        this.life = Objects.requireNonNull(life);
        this.id = Objects.requireNonNull(id);
        this.templateId = templateId;
        this.name = Objects.requireNonNull(name);
        this.voiceId = Objects.requireNonNull(voiceId);
        this.stats = new EnumMap<>(stats);
        for (StatType type : StatType.values()) {
            if (!this.stats.containsKey(type)) {
                throw new IllegalArgumentException(name + " is missing stat " + type);
            }
        }
        this.traits = new ArrayList<>(traits);
        this.stress = stress;
        this.trust = trust;
        this.injury = Objects.requireNonNull(injury);
        this.status = Objects.requireNonNull(status);
        this.recoveredAtMillis = recoveredAtMillis;
        this.healProgressHours = healProgressHours;
    }

    public String getId() {
        return id;
    }

    /** Nullable: only generated people have one (see {@code GameContent.persona}). */
    public ScoutTemplate getPersona() {
        return persona;
    }

    public void setPersona(ScoutTemplate persona) {
        this.persona = persona;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getName() {
        return name;
    }

    public String getVoiceId() {
        return voiceId;
    }

    public Stat stat(StatType type) {
        return stats.get(type);
    }

    /** Retraining: a new value and a higher ceiling. */
    public void setStat(StatType type, Stat stat) {
        stats.put(type, stat);
    }

    public Map<StatType, Stat> getStats() {
        return Collections.unmodifiableMap(stats);
    }

    public List<ScoutTrait> getTraits() {
        return Collections.unmodifiableList(traits);
    }

    public boolean hasTrait(String traitId) {
        for (ScoutTrait trait : traits) {
            if (trait.getTraitId().equals(traitId)) {
                return true;
            }
        }
        return false;
    }

    void addTrait(ScoutTrait trait) {
        traits.add(trait);
    }

    public ScoutLife getLife() {
        return life;
    }

    public int getStress() {
        return stress;
    }

    void setStress(int stress) {
        this.stress = stress;
    }

    public int getTrust() {
        return trust;
    }

    void setTrust(int trust) {
        this.trust = trust;
    }

    public Injury getInjury() {
        return injury;
    }

    void setInjury(Injury injury) {
        this.injury = injury;
    }

    public ScoutStatus getStatus() {
        return status;
    }

    void setStatus(ScoutStatus status) {
        this.status = status;
    }

    public boolean isAlive() {
        return status != ScoutStatus.DEAD;
    }

    public long getRecoveredAtMillis() {
        return recoveredAtMillis;
    }

    void setRecoveredAtMillis(long millis) {
        this.recoveredAtMillis = millis;
    }

    public double getHealProgressHours() {
        return healProgressHours;
    }

    void setHealProgressHours(double hours) {
        this.healProgressHours = hours;
    }
}
