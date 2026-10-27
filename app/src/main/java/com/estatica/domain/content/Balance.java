package com.estatica.domain.content;

import java.util.List;

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
    private Omens omens;
    private Romance romance;

    public Romance getRomance() {
        return romance != null ? romance : new Romance();
    }

    /** Romance with the operator. [placeholder] */
    public static final class Romance {
        /** Meter at which "something changed" shows (flirting). */
        private int sparkAt = 40;
        /** Hours without attention before a partner feels neglected. */
        private double neglectHours = 36;
        private double neglectPerDay = 8;
        /** A partner ends it below this meter, bond level or trust. */
        private int endBelow = 25;
        private int endBelowBondLevel = 3;
        private int endBelowTrust = 15;
        private int afterBreakup = 20;
        private int breakupStress = 14;
        private int breakupBond = 8;
        /** No flirting this long after a breakup. */
        private double cooldownHours = 72;
        /** Per hour, times jealousy/100, times other partners. */
        private double jealousyPerHour = 0.02;
        private int jealousyStress = 4;
        private int jealousyMeter = 4;
        private double jealousyAffinity = 3;
        /** Romance from a gift to someone open to you. */
        private int gift = 3;

        public int getSparkAt() {
            return sparkAt;
        }

        public double getNeglectHours() {
            return neglectHours;
        }

        public double getNeglectPerDay() {
            return neglectPerDay;
        }

        public int getEndBelow() {
            return endBelow;
        }

        public int getEndBelowBondLevel() {
            return endBelowBondLevel;
        }

        public int getEndBelowTrust() {
            return endBelowTrust;
        }

        public int getAfterBreakup() {
            return afterBreakup;
        }

        public int getBreakupStress() {
            return breakupStress;
        }

        public int getBreakupBond() {
            return breakupBond;
        }

        public double getCooldownHours() {
            return cooldownHours;
        }

        public double getJealousyPerHour() {
            return jealousyPerHour;
        }

        public int getJealousyStress() {
            return jealousyStress;
        }

        public int getJealousyMeter() {
            return jealousyMeter;
        }

        public double getJealousyAffinity() {
            return jealousyAffinity;
        }

        public int getGift() {
            return gift;
        }
    }
    private List<RoomDef> rooms;
    private Rooms roomTuning;

    public List<RoomDef> getRooms() {
        return rooms != null ? rooms : List.of();
    }

    public RoomDef room(String id) {
        for (RoomDef r : getRooms()) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    public Rooms getRoomTuning() {
        return roomTuning != null ? roomTuning : new Rooms();
    }

    /** What the room tasks do per hour. [placeholder] */
    public static final class Rooms {
        /** Rations per hour per person tending the hydroponics bay. */
        private double hydroRationsPerHour = 0.06;
        /** Hydroponics never covers more than this share of what the crew eats. */
        private double hydroMaxShare = 0.25;
        /** Reserve per hour per person on hydroponics (grow lamps, pumps). */
        private double hydroReservePerHour = 0.3;
        private double blightChancePerDay = 0.08;
        private double blightHours = 24;
        /** XP per hour for everyone at home, per teacher. */
        private double teachXpPerHour = 0.25;
        /** Defense the lookout adds when a raid comes. */
        private int lookoutDefense = 4;

        public double getHydroRationsPerHour() {
            return hydroRationsPerHour;
        }

        public double getHydroMaxShare() {
            return hydroMaxShare;
        }

        public double getHydroReservePerHour() {
            return hydroReservePerHour;
        }

        public double getBlightChancePerDay() {
            return blightChancePerDay;
        }

        public double getBlightHours() {
            return blightHours;
        }

        public double getTeachXpPerHour() {
            return teachXpPerHour;
        }

        public int getLookoutDefense() {
            return lookoutDefense;
        }
    }
    private Treatment treatment;

    public Treatment getTreatment() {
        return treatment != null ? treatment : new Treatment();
    }

    /** Treating an injury on the spot in the infirmary. [placeholder] */
    public static final class Treatment {
        /** Medicine per injury level healed (light 1 level, critical 3). */
        private int medicinePerLevel = 2;
        /** After "not now", they don't offer again for this long. */
        private double declineCooldownHours = 6;
        /** Healers with any of these traits don't ask: they just do it. */
        private java.util.List<String> eagerTraits;

        public int getMedicinePerLevel() {
            return medicinePerLevel;
        }

        public double getDeclineCooldownHours() {
            return declineCooldownHours;
        }

        public java.util.List<String> getEagerTraits() {
            return eagerTraits != null ? eagerTraits : java.util.List.of();
        }
    }

    public Omens getOmens() {
        return omens != null ? omens : new Omens();
    }

    /** How often the bunker gets a scare (StoryDef.Omen). [placeholder] */
    public static final class Omens {
        private double intervalHours = 5;
        private double chance = 0.35;
        /** An omen doesn't repeat for this long. */
        private double cooldownHours = 72;

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

    private Market market = new Market();
    private Workshop workshop = new Workshop();

    public Market getMarket() {
        return market != null ? market : new Market();
    }

    public Workshop getWorkshop() {
        return workshop != null ? workshop : new Workshop();
    }

    private Link link = new Link();

    public Link getLink() {
        return link != null ? link : new Link();
    }

    /** Other bunkers: codes, QR and Bluetooth (GDD §12). [placeholder] */
    public static final class Link {
        /** Codes from players taken per local day (broadcasts and nearby links don't count). */
        private int dailyLimit = 3;
        private int messageMaxChars = 140;
        private int noteMaxChars = 80;
        private int wordsMaxChars = 160;
        /** A drop: at most this many units, and pieces of gear. */
        private int dropMaxUnits = 40;
        private int dropMaxGear = 2;
        private List<String> dropResources = List.of("scrap", "components", "parts", "rations", "medicine",
                "batteries", "fuel", "relay_kit", "chips");
        /** Stress on everyone at home when a stranger's last words come in. */
        private int lastWordsStress = 3;
        /** Codes sent, kept to show again. */
        private int sentKept = 10;
        /** ECDSA P-256 public key (X.509, base64) that signs developer broadcasts. */
        private String broadcastKey = "";
        private Wanderer wanderer = new Wanderer();
        /** By scheme: the most of each resource decoding another bunker's message can give. */
        private java.util.Map<String, java.util.Map<String, Integer>> decodeRewards = new java.util.HashMap<>();

        public java.util.Map<String, java.util.Map<String, Integer>> getDecodeRewards() {
            return decodeRewards != null ? decodeRewards : java.util.Map.of();
        }

        public int getDailyLimit() {
            return dailyLimit;
        }

        public int getMessageMaxChars() {
            return messageMaxChars;
        }

        public int getNoteMaxChars() {
            return noteMaxChars;
        }

        public int getWordsMaxChars() {
            return wordsMaxChars;
        }

        public int getDropMaxUnits() {
            return dropMaxUnits;
        }

        public int getDropMaxGear() {
            return dropMaxGear;
        }

        public List<String> getDropResources() {
            return dropResources != null ? dropResources : List.of();
        }

        public int getLastWordsStress() {
            return lastWordsStress;
        }

        public int getSentKept() {
            return sentKept;
        }

        public String getBroadcastKey() {
            return broadcastKey != null ? broadcastKey : "";
        }

        public Wanderer getWanderer() {
            return wanderer != null ? wanderer : new Wanderer();
        }
    }

    /** Someone from another bunker, met on the road. */
    public static final class Wanderer {
        /** How long they wait before moving on. */
        private double days = 3;
        /** Stress the team they meet sheds. */
        private int stressRelief = 8;
        /** By their strongest stat: what they leave. */
        private java.util.Map<String, java.util.Map<String, Integer>> gifts = new java.util.HashMap<>();
        /** By their strongest stat: intel they share on another place (CACHE or DANGER). */
        private java.util.Map<String, String> intel = new java.util.HashMap<>();

        public double getDays() {
            return days;
        }

        public int getStressRelief() {
            return stressRelief;
        }

        public java.util.Map<String, java.util.Map<String, Integer>> getGifts() {
            return gifts != null ? gifts : java.util.Map.of();
        }

        public java.util.Map<String, String> getIntel() {
            return intel != null ? intel : java.util.Map.of();
        }
    }

    /** THE FAIR: the trader on the radio. Prices in chips. [placeholder] */
    public static final class Market {
        private String station = "st_feria";
        /** The place on the map where goods can be picked up (no fee). */
        private String zone = "feria";
        /** Courier's cut on top of the price, and how long it takes. */
        private double courierFee = 0.15;
        private double courierHours = 4;
        private int startReputation = 50;
        private int reputationPerTrade = 1;
        /** ±share on prices at reputation 0 / 100. */
        private double reputationSwing = 0.1;
        private int rareStockReputation = 70;
        private double saturationPerUnit = 0.04;
        private double saturationFloor = 0.5;
        private int wantedPerDay = 2;
        private double wantedMultiplier = 1.5;
        private int scarcePerDay = 1;
        private double scarceMultiplier = 1.5;
        private int blueprintsPerDay = 2;
        private int gearPerDay = 2;
        private int blueprintBase = 15;
        private int blueprintPerCraft = 6;
        /** Good id to {buy, sell}. */
        private java.util.Map<String, int[]> goods = new java.util.LinkedHashMap<>();
        private java.util.Map<String, Integer> gearBuy = new java.util.HashMap<>();
        private java.util.Map<String, Integer> gearSell = new java.util.HashMap<>();

        public String getStation() {
            return station;
        }

        public String getZone() {
            return zone;
        }

        public double getCourierFee() {
            return courierFee;
        }

        public double getCourierHours() {
            return courierHours;
        }

        public int getStartReputation() {
            return startReputation;
        }

        public int getReputationPerTrade() {
            return reputationPerTrade;
        }

        public double getReputationSwing() {
            return reputationSwing;
        }

        public int getRareStockReputation() {
            return rareStockReputation;
        }

        public double getSaturationPerUnit() {
            return saturationPerUnit;
        }

        public double getSaturationFloor() {
            return saturationFloor;
        }

        public int getWantedPerDay() {
            return wantedPerDay;
        }

        public double getWantedMultiplier() {
            return wantedMultiplier;
        }

        public int getScarcePerDay() {
            return scarcePerDay;
        }

        public double getScarceMultiplier() {
            return scarceMultiplier;
        }

        public int getBlueprintsPerDay() {
            return blueprintsPerDay;
        }

        public int getGearPerDay() {
            return gearPerDay;
        }

        public int getBlueprintBase() {
            return blueprintBase;
        }

        public int getBlueprintPerCraft() {
            return blueprintPerCraft;
        }

        public java.util.Map<String, int[]> getGoods() {
            return goods != null ? goods : java.util.Map.of();
        }

        public java.util.Map<String, Integer> getGearBuy() {
            return gearBuy != null ? gearBuy : java.util.Map.of();
        }

        public java.util.Map<String, Integer> getGearSell() {
            return gearSell != null ? gearSell : java.util.Map.of();
        }
    }

    /** The bench: who works it and how well. [placeholder] */
    public static final class Workshop {
        /** Speed at the bench: base + perCraft × the best Craft there. */
        private double speedBase = 0.6;
        private double speedPerCraft = 0.08;
        /** Jobs that can wait in line beyond the benches. */
        private int queue = 3;
        /** Craft XP per hour of work, for whoever is at the bench. */
        private double xpPerHour = 0.8;
        /** Chance per day that someone at the bench works out a plan they're good enough for. */
        private double workOutChancePerDay = 0.2;
        /** Chance per expedition home of finding a blueprint, by band (index). */
        private double[] findChance = {0.04, 0.08, 0.12, 0.16};
        /** Upgrading a piece to the next rarity: Craft needed, and parts/components per step. */
        private int[] upgradeCraft = {5, 7};
        private java.util.Map<String, Integer> upgradeCost = new java.util.HashMap<>();
        /** What a piece gives back when salvaged, by rarity. */
        private java.util.Map<String, java.util.Map<String, Integer>> salvage = new java.util.HashMap<>();

        public double getSpeedBase() {
            return speedBase;
        }

        public double getSpeedPerCraft() {
            return speedPerCraft;
        }

        public int getQueue() {
            return queue;
        }

        public double getXpPerHour() {
            return xpPerHour;
        }

        public double getWorkOutChancePerDay() {
            return workOutChancePerDay;
        }

        public double findChance(int bandIndex) {
            return findChance.length == 0 ? 0 : findChance[Math.min(bandIndex, findChance.length - 1)];
        }

        /** Craft needed to raise a piece from {@code rarity} to the next one, or -1 at the top. */
        public int upgradeCraft(int fromRarityOrdinal) {
            return fromRarityOrdinal < upgradeCraft.length ? upgradeCraft[fromRarityOrdinal] : -1;
        }

        public java.util.Map<String, Integer> getUpgradeCost() {
            return upgradeCost != null ? upgradeCost : java.util.Map.of();
        }

        public java.util.Map<String, Integer> salvage(String rarity) {
            return salvage != null ? salvage.getOrDefault(rarity, java.util.Map.of()) : java.util.Map.of();
        }
    }

    private Retrain retrain = new Retrain();
    private Fuel fuel = new Fuel();
    private DeepRuns deepRuns = new DeepRuns();

    public Retrain getRetrain() {
        return retrain != null ? retrain : new Retrain();
    }

    public Fuel getFuel() {
        return fuel != null ? fuel : new Fuel();
    }

    public DeepRuns getDeepRuns() {
        return deepRuns != null ? deepRuns : new DeepRuns();
    }

    /** Retraining once every stat is maxed. [placeholder] */
    public static final class Retrain {
        private int ceilingGain = 1;
        private int focusGain = 1;
        /** Share of each stat kept. */
        private double keep = 0.5;
        private double farPenaltyPerTier = 0.04;

        public int getCeilingGain() {
            return ceilingGain;
        }

        public int getFocusGain() {
            return focusGain;
        }

        public double getKeep() {
            return keep;
        }

        public double getFarPenaltyPerTier() {
            return farPenaltyPerTier;
        }
    }

    /** Fuel: rides with engines and the generator in a blackout. [placeholder] */
    public static final class Fuel {
        /** Burned per blackout hour to cover the missing watts. */
        private double generatorPerHour = 1.0;

        public double getGeneratorPerHour() {
            return generatorPerHour;
        }
    }

    /** Deep runs past the map's edge (VLF): never picked clean, harder by the tier. [placeholder] */
    public static final class DeepRuns {
        /** Antenna level per tier (index 0 = tier 1). */
        private int[] tierAntenna = {7, 7, 8};
        private double durationPerTier = 0.5;
        private double lootPerTier = 0.6;
        /** Odds lost per tier above the first. */
        private double oddsPerTier = 0.08;
        private int dangerPerTier = 1;

        public int[] getTierAntenna() {
            return tierAntenna;
        }

        public int tiers() {
            return tierAntenna.length;
        }

        public double durationFactor(int tier) {
            return 1 + Math.max(0, tier - 1) * durationPerTier;
        }

        public double lootFactor(int tier) {
            return 1 + Math.max(0, tier - 1) * lootPerTier;
        }

        public double oddsPenalty(int tier) {
            return Math.max(0, tier - 1) * oddsPerTier;
        }

        public int extraDanger(int tier) {
            return Math.max(0, tier - 1) * dangerPerTier;
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

        /** Three on a trip. */
        private double trioLootMultiplier = 1.7;
        /** Two companions carrying a dying lead: a better chance. */
        private double secondCarrierBonus = 0.15;

        public double getTrioLootMultiplier() {
            return trioLootMultiplier;
        }

        public double getSecondCarrierBonus() {
            return secondCarrierBonus;
        }

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
        /** Condition lost per hour out, per failed check, and per injury (by severity). */
        private double wearPerHour = 1.5;
        private double wearPerFailure = 2;
        private java.util.Map<String, Integer> wearPerInjury = new java.util.HashMap<>();
        /** Better pieces wear slower: multiplier by rarity. */
        private java.util.Map<String, Double> rarityWear = new java.util.HashMap<>();
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

        public double getWearPerHour() {
            return wearPerHour;
        }

        public double getWearPerFailure() {
            return wearPerFailure;
        }

        public int wearPerInjury(String injury) {
            return wearPerInjury != null ? wearPerInjury.getOrDefault(injury, 0) : 0;
        }

        public double rarityWear(String rarity) {
            return rarityWear != null ? rarityWear.getOrDefault(rarity, 1.0) : 1.0;
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
        private int talkCooldownMinutes;
        private int initiatedCooldownHours;
        private int initiatedExpireHours;
        /** How often anyone may come to talk at all, and the odds when they could. */
        private int initiatedCheckMinutes = 60;
        private int initiatedGapHours = 6;
        private double initiatedChance = 0.15;
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

        /** Bond for their favorite thing, and how much a gift they like (or love) eases them. */
        private int giftFavorite = 25;
        private int likedStressRelief = 10;
        private int favoriteStressRelief = 20;
        /** From this bond level, a gift they like gets paid back in their own way. */
        private int thanksAtLevel = 3;
        /** Hours until they pay it back, and how long before they will again. */
        private double thanksDelayHours = 2;
        private double thanksCooldownHours = 20;
        /** XP each person home gets when someone teaches them a little. */
        private int teachXp = 6;
        /** Once the bond is full, a share of whatever would have raised it eases stress instead. */
        private double fullBondReliefShare = 0.5;
        /** Talking to someone with nothing new to say: a little company, for this much stress. */
        private int companyStressRelief = 3;

        public int getGiftFavorite() {
            return giftFavorite;
        }

        public int getLikedStressRelief() {
            return likedStressRelief;
        }

        public int getFavoriteStressRelief() {
            return favoriteStressRelief;
        }

        public int getThanksAtLevel() {
            return thanksAtLevel;
        }

        public double getThanksDelayHours() {
            return thanksDelayHours;
        }

        public double getThanksCooldownHours() {
            return thanksCooldownHours;
        }

        public int getTeachXp() {
            return teachXp;
        }

        public double getFullBondReliefShare() {
            return fullBondReliefShare;
        }

        public int getCompanyStressRelief() {
            return companyStressRelief;
        }

        public int[] getLevelThresholds() {
            return levelThresholds;
        }

        public int getMax() {
            return max;
        }

        public int getTalkCooldownMinutes() {
            return talkCooldownMinutes;
        }

        public int getInitiatedCooldownHours() {
            return initiatedCooldownHours;
        }

        public int getInitiatedExpireHours() {
            return initiatedExpireHours;
        }

        public int getInitiatedCheckMinutes() {
            return initiatedCheckMinutes;
        }

        public int getInitiatedGapHours() {
            return initiatedGapHours;
        }

        public double getInitiatedChance() {
            return initiatedChance;
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
        /** A situation seen this recently only comes up once the fresh ones run out. */
        private double eventRecentHours = 48;
        /** Between one step of a multi-step situation and the next. */
        private int followUpSeconds = 45;

        public double getEventRecentHours() {
            return eventRecentHours;
        }

        public long getFollowUpMillis() {
            return Math.max(1, followUpSeconds) * 1000L;
        }

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
