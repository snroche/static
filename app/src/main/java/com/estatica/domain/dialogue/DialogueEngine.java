package com.estatica.domain.dialogue;

import com.estatica.core.GameRandom;
import com.estatica.core.TextTemplate;
import com.estatica.domain.content.DialogueDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.ScoutTemplate;
import com.estatica.domain.content.TerminalText;
import com.estatica.domain.content.VoiceDef;
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;
import com.estatica.domain.scouts.DialogueOutcomes;
import com.estatica.domain.scouts.Memory;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutRules;
import com.estatica.domain.scouts.ScoutTrait;
import com.estatica.domain.scouts.ScoutStatus;
import com.estatica.domain.scouts.StatType;
import com.estatica.domain.scouts.VoiceRenderer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks and plays short conversations from content (GDD §5.3). A dialogue is marked as done
 * when it opens, so an ignored one is not offered again; GRIEF dialogues are done per fallen
 * companion.
 */
public final class DialogueEngine {

    /** Flag prefix marking a dialogue as already had. */
    public static final String DONE = "dlg:";

    private static final double MILLIS_PER_HOUR = 3_600_000.0;

    /** Who a scout gets along with (or doesn't). Provided by whoever owns affinities. */
    public interface Relations {
        Relations NONE = new Relations() {
            @Override
            public Scout find(Scout scout, String kind) {
                return null;
            }

            @Override
            public void adjust(Scout a, Scout b, double delta) {
            }
        };

        /** An alive companion in that relation ("friend", "inseparable", "rival"), or null. */
        Scout find(Scout scout, String kind);

        void adjust(Scout a, Scout b, double delta);
    }

    private final GameContent content;
    private final ScoutRules rules;
    private final Relations relations;

    public DialogueEngine(GameContent content, ScoutRules rules) {
        this(content, rules, Relations.NONE);
    }

    public DialogueEngine(GameContent content, ScoutRules rules, Relations relations) {
        this.content = content;
        this.rules = rules;
        this.relations = relations;
    }

    /** The companion a dialogue is about: the dead (GRIEF) or a friend / rival. */
    private Scout subject(Scout s, DialogueDef d, Collection<Scout> everyone) {
        if (d.getKind() == DialogueDef.Kind.GRIEF) {
            return lastDead(s, everyone);
        }
        return d.getRequiresRelation() != null ? relations.find(s, d.getRequiresRelation()) : null;
    }

    public boolean isEligible(Scout s, DialogueDef d, Collection<Scout> everyone, long now) {
        if (!s.isAlive() || s.getStatus() != ScoutStatus.IDLE) {
            return false;
        }
        if (d.getScout() != null && !d.getScout().equals(s.getTemplateId())) {
            return false;
        }
        if (!fitsPersonality(s, d)) {
            return false;
        }
        if (rules.bondLevel(s) < d.getMinBondLevel()) {
            return false;
        }
        for (String flag : d.getRequiresFlags()) {
            if (!s.getLife().hasFlag(flag)) {
                return false;
            }
        }
        for (String flag : d.getForbidsFlags()) {
            if (s.getLife().hasFlag(flag)) {
                return false;
            }
        }
        for (Map.Entry<String, Integer> req : d.getRequiresStats().entrySet()) {
            if (s.stat(StatType.fromKey(req.getKey())).getValue() < req.getValue()) {
                return false;
            }
        }
        Scout other = subject(s, d, everyone);
        if (d.getKind() == DialogueDef.Kind.GRIEF && (other == null
                || (now - other.getLife().getDiedAtMillis()) / MILLIS_PER_HOUR < d.getMinHoursSinceDeath())) {
            return false;
        }
        if (d.getRequiresRelation() != null && other == null) {
            return false;
        }
        return d.isRepeatable() || !s.getLife().hasFlag(doneFlag(d, other));
    }

    /** Trait and background requirements: who they are, not which template they came from. */
    private boolean fitsPersonality(Scout s, DialogueDef d) {
        if (!d.getRequiresTrait().isEmpty()) {
            boolean any = false;
            for (ScoutTrait t : s.getTraits()) {
                any |= t.isVisible() && d.getRequiresTrait().contains(t.getTraitId());
            }
            if (!any) {
                return false;
            }
        }
        if (d.getRequiresHiddenTrait() != null) {
            boolean hidden = false;
            for (ScoutTrait t : s.getTraits()) {
                hidden |= t.isHidden() && !t.isRevealed() && d.getRequiresHiddenTrait().equals(t.getTraitId());
            }
            if (!hidden) {
                return false;
            }
        }
        if (d.getRequiresBackground() != null) {
            ScoutTemplate persona = content.persona(s);
            return persona != null && d.getRequiresBackground().equals(persona.getBackground());
        }
        return true;
    }

