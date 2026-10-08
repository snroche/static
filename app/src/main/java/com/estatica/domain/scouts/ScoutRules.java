package com.estatica.domain.scouts;

import com.estatica.domain.content.Balance;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.GearDef;
import com.estatica.domain.content.TraitDef;

import java.util.ArrayList;
import java.util.List;

/** Rules that change a scout: stats, growth, stress, trust, injuries, idle recovery. */
public final class ScoutRules {

    private static final double MILLIS_PER_HOUR = 3_600_000.0;
    private static final int TRUST_MAX = 100;

    private final GameContent content;
    private final Balance.Scouts balance;

    public ScoutRules(GameContent content) {
        this.content = content;
        this.balance = content.getBalance().getScouts();
    }

    /** Trait definitions, hidden ones included (they act even before being revealed). */
    public List<TraitDef> traitsOf(Scout scout) {
        List<TraitDef> defs = new ArrayList<>(scout.getTraits().size());
        for (ScoutTrait trait : scout.getTraits()) {
            defs.add(content.trait(trait.getTraitId()));
        }
        return defs;
    }

    /** Stat value used in checks: value − injury penalty + trait bonuses, never below 0. */
    public int effectiveStat(Scout scout, StatType type) {
        int value = scout.stat(type).getValue();
        value -= balance.getInjuryStatPenalty()[scout.getInjury().ordinal()];
        for (TraitDef trait : traitsOf(scout)) {
            value += trait.statBonus(type.key());
        }
        for (com.estatica.domain.bunker.GearPiece piece : working(scout)) {
            value += com.estatica.domain.bunker.GearRules.statBonus(content.gear(piece.getGearId()), piece, type.key());
        }
        return Math.max(0, value);
    }

    public double checkBonus(Scout scout) {
        double bonus = 0;
        for (TraitDef trait : traitsOf(scout)) {
            bonus += trait.getCheckBonus();
        }
        return bonus;
    }

    public double lootMultiplier(Scout scout) {
        double m = 1.0;
        for (TraitDef trait : traitsOf(scout)) {
            m *= trait.getLootMultiplier();
        }
        for (com.estatica.domain.bunker.GearPiece piece : working(scout)) {
            m *= com.estatica.domain.bunker.GearRules.lootMultiplier(content.gear(piece.getGearId()), piece);
        }
        return m;
    }

    /** Pieces worn that still work (broken ones do nothing). */
    public List<com.estatica.domain.bunker.GearPiece> working(Scout scout) {
        List<com.estatica.domain.bunker.GearPiece> out = new ArrayList<>();
        for (com.estatica.domain.bunker.GearPiece piece : scout.getLife().getEquipped()) {
            if (!piece.isBroken() && content.gear(piece.getGearId()) != null) {
                out.add(piece);
            }
        }
        return out;
    }

    /** A working piece that absorbs an injury (first-aid kit), or null. */
    public com.estatica.domain.bunker.GearPiece injuryAbsorber(Scout scout) {
        for (com.estatica.domain.bunker.GearPiece piece : working(scout)) {
            if (content.gear(piece.getGearId()).isAbsorbsInjury()) {
                return piece;
            }
        }
        return null;
    }

    /**
     * Puts a piece on in its slot; whatever was there goes back to the bunker.
     *
     * @return the piece taken off, or null
     */
    public com.estatica.domain.bunker.GearPiece equip(Scout scout, com.estatica.domain.bunker.GearPiece piece) {
        GearDef def = content.gear(piece.getGearId());
        com.estatica.domain.bunker.GearPiece old = scout.getLife().wear(def.getSlot(), piece);
        if (old != null) {
            old.moveTo(com.estatica.domain.bunker.GearPiece.BUNKER);
        }
        piece.moveTo(com.estatica.domain.bunker.GearPiece.carriedBy(scout.getId()));
        return old;
    }

    /** Takes off whatever is in a slot and puts it back in the bunker. @return it, or null */
    public com.estatica.domain.bunker.GearPiece unequip(Scout scout, GearDef.Slot slot) {
        com.estatica.domain.bunker.GearPiece old = scout.getLife().wear(slot, null);
        if (old != null) {
            old.moveTo(com.estatica.domain.bunker.GearPiece.BUNKER);
        }
        return old;
    }

    /** Noise multiplier from radios worn (1 without). */
    public double gearNoiseFactor(Scout scout) {
        double factor = 1.0;
        for (com.estatica.domain.bunker.GearPiece piece : working(scout)) {
            factor *= com.estatica.domain.bunker.GearRules.noiseFactor(content.gear(piece.getGearId()), piece);
        }
        return factor;
    }

