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
        com.estatica.domain.content.Balance.Bond balance = content.getBalance().getBond();
        if (delta > 0 && scout.getLife().getBond() >= balance.getMax()) {
            // Nothing left to grow: being that close shows as ease instead.
            int eased = -rules.addStress(scout, -Math.max(1, (int) Math.round(delta * balance.getFullBondReliefShare())));
            if (eased > 0) {
                vars.put("n", String.valueOf(eased));
                lines.add(LogLine.draft(now, LogChannel.SYSTEM, null,
                        TextTemplate.format(content.getTerminal().moment("bondFull"), vars), scout.getId()));
            }
            return;
        }
        int levels = rules.addBond(scout, delta);
        String template = levels > 0 ? text.getBondUp()
                : delta > 0 ? text.getBondWarmer()
                : delta < 0 ? text.getBondCooler() : null;
        if (template != null) {
            // Every change shows, not just level-ups: talking to someone always lands somewhere.
            vars.put("progress", String.valueOf(rules.bondProgress(scout)));
            vars.put("level", String.valueOf(rules.bondLevel(scout)));
            LogLine line = LogLine.draft(now, LogChannel.SYSTEM, null,
                    TextTemplate.format(template, vars), scout.getId());
            lines.add(levels > 0 ? line.withCue(LogLine.Cue.BOND) : line);
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
