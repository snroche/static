package com.estatica.domain.content;

/**
 * Tunable numbers from {@code assets/content/balance.json}. Fields are filled by Gson;
 * only the sections used by the code are modeled (unknown sections are ignored).
 */
public final class Balance {

    private int contentVersion;
    private Ui ui;
    private Expedition expedition;
    private Scouts scouts;
    private Checks checks;
    private Bond bond;
    private Grief grief;
    private Affinity affinity;
    private java.util.List<BandDef> bands;
    private Radio radio;
    private java.util.List<ModuleDef> modules;
    private EnergyDef energy;
    private Barks barks;
    private People people;
    private Food food;
    private Gear gear;
    private Pairs pairs;
    private Depletion depletion;
    private Weather weather;
    private Vignettes vignettes;

    public Vignettes getVignettes() {
        return vignettes;
    }

    /** Small moments in the bunker. [placeholder] */
    public static final class Vignettes {
        private double intervalHours;
        private double chance;
        /** A vignette doesn't repeat for this long. */
        private double cooldownHours;

        public double getIntervalHours() {
            return intervalHours;
        }

        public double getChance() {
            return chance;
        }

        public double getCooldownHours() {
            return cooldownHours;
        }
    }

    public Weather getWeather() {
        return weather;
    }

    /** Weather and darkness outside. [placeholder] */
    public static final class Weather {
        private int blockHours = 3;
        /** Chance a block keeps the previous block's weather. */
        private double persistence;
        private java.util.Map<String, Integer> weights;
        /** By weather: stat key (or "all") to check bonus. */
        private java.util.Map<String, java.util.Map<String, Double>> effects;
        /** At night: stat key (or "all") to check bonus. */
        private java.util.Map<String, Double> night;
        /** By weather: extra radio noise. */
        private java.util.Map<String, Double> noise;
        /** Chance a transit leg mentions the weather. */
        private double flavorChance;

        public int getBlockHours() {
            return Math.max(1, blockHours);
        }

        public double getPersistence() {
            return persistence;
        }

        public java.util.Map<String, Integer> getWeights() {
            return weights != null ? weights : java.util.Map.of();
        }

        public java.util.Map<String, java.util.Map<String, Double>> getEffects() {
            return effects != null ? effects : java.util.Map.of();
        }

        public java.util.Map<String, Double> getNight() {
            return night != null ? night : java.util.Map.of();
        }

        public java.util.Map<String, Double> getNoise() {
            return noise != null ? noise : java.util.Map.of();
        }

        public double getFlavorChance() {
            return flavorChance;
        }
    }

    public Depletion getDepletion() {
        return depletion;
    }

    /** Places run dry when looted and slowly refill. [placeholder] */
    public static final class Depletion {
        /** Added per expedition there, 0–1. */
        private double perTrip;
        private double recoveryPerDay;
        /** Loot lost when fully picked over (0.75 = a quarter left). */
        private double maxPenalty;

        public double getPerTrip() {
            return perTrip;
        }

        public double getRecoveryPerDay() {
            return recoveryPerDay;
        }

        public double getMaxPenalty() {
            return maxPenalty;
        }
    }

    public Pairs getPairs() {
        return pairs;
    }

    /** Two-scout expeditions (GDD §5.3). [placeholder] */
    public static final class Pairs {
        private double lootMultiplier = 1.0;
        /** Check bonus per point the partner is better at the stat. */
        private double supportPerPoint;
        private double maxSupport;
        /** Check bonus by relationship ("inseparable", "friends", "rivals"). */
        private java.util.Map<String, Double> relationBonus;
        /** Chance the partner carries a dying lead home. */
        private double carryBase;
        private double carryPerEndurance;
        /** Chance per transit leg that the partner says something. */
        private double chatterChance;
        /** Stress for both when rivals argue on the radio. */
        private int rivalArgueStress;
        /** Affinity gained by coming back together. */
        private double affinityPerTrip;

        public double getLootMultiplier() {
            return lootMultiplier;
        }

        public double getSupportPerPoint() {
            return supportPerPoint;
        }

        public double getMaxSupport() {
            return maxSupport;
        }

        public double relationBonus(String relation) {
            return relation == null || relationBonus == null ? 0 : relationBonus.getOrDefault(relation, 0.0);
        }

        public double getCarryBase() {
            return carryBase;
        }

        public double getCarryPerEndurance() {
            return carryPerEndurance;
        }

        public double getChatterChance() {
            return chatterChance;
        }

        public int getRivalArgueStress() {
            return rivalArgueStress;
        }

        public double getAffinityPerTrip() {
            return affinityPerTrip;
        }
    }

    public Gear getGear() {
        return gear;
    }

