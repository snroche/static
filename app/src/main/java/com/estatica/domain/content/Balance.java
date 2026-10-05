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
