package com.estatica.domain.terminal;

import com.estatica.core.TextTemplate;
import com.estatica.domain.content.TerminalText;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Maps typed terminal commands to response lines defined in content. */
public final class CommandInterpreter {

    private final TerminalText text;

    public CommandInterpreter(TerminalText text) {
        this.text = text;
    }

    /**
     * @param rawInput what the operator typed
     * @param vars     values for placeholders in the response templates
     * @return response lines, already formatted
     */
    public List<String> respond(String rawInput, Map<String, String> vars) {
        List<String> templates = text.getCommands().get(normalize(rawInput));
        if (templates == null) {
            Map<String, String> withInput = new HashMap<>(vars);
            withInput.put("input", rawInput.trim());
            return List.of(TextTemplate.format(text.getUnknownCommand(), withInput));
        }
        List<String> out = new ArrayList<>(templates.size());
        for (String template : templates) {
            out.add(TextTemplate.format(template, vars));
        }
        return out;
    }

    /** Lowercase, trimmed, accents removed: "Ayúda " → "ayuda". */
    static String normalize(String input) {
        String decomposed = Normalizer.normalize(input.trim(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