    /** Equipment rarity, wear and repair. [placeholder] */
    public static final class Gear {
        /** Chance of finding a piece on the way back, per band index. */
        private double[] findChance;
        /** {common, uncommon, rare} weights per band index. */
        private java.util.List<int[]> rarityWeights;
        /** Condition lost per expedition, by rarity. */
        private java.util.Map<String, Integer> wear;
        private int injuryWear;
        private int wornAt;
        /** By rarity. */
        private java.util.Map<String, java.util.Map<String, Integer>> repairCost;
        private double craftUncommonPerPoint;
        private int craftRareAbove;
        private double craftRarePerPoint;

        public double[] getFindChance() {
            return findChance;
        }

        public java.util.List<int[]> getRarityWeights() {
            return rarityWeights;
        }

        public java.util.Map<String, Integer> getWear() {
            return wear;
        }

        public int getInjuryWear() {
            return injuryWear;
        }

        public int getWornAt() {
            return wornAt;
        }

        public java.util.Map<String, java.util.Map<String, Integer>> getRepairCost() {
            return repairCost;
        }

        public double getCraftUncommonPerPoint() {
            return craftUncommonPerPoint;
        }

        public int getCraftRareAbove() {
            return craftRareAbove;
        }

        public double getCraftRarePerPoint() {
            return craftRarePerPoint;
        }
    }

    public Food getFood() {
        return food;
    }

    /** The bunker eats. [placeholder] */
    public static final class Food {
        private double rationsPerScoutPerDay;
        /** Cap on what cooks can save, 0–1. */
        private double kitchenMaxSaving;
        private double hungerStressPerHour;
        /** Healing speed while hungry. */
        private double hungerHealMultiplier = 1.0;
        /** Warn when the stores last less than this. */
        private double lowFoodDays;

        public double getRationsPerScoutPerDay() {
            return rationsPerScoutPerDay;
        }

        public double getKitchenMaxSaving() {
            return kitchenMaxSaving;
        }

        public double getHungerStressPerHour() {
            return hungerStressPerHour;
        }

        public double getHungerHealMultiplier() {
            return hungerHealMultiplier;
        }

        public double getLowFoodDays() {
            return lowFoodDays;
        }
    }

    public Barks getBarks() {
        return barks;
    }

    public People getPeople() {
        return people;
    }

    /** Generated survivors (GDD §4.4: farther = stronger). [placeholder] */
    public static final class People {
        /** Starting stat value, {@code [min, max]}. */
        private int[] statBase;
        /** Ceiling above the starting value, {@code [min, max]}. */
        private int[] ceilingGain;
        private int primaryBonus;
        private int secondaryBonus;
        /** Extra ceiling per band index (short 0, medium 1…). */
        private int bandCeilingBonus;
        private double nicknameChance;
        /** How many recent recruits a new person must not resemble. */
        private int recentToAvoid;
        /** Survivors heard on the dial waiting for rescue, at most. */
        private int maxPendingRescues;
        /** Generated people in the starting crew, besides the authored starters. */
        private int generatedStarters;

        public int getGeneratedStarters() {
            return generatedStarters;
        }

        public int[] getStatBase() {
            return statBase;
        }

        public int[] getCeilingGain() {
            return ceilingGain;
        }

        public int getPrimaryBonus() {
            return primaryBonus;
        }

        public int getSecondaryBonus() {
            return secondaryBonus;
        }

        public int getBandCeilingBonus() {
            return bandCeilingBonus;
        }

        public double getNicknameChance() {
            return nicknameChance;
        }

        public int getRecentToAvoid() {
            return recentToAvoid;
        }

        public int getMaxPendingRescues() {
            return maxPendingRescues;
        }
    }

    /** Ambient chatter in the bunker. */
    public static final class Barks {
        private int intervalMinutes;
        private double chance;

        public int getIntervalMinutes() {
            return intervalMinutes;
        }

        public double getChance() {
            return chance;
        }
    }

    public int getContentVersion() {
        return contentVersion;
    }

    public Ui getUi() {
        return ui;
    }

    public Expedition getExpedition() {
        return expedition;
    }

    public Scouts getScouts() {
        return scouts;
    }

    public Checks getChecks() {
        return checks;
    }

    public Bond getBond() {
        return bond;
    }

    public Grief getGrief() {
        return grief;
    }

    public Affinity getAffinity() {
        return affinity;
    }

    /** Bands from nearest to farthest. */
    public java.util.List<BandDef> getBands() {
        return bands;
    }

