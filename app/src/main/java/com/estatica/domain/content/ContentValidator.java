package com.estatica.domain.content;

import com.estatica.domain.scouts.Injury;
import com.estatica.domain.scouts.ScoutStatus;
import com.estatica.domain.scouts.StatType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks references between content files (traits, voices, events, resources, stats) and
 * value ranges. Collects every problem, so one test run shows them all.
 */
public final class ContentValidator {

    /** Phrase keys every voice must define (used by the expedition engine). */
    public static final List<String> REQUIRED_PHRASES = List.of(
            "departure", "arrival", "oddsGood", "oddsUnsure", "oddsBad", "disobey",
            "autoDecide", "injured", "find", "lastWords", "lastMemory", "lastOrder",
            "initiate", "askReturn", "goingHome", "findItem", "lastGift", "lastCompanion",
            "giftLiked", "giftDisliked", "giftNeutral");

    private final GameContent content;
    private final List<String> problems = new ArrayList<>();

    private ContentValidator(GameContent content) {
        this.content = content;
    }

    /** @throws IllegalStateException listing every problem found */
    public static void validate(GameContent content) {
        ContentValidator v = new ContentValidator(content);
        v.run();
        if (!v.problems.isEmpty()) {
            throw new IllegalStateException("Invalid content:\n - " + String.join("\n - ", v.problems));
        }
    }

    private void run() {
        int statMax = content.getBalance().getScouts().getStatMax();

        for (TraitDef trait : content.getTraits().values()) {
            check(trait.getName() != null, "trait " + trait.getId() + ": name");
            if (trait.getStatBonus() != null) {
                for (String stat : trait.getStatBonus().keySet()) {
                    check(StatType.fromKey(stat) != null, "trait " + trait.getId() + ": unknown stat " + stat);
                }
            }
        }

        for (ScoutTemplate s : content.getStarters()) {
            String where = "scout " + s.getId();
            check(s.getName() != null, where + ": name");
            check(content.getVoices().containsKey(s.getVoice()), where + ": unknown voice " + s.getVoice());
            if (s.getStats() == null) {
                problems.add(where + ": stats");
            } else {
                for (StatType type : StatType.values()) {
                    int[] range = s.getStats().get(type.key());
                    check(range != null && range.length == 2 && range[0] >= 0
                                    && range[0] <= range[1] && range[1] <= statMax,
                            where + ": stat " + type.key() + " needs [base, ceiling] with base ≤ ceiling ≤ " + statMax);
                }
            }
            check(s.getTraits() != null, where + ": traits");
            if (s.getTraits() != null) {
                for (String t : s.getTraits()) {
                    checkTrait(t, where);
                }
            }
            if (s.getHiddenTrait() != null) {
                checkTrait(s.getHiddenTrait(), where);
            }
            for (String taste : s.getLikes()) {
                checkTaste(taste, where + ": likes");
            }
            for (String taste : s.getDislikes()) {
                checkTaste(taste, where + ": dislikes");
            }
        }

        for (TaskDef task : content.getTasks().values()) {
            String where = "task " + task.getId();
            check(task.getName() != null, where + ": name");
            check(StatType.fromKey(task.getStat()) != null, where + ": unknown stat " + task.getStat());
            if (task.getConversion() != null) {
                check(content.getResources().containsKey(task.getConversion().getFrom())
                                && content.getResources().containsKey(task.getConversion().getTo())
                                && task.getConversion().getInput() > 0
                                && task.getConversion().getHoursPerUnit() > 0,
                        where + ": conversion");
            }
        }

        for (DialogueDef d : content.getDialogues().values()) {
            String where = "dialogue " + d.getId();
            check(d.getScout() == null || content.starter(d.getScout()) != null,
                    where + ": unknown scout " + d.getScout());
            check(d.getLines() != null && !d.getLines().isEmpty(), where + ": lines");
            check(d.getChoices() != null && !d.getChoices().isEmpty() && d.getChoices().size() <= 4,
                    where + ": needs 1–4 choices");
            for (String stat : d.getRequiresStats().keySet()) {
                check(StatType.fromKey(stat) != null, where + ": unknown stat " + stat);
            }
            if (d.getChoices() == null) {
                continue;
            }
            for (DialogueDef.Choice c : d.getChoices()) {
                check(c.getLabel() != null, where + ": choice label");
                if (c.getAddTrait() != null) {
                    checkTrait(c.getAddTrait(), where);
                }
                for (String flag : c.getSetFlags()) {
                    if (flag.startsWith("likes:") || flag.startsWith("dislikes:")) {
                        String item = flag.substring(flag.indexOf(':') + 1);
                        check(content.getItems().containsKey(item), where + ": taste flag for unknown item " + item);
                    }
                }
            }
        }

        for (String trait : content.getBalance().getGrief().getTraits()) {
            checkTrait(trait, "balance grief.traits");
        }

        for (ZoneDef z : content.getZones().values()) {
            String where = "zone " + z.getId();
            check(z.getName() != null, where + ": name");
            checkRange(z.getDurationMinutes(), 1, where + ": durationMinutes");
            checkRange(z.getSituations(), 0, where + ": situations");
            checkRange(z.getFinds(), 0, where + ": finds");
            check(z.getDanger() >= 1, where + ": danger ≥ 1");
            check(z.getTransit() != null && !z.getTransit().isEmpty(), where + ": transit");
            check(z.getLoot() != null && !z.getLoot().isEmpty(), where + ": loot");
            checkLoot(z.getLoot(), where);
            check(z.getEvents() != null && !z.getEvents().isEmpty(), where + ": events");
            for (ItemDrop drop : z.getItems()) {
                check(content.getItems().containsKey(drop.getItem()), where + ": unknown item " + drop.getItem());
                check(drop.getChance() > 0 && drop.getChance() < 1, where + ": item chance in (0, 1)");
            }
            if (z.getEvents() != null) {
                for (String e : z.getEvents()) {
                    check(content.getEvents().containsKey(e), where + ": unknown event " + e);
                }
            }
        }

        for (EventDef e : content.getEvents().values()) {
            String where = "event " + e.getId();
            check(e.getIntro() != null && !e.getIntro().isEmpty(), where + ": intro");
            List<EventDef.Option> options = e.getOptions();
            check(options != null && options.size() >= 2 && options.size() <= 4, where + ": needs 2–4 options");
            if (options == null) {
                continue;
            }
            for (EventDef.Option o : options) {
                String ow = where + "/" + o.getId();
                check(o.getLabel() != null, ow + ": label");
                check(StatType.fromKey(o.getStat()) != null, ow + ": unknown stat " + o.getStat());
                checkOutcome(o.getSuccess(), ow + " success");
                checkOutcome(o.getFailure(), ow + " failure");
            }
        }

        for (VoiceDef voice : content.getVoices().values()) {
            Map<String, List<String>> phrases = voice.getPhrases();
            for (String key : REQUIRED_PHRASES) {
                check(phrases != null && phrases.get(key) != null && !phrases.get(key).isEmpty(),
                        "voice " + voice.getId() + ": missing phrases " + key);
            }
        }

        Map<String, String> statNames = content.getTerminal().getStatNames();
        for (StatType type : StatType.values()) {
            check(statNames.containsKey(type.key()), "terminal: statNames." + type.key());
        }
        Map<String, String> statusLabels = content.getTerminal().getScoutStatus();
        for (ScoutStatus status : ScoutStatus.values()) {
            check(statusLabels != null && statusLabels.containsKey(status.name()),
                    "terminal: scoutStatus." + status.name());
        }
        Map<String, String> injuryLabels = content.getTerminal().getInjuries();
        for (Injury injury : Injury.values()) {
            check(injuryLabels != null && injuryLabels.containsKey(injury.name()),
                    "terminal: injuries." + injury.name());
        }
    }

