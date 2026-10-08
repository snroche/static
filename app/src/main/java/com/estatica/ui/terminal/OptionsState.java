package com.estatica.ui.terminal;

/** What the options screen shows. Immutable snapshot for LiveData. */
final class OptionsState {

    /** Typewriter speed steps, slowest first (multiplier on the base char delay). */
    static final double[] TEXT_SPEEDS = {1.8, 1.0, 0.45, 0.05};

    final String operatorName;
    final boolean sound;
    final boolean haptics;
    final boolean notifications;
    /** Index into {@link #TEXT_SPEEDS}. */
    final int textSpeed;
    final boolean crt;

    OptionsState(String operatorName, boolean sound, boolean haptics, boolean notifications, int textSpeed,
                 boolean crt) {
        this.operatorName = operatorName;
        this.sound = sound;
        this.haptics = haptics;
        this.notifications = notifications;
        this.textSpeed = textSpeed;
        this.crt = crt;
    }
}
