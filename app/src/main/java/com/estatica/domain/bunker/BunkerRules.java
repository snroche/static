package com.estatica.domain.bunker;

import com.estatica.core.TextTemplate;
import com.estatica.domain.content.Balance;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.TaskDef;
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;
import com.estatica.domain.scouts.Affinities;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutRules;
import com.estatica.domain.scouts.ScoutStatus;
import com.estatica.domain.scouts.StatType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Life in the bunker between catch-ups (GDD §5.3): tasks, recovery, training, mentoring,
 * workshop output and bond neglect. Everything is a function of elapsed time; assignments
 * only change through player actions, which catch up first, so they are constant within
 * one call.
 */
public final class BunkerRules {

    private static final double MILLIS_PER_HOUR = 3_600_000.0;
    private static final long DAY = 24L * 3_600_000L;
    /** Mentoring: the student learns this much faster in the mentor's strong stat. */
    private static final double MENTOR_BONUS = 1.5;

    private final GameContent content;
    private final ScoutRules rules;
    private final Balance.Bond bond;
    private final Balance.Affinity affinity;

    public BunkerRules(GameContent content, ScoutRules rules) {
        this.content = content;
        this.rules = rules;
        this.bond = content.getBalance().getBond();
        this.affinity = content.getBalance().getAffinity();
    }

    public static final class Result {
        public final List<LogLine> lines = new ArrayList<>();
        /** Resource changes (workshop). */
        public final Map<String, Integer> resourceDelta = new HashMap<>();
        /** Signals the radio assistant caught on their own. */
        public int passiveSignals;
    }

    /** Bunker-wide modifiers from power and modules for this interval. */
    public static final class Conditions {
        public static final Conditions NONE = new Conditions(1.0, 0, 0);

        /** Healing multiplier for everyone (infirmary module and power). */
        public final double heal;
        /** Stress relief per hour for everyone (powered infirmary). */
        public final double relief;
        /** Stress gained per hour by everyone (filters short of power). */
        public final double badAir;

        public Conditions(double heal, double relief, double badAir) {
            this.heal = heal;
            this.relief = relief;
            this.badAir = badAir;
        }
    }

    public Result advance(Collection<Scout> scouts, Affinities affinities,
                          Map<String, Integer> resources, long now) {
        return advance(scouts, affinities, resources, now, Conditions.NONE);
    }

    public Result advance(Collection<Scout> scouts, Affinities affinities,
                          Map<String, Integer> resources, long now, Conditions conditions) {
        Result out = new Result();
        List<Scout> home = new ArrayList<>();
        for (Scout s : scouts) {
            if (s.getStatus() == ScoutStatus.IDLE) {
                home.add(s);
            }
        }

        for (Scout s : home) {
            double hours = Math.max(0, now - s.getRecoveredAtMillis()) / MILLIS_PER_HOUR;
            TaskDef task = taskOf(s);

            double relief = 0;
            double heal = 1.0;
            for (Scout other : home) {
                TaskDef t = taskOf(other);
                if (other == s || t == null) {
                    continue;
                }
                relief += t.getGroupStressReliefPerHour();
                if (t.getGroupHealMultiplier() != 1.0) {
                    heal *= t.getGroupHealMultiplier() * rules.healOthersMultiplier(other);
                }
                if (task != null && t.getId().equals(task.getId())) {
                    affinities.add(s.getId(), other.getId(),
                            sign(s, other) * affinity.getSameTaskPerHour() * hours / 2);
                    // Friends working side by side calm each other; rivals grate.
                    double closeness = affinities.get(s.getId(), other.getId());
                    if (closeness >= affinity.getFriendAt()) {
                        relief += affinity.getSharedTaskEffect();
                    } else if (closeness <= affinity.getRivalAt()) {
                        relief -= affinity.getSharedTaskEffect();
                    }
                }
            }
            double self = task != null ? task.getSelfStressRecoveryMultiplier() : 1.0;
            rules.recoverIdle(s, now, self, heal * conditions.heal,
                    relief + conditions.relief - conditions.badAir);

            if (task != null && hours > 0) {
                work(s, task, home, hours, affinities, resources, now, out);
            }
        }

        for (Scout s : scouts) {
            if (s.isAlive()) {
                neglect(s, now);
            }
        }
        return out;
    }

