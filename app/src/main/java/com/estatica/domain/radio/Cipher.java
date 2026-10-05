package com.estatica.domain.radio;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Caesar and Morse (GDD §6.2). Works on normalized text: A–Z and single spaces. */
public final class Cipher {

    private static final String[] MORSE = {
            "·—", "—···", "—·—·", "—··", "·", "··—·", "——·", "····", "··", "·———",
            "—·—", "·—··", "——", "—·", "———", "·——·", "——·—", "·—·", "···", "—",
            "··—", "···—", "·——", "—··—", "—·——", "——··"};
    private static final Map<String, Character> FROM_MORSE = new HashMap<>();

    static {
        for (int i = 0; i < MORSE.length; i++) {
            FROM_MORSE.put(MORSE[i], (char) ('A' + i));
        }
    }

    /** Separates letters in Morse output. */
    public static final String LETTER_GAP = " ";
    /** Separates words in Morse output. */
    public static final String WORD_GAP = " / ";
    public static final char DOT = '·';
    public static final char DASH = '—';

    private Cipher() {
    }

    /** Uppercase, no accents (Ñ → N), only letters and single spaces. */
    public static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder out = new StringBuilder();
        for (char c : decomposed.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                out.append(c);
            } else if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
                out.append(' ');
            }
        }
        return out.toString().trim();
    }

    /** Shifts letters forward by {@code shift} (negative to decode). */
    public static String caesar(String text, int shift) {
        int s = ((shift % 26) + 26) % 26;
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            out.append(c >= 'A' && c <= 'Z' ? (char) ('A' + (c - 'A' + s) % 26) : c);
        }
        return out.toString();
    }

    public static String toMorse(String normalized) {
        StringBuilder out = new StringBuilder();
        String[] words = normalized.split(" ");
        for (int w = 0; w < words.length; w++) {
            if (w > 0) {
                out.append(WORD_GAP);
            }
            for (int i = 0; i < words[w].length(); i++) {
                if (i > 0) {
                    out.append(LETTER_GAP);
                }
                out.append(MORSE[words[w].charAt(i) - 'A']);
            }
        }
        return out.toString();
    }

    /** One Morse letter ("·—") to its character, or null if it isn't a letter. */
    public static Character letter(String code) {
        return FROM_MORSE.get(code);
    }

    /** Morse code of a letter, for the reference table. */
    public static String code(char letter) {
        return MORSE[Character.toUpperCase(letter) - 'A'];
    }

    /** True if the answer says the same as the plain text (case, accents, spacing ignored). */
    public static boolean matches(String answer, String plainText) {
        return normalize(answer).equals(normalize(plainText));
    }
}
