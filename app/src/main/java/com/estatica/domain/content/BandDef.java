package com.estatica.domain.content;

import java.util.Map;

/**
 * A radio band (GDD §4.4): frequency = distance. Farther bands mean longer expeditions,
 * worse signal and stronger finds. Filled by Gson from {@code balance.json → bands}.
 */
public final class BandDef {

    private String id;
    private String name;
    private String description;

    public String getDescription() {
        return description;
    }
    private int unlockAntennaLevel;
    /** Report noise for zones in this band (0 = clean, 1 = unintelligible). */
    private double noise;
    /** Seconds an order takes to reach a scout in this band. */
    private int orderDelaySeconds;
    /** How close (in dial units) the dial must be to start hearing a signal. */
    private int tuneRadius;
    private int signalsPerWindow;
    /** Signal kind (ZONE, SURVIVOR, TRANSMISSION, RARE) to relative weight. */
    private Map<String, Integer> signalWeights;
    /** Chance a transmission found here is Morse instead of Caesar. */
    private double morseChance;
    /** Frequency at dial position 0, in kHz. */
    private double baseKHz;
    /** kHz per dial step. */
    private double stepKHz = 1;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getUnlockAntennaLevel() {
        return unlockAntennaLevel;
    }

    public double getNoise() {
        return noise;
    }

    public int getOrderDelaySeconds() {
        return orderDelaySeconds;
    }

    public int getTuneRadius() {
        return tuneRadius;
    }

    public int getSignalsPerWindow() {
        return signalsPerWindow;
    }

    public Map<String, Integer> getSignalWeights() {
        return signalWeights != null ? signalWeights : Map.of();
    }

    /**
     * The dial position as a real frequency: "98.40 MHz" on FM (short), "881 kHz" on AM
     * (medium), "245.0 kHz" on long wave, "16.50 kHz" on VLF (deep). As many decimals as one
     * step of the dial needs.
     */
    public String frequency(int position) {
        double khz = baseKHz + position * stepKHz;
        if (stepKHz >= 10) {
            return String.format(java.util.Locale.ROOT, "%.2f MHz", khz / 1000.0);
        }
        String format = stepKHz >= 1 ? "%.0f kHz" : stepKHz >= 0.1 ? "%.1f kHz" : "%.2f kHz";
        return String.format(java.util.Locale.ROOT, format, khz);
    }

    public double getMorseChance() {
        return morseChance;
    }
}
