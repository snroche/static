package com.estatica.domain.scouts;

/** The five individual scout stats (GDD §5.1). Content uses {@link #key()}. */
public enum StatType {
    /** Aguante: combat, carrying, resisting injuries. */
    ENDURANCE("endurance"),
    /** Sigilo: avoiding encounters, stealing. */
    STEALTH("stealth"),
    /** Ingenio: repairing, lockpicking, reading signals. */
    WITS("wits"),
    /** Nervio: resisting stress. */
    NERVE("nerve"),
    /** Oficio: bunker task performance. */
    CRAFT("craft");

    private final String key;

    StatType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** @return the stat for a content key, or null if unknown. */
    public static StatType fromKey(String key) {
        for (StatType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return null;
    }
}