    private void work(Scout s, TaskDef task, List<Scout> home, double hours, Affinities affinities,
                      Map<String, Integer> resources, long now, Result out) {
        StatType stat = StatType.fromKey(task.getStat());
        double rate = task.getXpPerHour();
        if (task.isMentoring()) {
            Scout mentor = null;
            int bestGap = 0;
            for (Scout other : home) {
                TaskDef t = taskOf(other);
                if (other == s || t == null || !t.getId().equals(task.getId())) {
                    continue;
                }
                for (StatType type : StatType.values()) {
                    int gap = other.stat(type).getValue() - s.stat(type).getValue();
                    if (gap > bestGap && !s.stat(type).isMaxed()) {
                        bestGap = gap;
                        mentor = other;
                        stat = type;
                    }
                }
                affinities.add(s.getId(), other.getId(), sign(s, other) * affinity.getTrainingPerHour() * hours / 2);
            }
            if (mentor != null) {
                rate *= MENTOR_BONUS;
            }
        }

        double xp = s.getLife().getTaskXpCarry() + hours * rate;
        int whole = (int) Math.floor(xp);
        s.getLife().setTaskXpCarry(xp - whole);
        if (whole > 0 && rules.trainXp(s, stat, whole)) {
            Map<String, String> vars = new HashMap<>();
            vars.put("name", s.getName());
            vars.put("stat", content.getTerminal().getStatNames().get(stat.key()));
            vars.put("value", String.valueOf(s.stat(stat).getValue()));
            out.lines.add(LogLine.draft(now, LogChannel.SYSTEM, null,
                    TextTemplate.format(content.getTerminal().getExpedition().getStatUp(), vars), s.getId()));
        }

        if (task.isPassiveSweep()) {
            double progress = s.getLife().getTaskOutputCarry()
                    + hours / content.getBalance().getRadio().getAssistantHoursPerSignal();
            int caught = (int) Math.floor(progress);
            s.getLife().setTaskOutputCarry(progress - caught);
            out.passiveSignals += caught;
        }

        TaskDef.Conversion conversion = task.getConversion();
        if (conversion != null) {
            double progress = s.getLife().getTaskOutputCarry() + hours / conversion.getHoursPerUnit();
            int units = (int) Math.floor(progress);
            int available = resources.getOrDefault(conversion.getFrom(), 0)
                    + out.resourceDelta.getOrDefault(conversion.getFrom(), 0);
            int affordable = Math.min(units, available / conversion.getInput());
            // Without input the work is lost, not banked: idle hands, empty shelves.
            s.getLife().setTaskOutputCarry(progress - units);
            if (affordable > 0) {
                out.resourceDelta.merge(conversion.getFrom(), -affordable * conversion.getInput(), Integer::sum);
                out.resourceDelta.merge(conversion.getTo(), affordable, Integer::sum);
                Map<String, String> vars = new HashMap<>();
                vars.put("amount", String.valueOf(affordable));
                vars.put("resource", content.resource(conversion.getTo()).getName().toLowerCase(Locale.ROOT));
                out.lines.add(LogLine.draft(now, LogChannel.SYSTEM, null,
                        TextTemplate.format(content.getTerminal().getBunker().getWorkshopOutput(), vars), s.getId()));
            }
        }
    }

    /** GDD §5.2: bond fades if the operator ignores them for too long. */
    private void neglect(Scout s, long now) {
        long graceEnd = s.getLife().getLastTalkAtMillis() + bond.getNeglectGraceHours() * 3_600_000L;
        long from = Math.max(graceEnd, s.getLife().getLastDecayAtMillis());
        if (now <= from) {
            return;
        }
        long days = (now - from) / DAY;
        if (days > 0) {
            rules.addBond(s, (int) -(days * bond.getNeglectPerDay()));
            s.getLife().setLastDecayAtMillis(from + days * DAY);
        }
    }

    /** −1 if their traits clash (GDD §5.3: rivalries), else +1. */
    private double sign(Scout a, Scout b) {
        for (com.estatica.domain.content.TraitDef ta : rules.traitsOf(a)) {
            for (com.estatica.domain.content.TraitDef tb : rules.traitsOf(b)) {
                if (ta.getClashes().contains(tb.getId()) || tb.getClashes().contains(ta.getId())) {
                    return -1;
                }
            }
        }
        return 1;
    }

    /** Relationship label key between two scouts: "inseparable", "friends", "rivals" or null. */
    public String relationship(Affinities affinities, Scout a, Scout b) {
        double v = affinities.get(a.getId(), b.getId());
        if (v >= affinity.getInseparableAt()) {
            return "inseparable";
        }
        if (v >= affinity.getFriendAt()) {
            return "friends";
        }
        return v <= affinity.getRivalAt() ? "rivals" : null;
    }

    private TaskDef taskOf(Scout s) {
        String id = s.getLife().getTaskId();
        return id != null ? content.task(id) : null;
    }
}