    public double durationMultiplier(Scout scout) {
        double m = 1.0;
        for (com.estatica.domain.bunker.GearPiece piece : working(scout)) {
            m *= content.gear(piece.getGearId()).getDurationMultiplier();
        }
        for (TraitDef trait : traitsOf(scout)) {
            m *= trait.getDurationMultiplier();
        }
        return m;
    }

    /** Trust below which the scout may ignore orders; 0 if no trait makes them disobey. */
    public int disobeyThreshold(Scout scout) {
        int threshold = 0;
        for (TraitDef trait : traitsOf(scout)) {
            threshold = Math.max(threshold, trait.getDisobeyBelowTrust());
        }
        return threshold;
    }

    public TraitDef.Bias bias(Scout scout) {
        for (TraitDef trait : traitsOf(scout)) {
            if (trait.getBias() != TraitDef.Bias.NONE) {
                return trait.getBias();
            }
        }
        return TraitDef.Bias.NONE;
    }

    /**
     * Applies stress. Increases are scaled by traits and reduced by Nerve; decreases are
     * applied as-is.
     *
     * @return the amount actually applied
     */
    public int addStress(Scout scout, int delta) {
        double scaled = delta;
        if (delta > 0) {
            for (TraitDef trait : traitsOf(scout)) {
                scaled *= trait.getStressMultiplier();
            }
            double reduction = scout.stat(StatType.NERVE).getValue()
                    * balance.getNerveStressReductionPerPoint();
            scaled *= Math.max(0.2, 1.0 - reduction);
        }
        int before = scout.getStress();
        scout.setStress(clamp(before + (int) Math.round(scaled), 0, balance.getMaxStress()));
        return scout.getStress() - before;
    }

    public void addTrust(Scout scout, int delta) {
        scout.setTrust(clamp(scout.getTrust() + delta, 0, TRUST_MAX));
    }

    /** @return true if the stat went up a point */
    public boolean train(Scout scout, StatType type, boolean success) {
        int xp = success ? balance.getXpOnSuccess() : balance.getXpOnFailure();
        return scout.stat(type).gainXp(xp, balance.getXpPerLevel());
    }

    /** Adds raw XP (bunker tasks). @return true if the stat went up a point */
    public boolean trainXp(Scout scout, StatType type, int xp) {
        return scout.stat(type).gainXp(xp, balance.getXpPerLevel());
    }

    /**
     * Whether someone fits a requirement: any of the traits (visible ones only, so a choice
     * never gives away a hidden trait) and the background. Empty requirements always fit.
     */
    public boolean fits(Scout scout, java.util.List<String> anyTrait, String background) {
        if (!anyTrait.isEmpty()) {
            boolean any = false;
            for (ScoutTrait t : scout.getTraits()) {
                any |= t.isVisible() && anyTrait.contains(t.getTraitId());
            }
            if (!any) {
                return false;
            }
        }
        if (background != null) {
            com.estatica.domain.content.ScoutTemplate persona = content.persona(scout);
            return persona != null && background.equals(persona.getBackground());
        }
        return true;
    }

    /** How they are doing, at a glance: content, steady, tense or breaking. */
    public String mood(Scout scout) {
        if (scout.getStress() >= 75) {
            return "breaking";
        }
        if (scout.getStress() >= 45) {
            return "tense";
        }
        return bondLevel(scout) >= 3 && scout.getStress() < 25 ? "content" : "steady";
    }

    // ---- Bond (GDD §5.2) ----

    /** 1-based bond level from the thresholds in balance.json. */
    public int bondLevel(Scout scout) {
        int[] thresholds = content.getBalance().getBond().getLevelThresholds();
        int level = 0;
        for (int threshold : thresholds) {
            if (scout.getLife().getBond() >= threshold) {
                level++;
            }
        }
        return Math.max(1, level);
    }

    /** @return levels gained (negative if lost) */
    public int addBond(Scout scout, int delta) {
        int before = bondLevel(scout);
        int max = content.getBalance().getBond().getMax();
        scout.getLife().setBond(clamp(scout.getLife().getBond() + delta, 0, max));
        return bondLevel(scout) - before;
    }

    /** Warmer phrases and nicknames once the bond is high enough. */
    public boolean isClose(Scout scout) {
        return bondLevel(scout) >= content.getBalance().getBond().getCloseVoiceAtLevel();
    }

    /** GDD §5.5: with a high bond, a warned scout asks to come home. */
    public boolean asksToReturn(Scout scout) {
        return bondLevel(scout) >= content.getBalance().getBond().getAskToReturnAtLevel()
                && isWarned(scout);
    }

    /**
     * Reveals the hidden trait once the bond reaches the configured level.
     *
     * @return the revealed trait id, or null
     */
    public String revealHiddenIfDue(Scout scout) {
        if (bondLevel(scout) < content.getBalance().getBond().getRevealHiddenAtLevel()) {
            return null;
        }
        return revealHidden(scout);
    }

