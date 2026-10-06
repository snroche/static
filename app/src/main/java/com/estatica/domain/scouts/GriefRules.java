package com.estatica.domain.scouts;

import com.estatica.core.GameRandom;
import com.estatica.core.TextTemplate;
import com.estatica.domain.content.Balance;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GDD §5.5 "después": the others react according to how close they were. Stress for
 * everyone, more for the closest; sometimes a scar (Rencor, Miedo). Conversations about the
 * dead come later, through GRIEF dialogues.
 */
public final class GriefRules {

    private final GameContent content;
    private final ScoutRules rules;

    public GriefRules(GameContent content, ScoutRules rules) {
        this.content = content;
        this.rules = rules;
    }

    public static final class Result {
        public final List<LogLine> lines = new ArrayList<>();
        public final List<Memory> memories = new ArrayList<>();
    }

    public Result onDeath(Scout dead, Collection<Scout> everyone, Affinities affinities,
                          GameRandom rng, long now) {
        Result out = new Result();
        Balance.Grief grief = content.getBalance().getGrief();
        for (Scout s : everyone) {
            if (s == dead || !s.isAlive()) {
                continue;
            }
            // Rivals still feel it, just not as a close friend would.
            double closeness = Math.max(0, affinities.get(s.getId(), dead.getId()));
            rules.addStress(s, (int) Math.round(grief.getBaseStress() + closeness * grief.getStressPerAffinity()));

            Map<String, String> vars = new HashMap<>();
            vars.put("name", s.getName());
            vars.put("dead", dead.getName());
            out.lines.add(LogLine.draft(now, LogChannel.SYSTEM, null,
                    TextTemplate.format(content.getTerminal().getBunker().getGriefNotice(), vars), s.getId()));
            out.memories.add(Memory.create(s.getId(), Memory.Type.GRIEF, dead.getName(), dead.getId(), now));

            if (closeness >= grief.getTraitAffinityMin() && rng.chance(grief.getTraitChance())) {
                List<String> options = new ArrayList<>();
                for (String trait : grief.getTraits()) {
                    if (!s.hasTrait(trait)) {
                        options.add(trait);
                    }
                }
                if (!options.isEmpty()) {
                    String trait = rng.pick(options);
                    rules.addTrait(s, trait);
                    DialogueOutcomes.announceTrait(content, s, trait,
                            content.getTerminal().getBunker().getTraitGained(), now, out.lines);
                }
            }
        }
        return out;
    }
}