    private void checkOutcome(EventDef.Outcome outcome, String where) {
        if (outcome == null) {
            problems.add(where + ": missing");
            return;
        }
        check(outcome.getText() != null && !outcome.getText().isEmpty(), where + ": text");
        try {
            Injury.fromKey(outcome.getInjury());
        } catch (IllegalArgumentException ex) {
            problems.add(where + ": unknown injury " + outcome.getInjury());
        }
        checkLoot(outcome.getLoot(), where);
    }

    private void checkLoot(List<LootRoll> loot, String where) {
        if (loot == null) {
            return;
        }
        for (LootRoll roll : loot) {
            check(content.getResources().containsKey(roll.getResource()),
                    where + ": unknown resource " + roll.getResource());
            check(roll.getMin() >= 1 && roll.getMin() <= roll.getMax() && roll.getWeight() > 0,
                    where + ": loot " + roll.getResource() + " needs 1 ≤ min ≤ max, weight > 0");
        }
    }

    /** A taste is an item id or a tag used by some item. */
    private void checkTaste(String taste, String where) {
        boolean known = content.getItems().containsKey(taste);
        for (ItemDef item : content.getItems().values()) {
            known |= item.getTags().contains(taste);
        }
        check(known, where + ": unknown item or tag " + taste);
    }

    private void checkTrait(String id, String where) {
        check(content.getTraits().containsKey(id), where + ": unknown trait " + id);
    }

    private void checkRange(int[] range, int min, String where) {
        check(range != null && range.length == 2 && range[0] >= min && range[0] <= range[1],
                where + " needs [min, max] with " + min + " ≤ min ≤ max");
    }

    private void check(boolean condition, String problem) {
        if (!condition) {
            problems.add(problem);
        }
    }
}