    /**
     * Highest-priority eligible dialogue of the requested origin, or null.
     *
     * @param initiated true for dialogues the scout starts (INITIATED, GUIDANCE, GRIEF)
     */
    public DialogueDef pick(Scout s, boolean initiated, Collection<Scout> everyone, GameRandom rng, long now) {
        List<DialogueDef> best = new ArrayList<>();
        int bestPriority = Integer.MIN_VALUE;
        for (DialogueDef d : content.getDialogues().values()) {
            if (d.isScoutInitiated() != initiated || !isEligible(s, d, everyone, now)) {
                continue;
            }
            if (d.getPriority() > bestPriority) {
                best.clear();
                bestPriority = d.getPriority();
            }
            if (d.getPriority() == bestPriority) {
                best.add(d);
            }
        }
        return best.isEmpty() ? null : rng.pick(best);
    }

    /** The scout's opening lines. Marks the dialogue as had. */
    /** What the scout says in a dialogue, rendered in their voice (for the decision card). */
    public List<String> linesOf(Scout s, DialogueDef d, Collection<Scout> everyone) {
        VoiceDef voice = content.voice(s.getVoiceId());
        Map<String, String> vars = vars(s, subject(s, d, everyone));
        List<String> out = new ArrayList<>();
        for (String line : d.getLines()) {
            out.add(VoiceRenderer.render(voice, line, vars));
        }
        return out;
    }

    public List<LogLine> open(Scout s, DialogueDef d, boolean initiated, Collection<Scout> everyone,
                              GameRandom rng, long now) {
        Scout dead = subject(s, d, everyone);
        s.getLife().addFlag(doneFlag(d, dead));
        if (initiated) {
            s.getLife().setLastInitiatedAtMillis(now);
        }
        VoiceDef voice = content.voice(s.getVoiceId());
        Map<String, String> vars = vars(s, dead);
        List<LogLine> lines = new ArrayList<>();
        if (initiated) {
            lines.add(say(s, VoiceRenderer.phrase(voice, "initiate", rng, vars, rules.isClose(s)), now));
        }
        for (String line : d.getLines()) {
            lines.add(say(s, VoiceRenderer.render(voice, line, vars), now));
        }
        return lines;
    }

    public static final class Answer {
        public final List<LogLine> lines = new ArrayList<>();
        public final List<Memory> memories = new ArrayList<>();
    }

    public Answer answer(Scout s, DialogueDef d, int choiceIndex, Collection<Scout> everyone, long now) {
        DialogueDef.Choice choice = d.getChoices().get(choiceIndex);
        Scout dead = subject(s, d, everyone);
        if (dead != null && dead.isAlive() && choice.getAffinity() != 0) {
            relations.adjust(s, dead, choice.getAffinity());
        }
        Map<String, String> vars = vars(s, dead);
        VoiceDef voice = content.voice(s.getVoiceId());
        TerminalText.BunkerText text = content.getTerminal().getBunker();
        Answer out = new Answer();

        out.lines.add(LogLine.draft(now, LogChannel.OPERATOR, null,
                TextTemplate.format(choice.getLabel(), vars), s.getId()));
        for (String line : choice.getReply()) {
            out.lines.add(say(s, VoiceRenderer.render(voice, line, vars), now));
        }
        rules.addTrust(s, choice.getTrust());
        rules.addStress(s, choice.getStress());
        for (String flag : choice.getSetFlags()) {
            s.getLife().addFlag(flag);
        }
        if (choice.getAddTrait() != null && rules.addTrait(s, choice.getAddTrait())) {
            DialogueOutcomes.announceTrait(content, s, choice.getAddTrait(), text.getTraitGained(), now, out.lines);
        }
        if (choice.isRevealHidden()) {
            String revealed = rules.revealHidden(s);
            if (revealed != null) {
                DialogueOutcomes.announceTrait(content, s, revealed, text.getTraitRevealed(), now, out.lines);
            }
        }
        DialogueOutcomes.applyBond(content, rules, s, choice.getBond(), now, out.lines);
        if (choice.getMemory() != null) {
            out.memories.add(Memory.create(s.getId(), Memory.Type.TALK,
                    TextTemplate.format(choice.getMemory(), vars), d.getId(), now));
        }
        return out;
    }

    /** Most recent death among the others, or null. */
    public static Scout lastDead(Scout s, Collection<Scout> everyone) {
        Scout last = null;
        for (Scout other : everyone) {
            if (other != s && !other.isAlive()
                    && (last == null || other.getLife().getDiedAtMillis() > last.getLife().getDiedAtMillis())) {
                last = other;
            }
        }
        return last;
    }

    static String doneFlag(DialogueDef d, Scout dead) {
        return dead == null ? DONE + d.getId() : DONE + d.getId() + ":" + dead.getId();
    }

    private static Map<String, String> vars(Scout s, Scout other) {
        Map<String, String> vars = new HashMap<>();
        vars.put("name", s.getName());
        if (other != null) {
            vars.put("dead", other.getName());
            vars.put("other", other.getName());
        }
        return vars;
    }

    /** The companion this dialogue is about, for labels shown before answering. */
    public Scout subjectOf(Scout s, DialogueDef d, Collection<Scout> everyone) {
        return subject(s, d, everyone);
    }

    private static LogLine say(Scout s, String text, long now) {
        return LogLine.draft(now, LogChannel.RADIO, s.getName(), text, s.getId());
    }
}
