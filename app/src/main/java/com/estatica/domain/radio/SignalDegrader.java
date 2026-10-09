package com.estatica.domain.radio;

import com.estatica.core.GameRandom;

import java.util.ArrayList;
import java.util.List;

/**
 * Static over a transmission: stretches of a word fade into dots and dashes, but the words
 * keep their shape, so a careful listener can still piece it together ("i found some rations"
 * → "i found s·-e r··-·ns"). Deterministic for a given RNG state.
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

    /**
     * Keeps the first letter and the punctuation; a stretch of the rest (2–4 letters, longer
     * with more static) drops out as dots and dashes, the way a signal fades for a moment.
     */
    private static String breakUp(String word, double intensity, GameRandom rng) {
        char[] chars = word.toCharArray();
        List<Integer> letterAt = new ArrayList<>();
        for (int i = 0; i < chars.length; i++) {
            if (Character.isLetterOrDigit(chars[i])) {
                letterAt.add(i);
            }
        }
        // Everything after the first letter can fade.
        int fadeable = letterAt.size() - 1;
        int max = Math.max(2, Math.min(4, (int) Math.round(fadeable * MAX_LOSS * Math.max(0.4, intensity))));
        int length = Math.min(fadeable, 2 + rng.nextInt(Math.max(1, max - 1)));
        int start = 1 + rng.nextInt(Math.max(1, fadeable - length + 1));
        for (int k = start; k < start + length && k < letterAt.size(); k++) {
            chars[letterAt.get(k)] = STATIC[rng.nextInt(STATIC.length)];
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
