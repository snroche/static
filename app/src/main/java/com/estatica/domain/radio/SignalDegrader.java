package com.estatica.domain.radio;

import com.estatica.core.GameRandom;

/**
 * Cuts words of a transmission to simulate a bad signal: "no hay salida" →
 * "no hay sal— ...". Deterministic for a given RNG state.
 */
public final class SignalDegrader {

    private static final String CUT = "—";

    private SignalDegrader() {
    }

    /**
     * @param intensity 0 = clean, 1 = every word cut
     */
    public static String degrade(String text, double intensity, GameRandom rng) {
        if (intensity <= 0) {
            return text;
        }
        String[] words = text.split(" ");
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (i > 0) {
                out.append(' ');
            }
            if (word.length() > 2 && rng.chance(intensity)) {
                int keep = 1 + rng.nextInt(word.length() - 1);
                out.append(word, 0, keep).append(CUT);
                if (rng.chance(intensity)) {
                    out.append(" ...");
                }
            } else {
                out.append(word);
            }
        }
        return out.toString();
    }
}
