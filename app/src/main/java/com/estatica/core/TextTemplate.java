package com.estatica.core;

import java.util.Map;

/**
 * Replaces {@code {key}} placeholders in content strings, and pronoun forms like
 * {@code {he:other}} (see {@link Pronouns}). Unknown keys are left untouched.
 */
public final class TextTemplate {

    private TextTemplate() {
    }

    public static String format(String template, Map<String, String> vars) {
        if (template.indexOf('{') < 0) {
            return template;
        }
        StringBuilder out = new StringBuilder(template.length() + 16);
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i + 1);
                if (end > i) {
                    String key = template.substring(i + 1, end);
                    String value = vars.get(key);
                    if (value == null) {
                        value = Pronouns.resolve(key, vars); // {he:other}, {his:name}…
                    }
                    if (value != null) {
                        out.append(value);
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