    /** @return the revealed trait id, or null if there was nothing hidden */
    public String revealHidden(Scout scout) {
        for (ScoutTrait trait : scout.getTraits()) {
            if (!trait.isVisible()) {
                trait.reveal();
                return trait.getTraitId();
            }
        }
        return null;
    }

    /** @return false if the scout already had it */
    public boolean addTrait(Scout scout, String traitId) {
        if (scout.hasTrait(traitId)) {
            return false;
        }
        scout.addTrait(new ScoutTrait(traitId, false, false));
        return true;
    }

    public boolean hasSpecialization(Scout scout) {
        for (TraitDef trait : traitsOf(scout)) {
            if (trait.getKind() == TraitDef.Kind.SPECIALIZATION) {
                return true;
            }
        }
        return false;
    }

    /** Healing bonus this scout gives others from the infirmary (traits, specialization). */
    public double healOthersMultiplier(Scout scout) {
        double m = 1.0;
        for (TraitDef trait : traitsOf(scout)) {
            m *= trait.getHealOthersMultiplier();
        }
        return m;
    }

    public void injure(Scout scout, Injury incoming) {
        scout.setInjury(scout.getInjury().combine(incoming));
    }

    public void setInjury(Scout scout, Injury injury) {
        scout.setInjury(injury);
    }

    /**
     * GDD §5.5 "no death without warning": a scout counts as warned when already seriously
     * hurt or highly stressed. (A bad-odds hint on the chosen option also counts; the
     * expedition engine checks that.)
     */
    public boolean isWarned(Scout scout) {
        return scout.getInjury().ordinal() >= Injury.SERIOUS.ordinal()
                || scout.getStress() >= balance.getStressWarning();
    }

    /** Catch-up for life in the bunker: stress and injuries recover with elapsed time. */
    public void recoverIdle(Scout scout, long nowMillis) {
        recoverIdle(scout, nowMillis, 1.0, 1.0, 0.0);
    }

    /**
     * Idle recovery with bunker task effects.
     *
     * @param stressMultiplier own stress recovery (e.g. resting)
     * @param healMultiplier   healing speed (e.g. someone in the infirmary)
     * @param extraReliefPerHour stress removed by others (kitchen, infirmary…)
     */
    public void recoverIdle(Scout scout, long nowMillis, double stressMultiplier,
                            double healMultiplier, double extraReliefPerHour) {
        if (scout.getStatus() != ScoutStatus.IDLE) {
            return;
        }
        double hours = Math.max(0, nowMillis - scout.getRecoveredAtMillis()) / MILLIS_PER_HOUR;
        scout.setRecoveredAtMillis(Math.max(nowMillis, scout.getRecoveredAtMillis()));
        if (hours <= 0) {
            return;
        }
        double total = scout.getLife().getStressCarry()
                + hours * (balance.getStressRecoveryPerHour() * stressMultiplier + extraReliefPerHour);
        int recovered = (int) Math.floor(total);
        scout.setStress(clamp(scout.getStress() - recovered, 0, balance.getMaxStress()));
        // Calm is not banked: at zero stress the leftover is dropped.
        scout.getLife().setStressCarry(scout.getStress() == 0 ? 0 : total - recovered);

        if (scout.getInjury() == Injury.NONE) {
            scout.setHealProgressHours(0);
            return;
        }
        double progress = scout.getHealProgressHours() + hours * healMultiplier;
        double perLevel = balance.getInjuryHealHoursPerLevel();
        while (scout.getInjury() != Injury.NONE && progress >= perLevel) {
            progress -= perLevel;
            scout.setInjury(scout.getInjury().better());
        }
        scout.setHealProgressHours(scout.getInjury() == Injury.NONE ? 0 : progress);
    }

    public void markDeparted(Scout scout, long atMillis) {
        recoverIdle(scout, atMillis);
        scout.setStatus(ScoutStatus.AWAY);
    }

    public void markReturned(Scout scout, long atMillis) {
        scout.setStatus(ScoutStatus.IDLE);
        scout.setRecoveredAtMillis(atMillis);
    }

    public void markDead(Scout scout) {
        scout.setStatus(ScoutStatus.DEAD);
    }

    public void markDead(Scout scout, long atMillis, String zoneId) {
        scout.setStatus(ScoutStatus.DEAD);
        scout.getLife().markDied(atMillis, zoneId);
    }

    /** Whether the scout can be sent out now. */
    public boolean canDispatch(Scout scout) {
        return scout.getStatus() == ScoutStatus.IDLE && scout.getInjury() != Injury.CRITICAL;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