    public BandDef band(String id) {
        for (BandDef b : bands) {
            if (b.getId().equals(id)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unknown band: " + id);
    }

    /** 0-based distance rank of a band. */
    public int bandIndex(String id) {
        for (int i = 0; i < bands.size(); i++) {
            if (bands.get(i).getId().equals(id)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown band: " + id);
    }

    public Radio getRadio() {
        return radio;
    }

    public java.util.List<ModuleDef> getModules() {
        return modules;
    }

    public EnergyDef getEnergy() {
        return energy;
    }

    public ModuleDef module(String id) {
        for (ModuleDef m : modules) {
            if (m.getId().equals(id)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown module: " + id);
    }

    /** Sweeping, decoding, relays and intel (GDD §4.4, §6.1, §6.2). */
    public static final class Radio {
        private int signalRotationHours;
        private int dialMax;
        /** Signal strength (0–1) needed to lock on. */
        private double lockThreshold;
        /** Chance per point of noise that an odds hint is lost in the static. */
        private double hintLossPerNoise;
        private java.util.Map<String, Integer> relayMaintenanceCost;
        private double relayDecayPerDay;
        private double relayMinCondition;
        /** Multiplies noise and order delay in a zone with a working relay. */
        private double relayFactor;
        private double dangerIntelCheckBonus;
        private int cacheIntelLootRolls;
        /** Radio assistant: hours of work per passively caught signal. */
        private double assistantHoursPerSignal;
        private int maxScouts;
        private int survivorTrust;

        public int getSignalRotationHours() {
            return signalRotationHours;
        }

        public int getDialMax() {
            return dialMax;
        }

        public double getLockThreshold() {
            return lockThreshold;
        }

        public double getHintLossPerNoise() {
            return hintLossPerNoise;
        }

        public java.util.Map<String, Integer> getRelayMaintenanceCost() {
            return relayMaintenanceCost;
        }

        public double getRelayDecayPerDay() {
            return relayDecayPerDay;
        }

        public double getRelayMinCondition() {
            return relayMinCondition;
        }

        public double getRelayFactor() {
            return relayFactor;
        }

        public double getDangerIntelCheckBonus() {
            return dangerIntelCheckBonus;
        }

        public int getCacheIntelLootRolls() {
            return cacheIntelLootRolls;
        }

        public double getAssistantHoursPerSignal() {
            return assistantHoursPerSignal;
        }

        public int getMaxScouts() {
            return maxScouts;
        }

        public int getSurvivorTrust() {
            return survivorTrust;
        }
    }

    /** Personal relationship with the operator (GDD §5.2). Separate from trust. */
    public static final class Bond {
        /** Points needed for each level; index 0 = level 1. */
        private int[] levelThresholds;
        private int max;
        private int talkCooldownHours;
        private int initiatedCooldownHours;
        private int initiatedExpireHours;
        private int neglectGraceHours;
        private int neglectPerDay;
        /** Lost when ordered into a "mala espina" risky option. */
        private int riskyOrderPenalty;
        private int revealHiddenAtLevel;
        private int closeVoiceAtLevel;
        private int askToReturnAtLevel;
        private int giftLiked;
        private int giftDisliked;
        private int giftNeutral;
        /** Bond gained when the operator lets them come home after they asked. */
        private int retreatGranted;

        public int getRetreatGranted() {
            return retreatGranted;
        }

        public int[] getLevelThresholds() {
            return levelThresholds;
        }

        public int getMax() {
            return max;
        }

        public int getTalkCooldownHours() {
            return talkCooldownHours;
        }

        public int getInitiatedCooldownHours() {
            return initiatedCooldownHours;
        }

        public int getInitiatedExpireHours() {
            return initiatedExpireHours;
        }

        public int getNeglectGraceHours() {
            return neglectGraceHours;
        }

        public int getNeglectPerDay() {
            return neglectPerDay;
        }

        public int getRiskyOrderPenalty() {
            return riskyOrderPenalty;
        }

        public int getRevealHiddenAtLevel() {
            return revealHiddenAtLevel;
        }

        public int getCloseVoiceAtLevel() {
            return closeVoiceAtLevel;
        }

        public int getAskToReturnAtLevel() {
            return askToReturnAtLevel;
        }

        public int getGiftLiked() {
            return giftLiked;
        }

        public int getGiftDisliked() {
            return giftDisliked;
        }

        public int getGiftNeutral() {
            return giftNeutral;
        }
    }

    /** How the others react to a death (GDD §5.5). */
    public static final class Grief {
        private int baseStress;
        private double stressPerAffinity;
        private double traitChance;
        private int traitAffinityMin;
        /** Scar traits a grieving scout can gain. */
        private java.util.List<String> traits;

        public int getBaseStress() {
            return baseStress;
        }

        public double getStressPerAffinity() {
            return stressPerAffinity;
        }

        public double getTraitChance() {
            return traitChance;
        }

        public int getTraitAffinityMin() {
            return traitAffinityMin;
        }

        public java.util.List<String> getTraits() {
            return traits;
        }
    }

    /** Hidden closeness between two scouts (minimal; full relationships are Phase 5). */
    public static final class Affinity {
        private double trainingPerHour;
        private double sameTaskPerHour;
        private int max;
        private int friendAt;
        private int inseparableAt;
        private int rivalAt;
        /** Stress relief per hour (friends) or stress (rivals) when sharing a task. */
        private double sharedTaskEffect;

        public int getFriendAt() {
            return friendAt;
        }

        public int getInseparableAt() {
            return inseparableAt;
        }

        public int getRivalAt() {
            return rivalAt;
        }

        public double getSharedTaskEffect() {
            return sharedTaskEffect;
        }

        public double getTrainingPerHour() {
            return trainingPerHour;
        }

        public double getSameTaskPerHour() {
            return sameTaskPerHour;
        }

        public int getMax() {
            return max;
        }
    }

    public static final class Ui {
        private int typewriterCharMillis;
        private int logWindowLines;
        private int logPageSize;

        public int getTypewriterCharMillis() {
            return typewriterCharMillis;
        }

        public int getLogWindowLines() {
            return logWindowLines;
        }

        public int getLogPageSize() {
            return logPageSize;
        }
    }

    public static final class Expedition {
        private int situationTimeoutMinutes;
        /** Chance a scout below their trust threshold ignores an order. */
        private double disobeyChance;

        public int getSituationTimeoutMinutes() {
            return situationTimeoutMinutes;
        }

        public double getDisobeyChance() {
            return disobeyChance;
        }
    }

    public static final class Scouts {
        private int statMax;
        private int initialStress;
        private int initialTrust;
        private int maxStress;
        /** Stress at or above this counts as a warning before a lethal situation. */
        private int stressWarning;
        private double stressRecoveryPerHour;
        private int stressOnFailure;
        private int stressOnSuccess;
        /** Each point of Nerve reduces incoming stress by this fraction. */
        private double nerveStressReductionPerPoint;
        private int trustOnSuccess;
        private int trustOnFailure;
        private int xpOnSuccess;
        private int xpOnFailure;
        /** XP needed to level a stat = xpPerLevel × current value. */
        private int xpPerLevel;
        /** Stat penalty per injury level: index 0 = none … 3 = critical. */
        private int[] injuryStatPenalty;
        /** Hours resting in the bunker to heal one injury level. */
        private double injuryHealHoursPerLevel;

        public int getStatMax() {
            return statMax;
        }

        public int getInitialStress() {
            return initialStress;
        }

        public int getInitialTrust() {
            return initialTrust;
        }

        public int getMaxStress() {
            return maxStress;
        }

        public int getStressWarning() {
            return stressWarning;
        }

        public double getStressRecoveryPerHour() {
            return stressRecoveryPerHour;
        }

        public int getStressOnFailure() {
            return stressOnFailure;
        }

        public int getStressOnSuccess() {
            return stressOnSuccess;
        }

        public double getNerveStressReductionPerPoint() {
            return nerveStressReductionPerPoint;
        }

        public int getTrustOnSuccess() {
            return trustOnSuccess;
        }

        public int getTrustOnFailure() {
            return trustOnFailure;
        }

        public int getXpOnSuccess() {
            return xpOnSuccess;
        }

        public int getXpOnFailure() {
            return xpOnFailure;
        }

        public int getXpPerLevel() {
            return xpPerLevel;
        }

        public int[] getInjuryStatPenalty() {
            return injuryStatPenalty;
        }

        public double getInjuryHealHoursPerLevel() {
            return injuryHealHoursPerLevel;
        }
    }

    public static final class Checks {
        private double baseChance;
        private double perPoint;
        private double perDanger;
        private double minChance;
        private double maxChance;
        /** Stress above this starts lowering every check. */
        private int stressPenaltyFrom;
        private double stressPenaltyPerPoint;
        /** Chance at or above → "good" hint. */
        private double hintGood;
        /** Chance below → "bad" hint ("esto me da mala espina"). */
        private double hintBad;

        public double getBaseChance() {
            return baseChance;
        }

        public double getPerPoint() {
            return perPoint;
        }

        public double getPerDanger() {
            return perDanger;
        }

        public double getMinChance() {
            return minChance;
        }

        public double getMaxChance() {
            return maxChance;
        }

        public int getStressPenaltyFrom() {
            return stressPenaltyFrom;
        }

        public double getStressPenaltyPerPoint() {
            return stressPenaltyPerPoint;
        }

        public double getHintGood() {
            return hintGood;
        }

        public double getHintBad() {
            return hintBad;
        }
    }
}
