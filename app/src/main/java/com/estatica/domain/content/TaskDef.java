package com.estatica.domain.content;

/**
 * A bunker task from {@code tasks.json} (GDD §5.3). Every task trains one stat; the rest
 * of the effects default to neutral. Filled by Gson.
 */
public final class TaskDef {

    private String id;
    private String name;
    private String description;
    /** Stat trained while on this task. */
    private String stat;
    private double xpPerHour;
    /** Multiplies this scout's own stress recovery. */
    private double selfStressRecoveryMultiplier = 1.0;
    /** Stress removed per hour from every other scout in the bunker. */
    private double groupStressReliefPerHour;
    /** Multiplies healing of every other scout in the bunker. */
    private double groupHealMultiplier = 1.0;
    /** Scouts on this task teach each other: the better one mentors. */
    private boolean mentoring;
    /** Converts resources over time (workshop). Nullable. */
    private Conversion conversion;
    /** Sweeps the dial while the operator is away (radio assistant). */
    private boolean passiveSweep;
    /** Defends the bunker against raids. */
    private boolean guards;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getStat() {
        return stat;
    }

    public double getXpPerHour() {
        return xpPerHour;
    }

    public double getSelfStressRecoveryMultiplier() {
        return selfStressRecoveryMultiplier;
    }

    public double getGroupStressReliefPerHour() {
        return groupStressReliefPerHour;
    }

    public double getGroupHealMultiplier() {
        return groupHealMultiplier;
    }

    public boolean isMentoring() {
        return mentoring;
    }

    public Conversion getConversion() {
        return conversion;
    }

    public boolean isPassiveSweep() {
        return passiveSweep;
    }

    public boolean isGuards() {
        return guards;
    }

    /** {@code input} units of {@code from} become one {@code to}, every {@code hoursPerUnit}. */
    public static final class Conversion {
        private String from;
        private int input;
        private String to;
        private double hoursPerUnit;

        public String getFrom() {
            return from;
        }

        public int getInput() {
            return input;
        }

        public String getTo() {
            return to;
        }

        public double getHoursPerUnit() {
            return hoursPerUnit;
        }
    }
}
