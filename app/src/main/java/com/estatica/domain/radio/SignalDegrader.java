package com.estatica.domain.radio;

import com.estatica.core.GameRandom;

/**
 * Static over a transmission: letters drop out and become dots and dashes, but the words keep
 * their shape, so a careful listener can still piece it together ("i found some rations" →
 * "i f·u-d s·me r-ti·ns"). Deterministic for a given RNG state.
 */
public final class SignalDegrader {

    private static final char[] STATIC = {'·', '-'};
    /** Share of a hit word's letters lost at full intensity. */
    private static final double MAX_LOSS = 0.6;

    private SignalDegrader() {
    }

    /**
     * @param intensity 0 = clean, 1 = every word broken up
     */
    public static String degrade(String text, double intensity, GameRandom rng) {
        if (intensity <= 0) {
            return text;
        }
        String[] words = text.split(" ", -1);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            String word = words[i];
            if (letters(word) > 2 && rng.chance(intensity)) {
                out.append(breakUp(word, intensity, rng));
            } else {
                out.append(word);
            }
        }
        return out.toString();
    }

    /** Keeps the first letter and the punctuation; some other letters turn into static. */
    private static String breakUp(String word, double intensity, GameRandom rng) {
        char[] chars = word.toCharArray();
        double loss = Math.min(MAX_LOSS, intensity * MAX_LOSS);
        boolean first = true;
        boolean lostAny = false;
        int lastLetter = -1;
        for (int i = 0; i < chars.length; i++) {
            if (!Character.isLetterOrDigit(chars[i])) {
                continue;
            }
            if (first) {
                first = false; // the first letter always comes through
                continue;
            }
            lastLetter = i;
            if (rng.chance(loss)) {
                chars[i] = STATIC[rng.nextInt(STATIC.length)];
                lostAny = true;
            }
        }
        if (!lostAny && lastLetter > 0) {
            chars[lastLetter] = STATIC[rng.nextInt(STATIC.length)];
        }
        return new String(chars);
    }

    private static int letters(String word) {
        int n = 0;
        for (int i = 0; i < word.length(); i++) {
            n += Character.isLetterOrDigit(word.charAt(i)) ? 1 : 0;
        }
        return n;
    }
}
