package com.estatica.domain.scouts;

/** Injury severity. Ordinal = level (used to index stat penalties). */
public enum Injury {
    NONE, LIGHT, SERIOUS, CRITICAL;

    public static Injury fromKey(String key) {
        if (key == null) {
            return NONE;
        }
        for (Injury injury : values()) {
            if (injury.name().equalsIgnoreCase(key)) {
                return injury;
            }
        }
        throw new IllegalArgumentException("Unknown injury: " + key);
    }

    public Injury worse() {
        return this == CRITICAL ? CRITICAL : values()[ordinal() + 1];
    }

    public Injury better() {
        return this == NONE ? NONE : values()[ordinal() - 1];
    }

    /** Taking an injury of {@code incoming} on top of this one. */
    public Injury combine(Injury incoming) {
        if (incoming == NONE) {
            return this;
        }
        if (this == NONE) {
            return incoming;
        }
        // Already hurt: one level worse than whichever is higher.
        Injury higher = incoming.ordinal() > ordinal() ? incoming : this;
        return higher.worse();
    }
}
