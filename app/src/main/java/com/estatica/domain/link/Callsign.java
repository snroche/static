package com.estatica.domain.link;

import java.util.Locale;

/**
 * A bunker's name on the air, from its operator id: "LU" (Argentina's amateur prefix), a digit
 * and three letters, like {@code LU4QXR}. Stable for the life of the save.
 */
public final class Callsign {

    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    private Callsign() {
    }

    public static String of(String operatorId) {
        long h = 1125899906842597L;
        for (char c : operatorId.toLowerCase(Locale.ROOT).toCharArray()) {
            h = 31 * h + c;
        }
        h ^= h >>> 29;
        StringBuilder out = new StringBuilder("LU");
        out.append((char) ('1' + Math.floorMod(h, 9)));
        h /= 9;
        for (int i = 0; i < 3; i++) {
            out.append(LETTERS.charAt((int) Math.floorMod(h, LETTERS.length())));
            h /= LETTERS.length();
        }
        return out.toString();
    }
}
