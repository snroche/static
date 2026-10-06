package com.estatica.domain.content;

import java.util.List;

/**
 * Energy and bunker events tuning (GDD §6.4, §5.3 Guardia), from
 * {@code balance.json → energy / raids}. Filled by Gson.
 */
public final class EnergyDef {

    /** Watts produced per generator level (index 0 = level 1). */
    private int[] generatorWatts;
    private List<Consumer> consumers;
    private int nightStartHour;
    private int nightEndHour;
    private double batteryBase;
    private double batteryPerGeneratorLevel;
    private double chargePerSpareWattHour;
    private double sweepCost;
    private double rechargePerBattery;
    /** Stress per hour for everyone in the bunker while the filters are short. */
    private double badAirStressPerHour;
    private double blackoutChancePerDay;
    private int blackoutMinHours;
    private int blackoutMaxHours;
    private int blackoutWatts;
    private Raids raids;

    public int watts(int generatorLevel) {
        int i = Math.max(0, Math.min(generatorWatts.length - 1, generatorLevel - 1));
        return generatorWatts[i];
    }

    public int[] getGeneratorWatts() {
        return generatorWatts;
    }

    public List<Consumer> getConsumers() {
        return consumers;
    }

    public Consumer consumer(String id) {
        for (Consumer c : consumers) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown consumer: " + id);
    }

    public int getNightStartHour() {
        return nightStartHour;
    }

    public int getNightEndHour() {
        return nightEndHour;
    }

    public boolean isNight(int hour) {
        return nightStartHour > nightEndHour
                ? hour >= nightStartHour || hour < nightEndHour
                : hour >= nightStartHour && hour < nightEndHour;
    }

    public double batteryCapacity(int generatorLevel) {
        return batteryBase + batteryPerGeneratorLevel * (generatorLevel - 1);
    }

    public double getChargePerSpareWattHour() {
        return chargePerSpareWattHour;
    }

    public double getSweepCost() {
        return sweepCost;
    }

    public double getRechargePerBattery() {
        return rechargePerBattery;
    }

    public double getBadAirStressPerHour() {
        return badAirStressPerHour;
    }

    public double getBlackoutChancePerDay() {
        return blackoutChancePerDay;
    }

    public int getBlackoutMinHours() {
        return blackoutMinHours;
    }

    public int getBlackoutMaxHours() {
        return blackoutMaxHours;
    }

    public int getBlackoutWatts() {
        return blackoutWatts;
    }

    public Raids getRaids() {
        return raids;
    }

    /** Something in the bunker that draws power. */
    public static final class Consumer {
        private String id;
        private String name;
        private String description;
        private int min;
        private int max;
        /** Extra watts required at night (filters). */
        private int nightExtra;
        /** Lower is cut first when there isn't enough power. */
        private int priority;
        /** Units given in a new game. */
        private int start;

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }

        public int getNightExtra() {
            return nightExtra;
        }

        public int getPriority() {
            return priority;
        }

        public int getStart() {
            return start;
        }
    }

    /** Raids on the bunker (GDD §5.3 Guardia). */
    public static final class Raids {
        private double chancePerDay;
        private int baseStrength;
        private int strengthPerAntennaLevel;
        private int strengthVariance;
        /** Fraction (0–1) of each targeted resource taken when a raid succeeds. */
        private double stealFraction;
        private int resourcesTargeted;
        /** Margin of defeat at or above which the injury is serious. */
        private int seriousMargin;

        public double getChancePerDay() {
            return chancePerDay;
        }

        public int getBaseStrength() {
            return baseStrength;
        }

        public int getStrengthPerAntennaLevel() {
            return strengthPerAntennaLevel;
        }

        public int getStrengthVariance() {
            return strengthVariance;
        }

        public double getStealFraction() {
            return stealFraction;
        }

        public int getResourcesTargeted() {
            return resourcesTargeted;
        }

        public int getSeriousMargin() {
            return seriousMargin;
        }
    }
}
