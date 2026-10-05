package com.estatica.domain.scouts;

import com.estatica.core.TextTemplate;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.TerminalText;
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared side effects of bond changes: level-up notice and hidden trait reveal. */
public final class DialogueOutcomes {

    private DialogueOutcomes() {
    }

    public static void applyBond(GameContent content, ScoutRules rules, Scout scout, int delta,
                                 long now, List<LogLine> lines) {
        TerminalText.BunkerText text = content.getTerminal().getBunker();
        Map<String, String> vars = new HashMap<>();
        vars.put("name", scout.getName());
        if (rules.addBond(scout, delta) > 0) {
            lines.add(LogLine.draft(now, LogChannel.SYSTEM, null,
                    TextTemplate.format(text.getBondUp(), vars), scout.getId()));
        }
        String revealed = rules.revealHiddenIfDue(scout);
        if (revealed != null) {
            announceTrait(content, scout, revealed, text.getTraitRevealed(), now, lines);
        }
    }

    public static void announceTrait(GameContent content, Scout scout, String traitId,
                                     String template, long now, List<LogLine> lines) {
        Map<String, String> vars = new HashMap<>();
        vars.put("name", scout.getName());
        vars.put("trait", content.trait(traitId).getName());
        lines.add(LogLine.draft(now, LogChannel.SYSTEM, null, TextTemplate.format(template, vars),
                scout.getId()));
    }
}
