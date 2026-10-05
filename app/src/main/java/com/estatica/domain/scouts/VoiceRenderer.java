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

    private static final Locale SPANISH = Locale.forLanguageTag("es");

    private VoiceRenderer() {
    }

    public static String render(VoiceDef voice, String template, Map<String, String> vars) {
        String text = TextTemplate.format(template, vars);
        Map<String, String> replacements = voice.getReplacements();
        if (replacements != null) {
            for (Map.Entry<String, String> r : replacements.entrySet()) {
                text = Pattern.compile("(?iU)\\b" + Pattern.quote(r.getKey()) + "\\b")
                        .matcher(text)
                        .replaceAll(Matcher.quoteReplacement(r.getValue()));
            }
        }
        if (voice.getTransform() == VoiceDef.Transform.LOWER) {
            text = text.toLowerCase(SPANISH);
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
