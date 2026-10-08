package com.estatica.domain.scouts;

import com.estatica.core.GameRandom;
import com.estatica.core.TextTemplate;
import com.estatica.domain.content.VoiceDef;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders text in a scout's voice style: placeholders, slang replacements, case transform. */
public final class VoiceRenderer {

    private static final Locale TEXT_LOCALE = Locale.ENGLISH;

    /** What the crew calls the operator; {@code {operator}} in any line. Set by the session. */
    private static volatile String operatorName = "Operator";

    private VoiceRenderer() {
    }

    public static void setOperatorName(String name) {
        operatorName = name == null || name.isBlank() ? "Operator" : name;
    }

    public static String operatorName() {
        return operatorName;
    }

    public static String render(VoiceDef voice, String template, Map<String, String> vars) {
        if (template.contains("{operator}") && !vars.containsKey("operator")) {
            vars = new java.util.HashMap<>(vars);
            vars.put("operator", operatorName);
        }
        String text = TextTemplate.format(template, vars);
        Map<String, String> replacements = voice.getReplacements();
        if (replacements != null) {
            for (Map.Entry<String, String> r : replacements.entrySet()) {
                // Whole words only. No inline flags: Android's regex engine (ICU) rejects (?U)/(?u),
                // and its CASE_INSENSITIVE is already Unicode-aware.
                text = Pattern.compile("(?<![\\p{L}\\d])" + Pattern.quote(r.getKey()) + "(?![\\p{L}\\d])",
                                Pattern.CASE_INSENSITIVE)
                        .matcher(text)
                        .replaceAll(Matcher.quoteReplacement(r.getValue()));
            }
        }
        if (voice.getTransform() == VoiceDef.Transform.LOWER) {
            text = text.toLowerCase(TEXT_LOCALE);
        } else if (voice.getTransform() == VoiceDef.Transform.UPPER) {
            text = text.toUpperCase(TEXT_LOCALE);
        }
        return text;
    }

    /**
     * Like {@link #phrase(VoiceDef, String, GameRandom, Map)}, but a close scout uses the
     * {@code key@close} variant when the voice has one (warmer tone, nicknames).
     */
    public static String phrase(VoiceDef voice, String key, GameRandom rng, Map<String, String> vars,
                                boolean close) {
        if (close && voice.getPhrases().containsKey(key + "@close")) {
            return phrase(voice, key + "@close", rng, vars);
        }
        return phrase(voice, key, rng, vars);
    }

    /** Picks a phrase from the voice bank and renders it. */
    public static String phrase(VoiceDef voice, String key, GameRandom rng, Map<String, String> vars) {
        List<String> options = voice.getPhrases().get(key);
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("Voice " + voice.getId() + " has no phrase " + key);
        }
        return render(voice, rng.pick(options), vars);
    }
}
