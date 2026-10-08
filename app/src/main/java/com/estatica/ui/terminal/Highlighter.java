package com.estatica.ui.terminal;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes the terminal text readable at a glance: people bright and bold, places underlined,
 * numbers bold, danger in red and good news in green. Vocabulary comes from the game (names,
 * zones, resources) and from content (danger / good words). Pure text in, spans out.
 */
final class Highlighter {

    static final Highlighter NONE = new Highlighter(List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0, 0);

    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\d])\\d+(?:[.,]\\d+)?%?");

    private final Pattern names;
    private final Pattern places;
    private final Pattern resources;
    private final Pattern danger;
    private final Pattern good;
    private final int bright;
    private final int dangerColor;
    private final int goodColor;

    Highlighter(Collection<String> names, Collection<String> places, Collection<String> resources,
                Collection<String> dangerWords, Collection<String> goodWords, int bright, int dangerColor,
                int goodColor) {
        this.names = words(names);
        this.places = words(places);
        this.resources = words(resources);
        this.danger = words(dangerWords);
        this.good = words(goodWords);
        this.bright = bright;
        this.dangerColor = dangerColor;
        this.goodColor = goodColor;
    }

    /** Whole words or phrases, longest first, case-insensitive (lowercase voices stay matched). */
    private static Pattern words(Collection<String> list) {
        List<String> sorted = new ArrayList<>();
        for (String w : list) {
            if (w != null && !w.isBlank()) {
                sorted.add(w.trim());
            }
        }
        if (sorted.isEmpty()) {
            return null;
        }
        sorted.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder alt = new StringBuilder();
        for (String w : sorted) {
            if (alt.length() > 0) {
                alt.append('|');
            }
            alt.append(Pattern.quote(w));
        }
        return Pattern.compile("(?iu)(?<![\\p{L}\\d])(?:" + alt + ")(?![\\p{L}\\d])");
    }

    /** Spans over {@code text} starting at {@code offset} in {@code out}. */
    void apply(SpannableStringBuilder out, int offset) {
        String text = out.toString().substring(offset);
        mark(out, offset, text, NUMBER, Typeface.BOLD, 0, false);
        mark(out, offset, text, resources, Typeface.BOLD, goodColor, false);
        mark(out, offset, text, good, Typeface.NORMAL, goodColor, false);
        mark(out, offset, text, danger, Typeface.BOLD, dangerColor, false);
        mark(out, offset, text, places, Typeface.NORMAL, bright, true);
        mark(out, offset, text, names, Typeface.BOLD, bright, false);
    }

    CharSequence apply(CharSequence text) {
        SpannableStringBuilder out = new SpannableStringBuilder(text);
        apply(out, 0);
        return out;
    }

    private static void mark(SpannableStringBuilder out, int offset, String text, Pattern pattern,
                             int style, int color, boolean underline) {
        if (pattern == null) {
            return;
        }
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            int start = offset + m.start();
            int end = offset + m.end();
            if (style != Typeface.NORMAL) {
                out.setSpan(new StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (color != 0) {
                out.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (underline) {
                out.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }
}
