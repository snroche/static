package com.estatica.domain.radio;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * The harder schemes found on the dial (GDD §6.2), past Caesar and Morse. All work on
 * normalized text (A–Z and single spaces) and are pure: encode for the game, decode for the
 * tools the key items unlock.
 */
public final class Ciphers {

    /** Polybius / prison tap code grid: C and K share a square. */
    private static final String GRID = "ABCDEFGHIJLMNOPQRSTUVWXYZ";
    static final String[] NATO = {"ALFA", "BRAVO", "CHARLIE", "DELTA", "ECHO", "FOXTROT", "GOLF", "HOTEL",
            "INDIA", "JULIETT", "KILO", "LIMA", "MIKE", "NOVEMBER", "OSCAR", "PAPA", "QUEBEC", "ROMEO", "SIERRA",
            "TANGO", "UNIFORM", "VICTOR", "WHISKEY", "XRAY", "YANKEE", "ZULU"};
    public static final char KNOCK = '·';

    private Ciphers() {
    }

    // ---- Atbash: the mirror alphabet ----

    public static String atbash(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            out.append(c >= 'A' && c <= 'Z' ? (char) ('Z' - (c - 'A')) : c);
        }
        return out.toString();
    }

    // ---- Tap code: row knocks, a pause, column knocks ----

    public static String tap(String normalized) {
        StringBuilder out = new StringBuilder();
        String[] words = normalized.split(" ");
        for (int w = 0; w < words.length; w++) {
            if (w > 0) {
                out.append(" / ");
            }
            for (int i = 0; i < words[w].length(); i++) {
                if (i > 0) {
                    out.append("   ");
                }
                char c = words[w].charAt(i) == 'K' ? 'C' : words[w].charAt(i);
                int index = GRID.indexOf(c);
                out.append(knocks(index / 5 + 1)).append(' ').append(knocks(index % 5 + 1));
            }
        }
        return out.toString();
    }

    private static String knocks(int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) {
            s.append(KNOCK);
        }
        return s.toString();
    }

    /** Letter at a grid square (1-based row and column). */
    public static char tapLetter(int row, int col) {
        return GRID.charAt((row - 1) * 5 + (col - 1));
    }

    // ---- Vigenère ----

    public static String vigenere(String text, String key, boolean decode) {
        String k = Cipher.normalize(key).replace(" ", "");
        if (k.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int j = 0;
        for (char c : text.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                int shift = k.charAt(j++ % k.length()) - 'A';
                out.append((char) ('A' + Math.floorMod(c - 'A' + (decode ? -shift : shift), 26)));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    // ---- Base64 ----

    public static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    /** Decoded text, or null if it isn't valid Base64. */
    public static String unbase64(String encoded) {
        try {
            return new String(Base64.getDecoder().decode(encoded.trim()), StandardCharsets.US_ASCII);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- Playfair, read out as NATO words ----

    private static char[][] square(String keyword) {
        StringBuilder seen = new StringBuilder();
        for (char c : (Cipher.normalize(keyword).replace(" ", "") + "ABCDEFGHIKLMNOPQRSTUVWXYZ").toCharArray()) {
            char k = c == 'J' ? 'I' : c;
            if (seen.indexOf(String.valueOf(k)) < 0) {
                seen.append(k);
            }
        }
        char[][] sq = new char[5][5];
        for (int i = 0; i < 25; i++) {
            sq[i / 5][i % 5] = seen.charAt(i);
        }
        return sq;
    }

    private static int[] find(char[][] sq, char c) {
        for (int r = 0; r < 5; r++) {
            for (int col = 0; col < 5; col++) {
                if (sq[r][col] == c) {
                    return new int[]{r, col};
                }
            }
        }
        throw new IllegalArgumentException(String.valueOf(c));
    }

    /** Letter pairs ready for Playfair: J→I, X between doubles, X pad at the end. */
    private static List<char[]> digraphs(String text) {
        String letters = text.replace(" ", "").replace('J', 'I');
        List<char[]> out = new ArrayList<>();
        int i = 0;
        while (i < letters.length()) {
            char a = letters.charAt(i);
            char b = i + 1 < letters.length() ? letters.charAt(i + 1) : 'X';
            if (a == b) {
                b = 'X';
                i++;
            } else {
                i += 2;
            }
            out.add(new char[]{a, b});
        }
        return out;
    }

    public static String playfair(String text, String keyword, boolean decode) {
        char[][] sq = square(keyword);
        int step = decode ? 4 : 1;
        StringBuilder out = new StringBuilder();
        List<char[]> pairs = decode ? pairsOf(text) : digraphs(text);
        for (char[] p : pairs) {
            int[] a = find(sq, p[0]);
            int[] b = find(sq, p[1]);
            if (a[0] == b[0]) {
                out.append(sq[a[0]][(a[1] + step) % 5]).append(sq[b[0]][(b[1] + step) % 5]);
            } else if (a[1] == b[1]) {
                out.append(sq[(a[0] + step) % 5][a[1]]).append(sq[(b[0] + step) % 5][b[1]]);
            } else {
                out.append(sq[a[0]][b[1]]).append(sq[b[0]][a[1]]);
            }
        }
        return out.toString();
    }

    private static List<char[]> pairsOf(String letters) {
        String clean = letters.replace(" ", "");
        List<char[]> out = new ArrayList<>();
        for (int i = 0; i + 1 < clean.length(); i += 2) {
            out.add(new char[]{clean.charAt(i), clean.charAt(i + 1)});
        }
        return out;
    }

    public static String toNato(String letters) {
        StringBuilder out = new StringBuilder();
        String clean = letters.replace(" ", "");
        for (int i = 0; i < clean.length(); i++) {
            if (i > 0) {
                out.append(i % 5 == 0 ? " / " : " ");
            }
            out.append(NATO[clean.charAt(i) - 'A']);
        }
        return out.toString();
    }

    /** NATO words back to letters (unknown words are skipped). */
    public static String fromNato(String words) {
        StringBuilder out = new StringBuilder();
        for (String w : words.toUpperCase(Locale.ROOT).split("[\\s/]+")) {
            for (int i = 0; i < NATO.length; i++) {
                if (NATO[i].equals(w)) {
                    out.append((char) ('A' + i));
                }
            }
        }
        return out.toString();
    }

    // ---- One-time pad numbers: 01=A … 26=Z, 00=space; digit-wise addition, no carry ----

    public static String numbers(String text, String pad) {
        StringBuilder digits = new StringBuilder();
        for (char c : text.toCharArray()) {
            int v = c == ' ' ? 0 : c - 'A' + 1;
            digits.append(v / 10).append(v % 10);
        }
        String p = pad.replaceAll("[^0-9]", "");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            int d = (digits.charAt(i) - '0' + p.charAt(i % p.length()) - '0') % 10;
            if (i > 0 && i % 5 == 0) {
                out.append(' ');
            }
            out.append(d);
        }
        return out.toString();
    }

    public static String unnumbers(String groups, String pad) {
        String c = groups.replaceAll("[^0-9]", "");
        String p = pad.replaceAll("[^0-9]", "");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i + 1 < c.length(); i += 2) {
            int hi = Math.floorMod(c.charAt(i) - p.charAt(i % p.length()), 10);
            int lo = Math.floorMod(c.charAt(i + 1) - p.charAt((i + 1) % p.length()), 10);
            int v = hi * 10 + lo;
            out.append(v == 0 ? ' ' : v >= 1 && v <= 26 ? (char) ('A' + v - 1) : '?');
        }
        return out.toString().trim();
    }

    // ---- Book cipher: page-line-word against a book's text ----

    /**
     * @param pages each page is a list of lines
     * @return "p-l-w" triples, or null if a word isn't in the book
     */
    public static String book(String text, List<List<String>> pages) {
        List<String> out = new ArrayList<>();
        for (String word : text.split(" ")) {
            String at = locate(word, pages);
            if (at == null) {
                return null;
            }
            out.add(at);
        }
        return String.join(" ", out);
    }

    private static String locate(String word, List<List<String>> pages) {
        for (int p = 0; p < pages.size(); p++) {
            for (int l = 0; l < pages.get(p).size(); l++) {
                String[] words = Cipher.normalize(pages.get(p).get(l)).split(" ");
                for (int w = 0; w < words.length; w++) {
                    if (words[w].equals(word)) {
                        return (p + 1) + "-" + (l + 1) + "-" + (w + 1);
                    }
                }
            }
        }
        return null;
    }

    public static String unbook(String triples, List<List<String>> pages) {
        List<String> out = new ArrayList<>();
        for (String t : triples.trim().split("\\s+")) {
            String[] n = t.split("-");
            try {
                String[] words = Cipher.normalize(pages.get(Integer.parseInt(n[0]) - 1)
                        .get(Integer.parseInt(n[1]) - 1)).split(" ");
                out.add(words[Integer.parseInt(n[2]) - 1]);
            } catch (RuntimeException e) {
                out.add("?");
            }
        }
        return String.join(" ", out);
    }
}
