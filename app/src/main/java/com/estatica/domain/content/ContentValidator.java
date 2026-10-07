package com.estatica.domain.content;

import com.estatica.domain.radio.SignalField;
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
            "giftLiked", "giftDisliked", "giftNeutral",
            "idle", "idleTense", "aboutFriend", "aboutRival", "hungry", "carryHome");

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

    /** Event and option requirements must point at real traits and backgrounds. */
    private void checkRequirements(java.util.List<String> traits, String background, String where) {
        for (String t : traits) {
            checkTrait(t, where);
        }
        check(background == null || content.getPeople().background(background) != null,
                where + ": unknown background " + background);
    }

    /** Generated people: every part must exist, and every part must have something to say. */
    private void checkPeople() {
        PeopleDef people = content.getPeople();
        Balance.People gen = content.getBalance().getPeople();
        check(gen != null, "balance: people");
        if (gen != null) {
            check(gen.getStatBase() != null && gen.getStatBase().length == 2 && gen.getStatBase()[0] >= 1
                            && gen.getStatBase()[0] <= gen.getStatBase()[1],
                    "balance.people: statBase [min, max]");
            check(gen.getCeilingGain() != null && gen.getCeilingGain().length == 2 && gen.getCeilingGain()[0] >= 1
                            && gen.getCeilingGain()[0] <= gen.getCeilingGain()[1],
                    "balance.people: ceilingGain [min, max]");
            check(gen.getMaxPendingRescues() >= 1, "balance.people: maxPendingRescues ≥ 1");
        }
        check(people.getNames().size() >= 30, "people: at least 30 names");
        check(new java.util.HashSet<>(people.getNames()).size() == people.getNames().size(), "people: duplicate names");
        check(people.getBackgrounds().size() >= 6, "people: at least 6 backgrounds");
        java.util.Set<String> backgroundIds = new java.util.HashSet<>();
        for (PeopleDef.Background b : people.getBackgrounds()) {
            String where = "background " + b.getId();
            check(backgroundIds.add(b.getId()), where + ": duplicate id");
            check(b.getName() != null && !b.getBios().isEmpty(), where + ": name, bios");
            check(StatType.fromKey(b.getPrimaryStat()) != null, where + ": unknown primaryStat");
            check(b.getSecondaryStat() == null || StatType.fromKey(b.getSecondaryStat()) != null,
                    where + ": unknown secondaryStat");
            check(!b.getVoices().isEmpty(), where + ": voices");
            for (String v : b.getVoices()) {
                check(content.getVoices().containsKey(v), where + ": unknown voice " + v);
            }
            for (String taste : b.getLikes()) {
                checkTaste(taste, where + ": likes");
            }
            for (String taste : b.getDislikes()) {
                checkTaste(taste, where + ": dislikes");
            }
            check(b.getHabits().size() >= 2, where + ": at least 2 habits");
            check(!b.getRescueLines().isEmpty(), where + ": rescueLines");
            long talks = content.getDialogues().values().stream()
                    .filter(d -> b.getId().equals(d.getRequiresBackground())).count();
            check(talks >= 2, where + ": needs at least 2 dialogues");
            check(content.getStory().getPersonalMissions().stream().anyMatch(m -> b.getId().equals(m.getBackground())),
                    where + ": needs a personal mission");
        }
        for (TraitDef t : content.getTraits().values()) {
            String where = "trait " + t.getId();
            for (String v : t.getVoices()) {
                check(content.getVoices().containsKey(v), where + ": unknown voice " + v);
            }
            for (String taste : t.getLikes()) {
                checkTaste(taste, where + ": likes");
            }
            for (String taste : t.getDislikes()) {
                checkTaste(taste, where + ": dislikes");
            }
            if (t.getKind() != TraitDef.Kind.PERSONALITY) {
                continue;
            }
            check(content.getDialogues().values().stream().anyMatch(d -> d.getRequiresTrait().contains(t.getId())),
                    where + ": needs a personality dialogue");
            if (t.isHideable()) {
                boolean confession = content.getDialogues().values().stream()
                        .filter(d -> t.getId().equals(d.getRequiresHiddenTrait()))
                        .anyMatch(d -> d.getChoices() != null
                                && d.getChoices().stream().allMatch(DialogueDef.Choice::isRevealHidden));
                check(confession, where + ": hideable, needs a confession whose every answer reveals it");
            }
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

        List<ScoutTemplate> templates = new ArrayList<>(content.getStarters());
        templates.addAll(content.getSurvivors());
        for (ScoutTemplate s : content.getSurvivors()) {
            check(s.getBackground() == null || content.getPeople().background(s.getBackground()) != null,
                    "survivor " + s.getId() + ": unknown background " + s.getBackground());
            check(content.getStarters().stream().noneMatch(t -> t.getId().equals(s.getId())),
                    "survivor " + s.getId() + ": id clashes with a starter");
        }
        for (ScoutTemplate s : templates) {
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

        checkPeople();
        Balance.Food food = content.getBalance().getFood();
        check(food != null && food.getRationsPerScoutPerDay() > 0 && food.getKitchenMaxSaving() >= 0
                        && food.getKitchenMaxSaving() < 1 && food.getHungerHealMultiplier() > 0,
                "balance.food: rationsPerScoutPerDay > 0, 0 ≤ kitchenMaxSaving < 1, hungerHealMultiplier > 0");
        Balance.Pairs pairs = content.getBalance().getPairs();
        check(pairs != null && pairs.getLootMultiplier() >= 1 && pairs.getCarryBase() >= 0,
                "balance.pairs: lootMultiplier ≥ 1, carryBase ≥ 0");
        check(content.getTerminal().getExpedition().getPairJoins() != null
                        && content.getTerminal().getExpedition().getCarriedMemory() != null,
                "terminal.expedition: pairJoins, carriedMemory");
        Balance.Gear gearBalance = content.getBalance().getGear();
        int bands = content.getBalance().getBands().size();
        check(gearBalance != null && gearBalance.getFindChance() != null && gearBalance.getRarityWeights() != null
                        && gearBalance.getWear() != null && gearBalance.getRepairCost() != null,
                "balance.gear: findChance, rarityWeights, wear, repairCost");
        if (gearBalance != null && gearBalance.getRepairCost() != null) {
            for (Map<String, Integer> cost : gearBalance.getRepairCost().values()) {
                checkResources(cost, "balance.gear.repairCost");
            }
            check(gearBalance.getFindChance().length >= Math.min(3, bands), "balance.gear.findChance: one per band");
        }
        TerminalText.BaseText baseText = content.getTerminal().getBase();
        check(baseText.getGearLabel() != null && baseText.getGearLabelBroken() != null && baseText.getGearFound() != null
                        && baseText.getGearWorn() != null && baseText.getGearBroken() != null
                        && baseText.getGearRepaired() != null && baseText.getGearRecovered() != null
                        && baseText.getRarities().size() == 3,
                "terminal.base: gear texts and the three rarities");
        TerminalText.BunkerText bunkerText = content.getTerminal().getBunker();
        check(bunkerText.getFoodLow() != null && bunkerText.getFoodOut() != null && bunkerText.getFoodBack() != null,
                "terminal.bunker: foodLow, foodOut, foodBack");
        for (EventDef e : content.getEvents().values()) {
            checkRequirements(e.getRequiresTrait(), e.getRequiresBackground(), "event " + e.getId());
            boolean open = false;
            for (EventDef.Option o : e.getOptions()) {
                checkRequirements(o.getRequiresTrait(), o.getRequiresBackground(),
                        "event " + e.getId() + " option " + o.getId());
                open |= o.getRequiresTrait().isEmpty() && o.getRequiresBackground() == null;
            }
            check(open, "event " + e.getId() + ": needs at least one option anyone can take");
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
            for (String t : d.getRequiresTrait()) {
                checkTrait(t, where);
            }
            if (d.getRequiresHiddenTrait() != null) {
                TraitDef hidden = content.getTraits().get(d.getRequiresHiddenTrait());
                check(hidden != null && hidden.isHideable(), where + ": requiresHiddenTrait must be a hideable trait");
            }
            check(d.getRequiresBackground() == null || content.getPeople().background(d.getRequiresBackground()) != null,
                    where + ": unknown background " + d.getRequiresBackground());
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

        for (BandDef band : content.getBalance().getBands()) {
            for (String kind : band.getSignalWeights().keySet()) {
                check(java.util.Set.of("ZONE", "SURVIVOR", "TRANSMISSION", "RARE").contains(kind),
                        "band " + band.getId() + ": unknown signal kind " + kind);
            }
            check(band.getTuneRadius() > 0 && band.getSignalsPerWindow() > 0,
                    "band " + band.getId() + ": tuneRadius and signalsPerWindow > 0");
        }
        for (ModuleDef module : content.getBalance().getModules()) {
            for (int level = module.getStartLevel(); level < module.getMaxLevel(); level++) {
                checkResources(module.costFrom(level), "module " + module.getId());
            }
        }
        for (GearDef gear : content.getGear().values()) {
            ResourceDef stock = content.getResources().get(gear.getId());
            check(stock != null && stock.getKind() == ResourceDef.Kind.GEAR,
                    "gear " + gear.getId() + ": needs a GEAR resource with the same id");
            if (gear.getStatBonus() != null) {
                for (String stat : gear.getStatBonus().keySet()) {
                    check(StatType.fromKey(stat) != null, "gear " + gear.getId() + ": unknown stat " + stat);
                }
            }
        }
        for (RecipeDef recipe : content.getRecipes().values()) {
            String where = "recipe " + recipe.getId();
            checkResources(recipe.getInputs(), where);
            check(content.getResources().containsKey(recipe.getOutput()), where + ": unknown output " + recipe.getOutput());
            check(recipe.getMinutes() > 0 && recipe.getAmount() > 0, where + ": minutes and amount > 0");
        }
        StoryDef story = content.getStory();
        java.util.Set<String> fragments = new java.util.HashSet<>();
        for (StoryDef.Chapter chapter : story.getChapters()) {
            check(chapter.getFinale() != null && !chapter.getFinale().isEmpty(), "chapter " + chapter.getId() + ": finale");
            for (StoryDef.Fragment f : chapter.getFragments()) {
                check(fragments.add(f.getId()), "story: duplicate fragment " + f.getId());
            }
        }
        for (SignalsDef.Transmission t : content.getSignals().getTransmissions()) {
            if (t.getReward() == SignalsDef.Transmission.Reward.PLOT) {
                check(fragments.contains(t.getPlot()), "transmission " + t.getId() + ": unknown fragment " + t.getPlot());
            }
        }
        for (EventDef e : content.getEvents().values()) {
            if (e.getOptions() == null) {
                continue;
            }
            for (EventDef.Option o : e.getOptions()) {
                for (EventDef.Outcome out : java.util.Arrays.asList(o.getSuccess(), o.getFailure())) {
                    if (out != null && out.getPlot() != null) {
                        check(fragments.contains(out.getPlot()), "event " + e.getId() + ": unknown fragment " + out.getPlot());
                    }
                }
            }
        }
        for (StoryDef.PersonalMission m : story.getPersonalMissions()) {
            String where = "personal " + m.getId();
            check((m.getScout() == null) != (m.getBackground() == null), where + ": needs a scout or a background");
            check(m.getScout() == null || content.starter(m.getScout()) != null, where + ": unknown scout " + m.getScout());
            check(m.getBackground() == null || content.getPeople().background(m.getBackground()) != null,
                    where + ": unknown background " + m.getBackground());
            check(content.getZones().containsKey(m.getZone()), where + ": unknown zone " + m.getZone());
            check(content.getEvents().containsKey(m.getEvent()), where + ": unknown event " + m.getEvent());
            if (m.getRewardTrait() != null) {
                checkTrait(m.getRewardTrait(), where);
            }
            boolean offered = false;
            for (DialogueDef d : content.getDialogues().values()) {
                for (DialogueDef.Choice c : d.getChoices() != null ? d.getChoices() : java.util.List.<DialogueDef.Choice>of()) {
                    offered |= c.getSetFlags().contains(m.offeredFlag());
                }
            }
            check(offered, where + ": no dialogue offers it");
        }
        java.util.List<StoryDef.Goal> goals = new java.util.ArrayList<>(story.getObjectives().getDaily());
        goals.add(story.getObjectives().getWeekly());
        for (StoryDef.Goal g : goals) {
            check(java.util.Set.of("decode", "expeditions", "sweeps").contains(g.getCounter()),
                    "goal " + g.getId() + ": unknown counter " + g.getCounter());
            checkResources(g.getReward(), "goal " + g.getId());
        }
        for (TraitDef trait : content.getTraits().values()) {
            for (String other : trait.getClashes()) {
                checkTrait(other, "trait " + trait.getId() + " clashes");
            }
        }

        EnergyDef energy = content.getBalance().getEnergy();
        for (String id : java.util.List.of("filters", "radio", "antenna", "workshop", "infirmary")) {
            check(energy.getConsumers().stream().anyMatch(c -> c.getId().equals(id)), "energy: consumer " + id);
        }
        for (String id : java.util.List.of("antenna", "generator", "radio", "workshop", "infirmary", "bunks")) {
            check(content.getBalance().getModules().stream().anyMatch(m -> m.getId().equals(id)), "modules: " + id);
        }
        checkResources(content.getBalance().getRadio().getRelayMaintenanceCost(), "radio.relayMaintenanceCost");

        SignalsDef signals = content.getSignals();
        for (SignalField.Kind kind : SignalField.Kind.values()) {
            List<String> teasers = signals.getTeasers().get(kind.name());
            check(teasers != null && !teasers.isEmpty(), "signals: teasers." + kind.name());
        }
        for (SignalsDef.Rare rare : signals.getRare()) {
            if (rare.getBand() != null) {
                checkBand(rare.getBand(), "rare " + rare.getId());
            }
            check(rare.getText() != null && !rare.getText().isEmpty(), "rare " + rare.getId() + ": text");
            checkResources(rare.getResources(), "rare " + rare.getId());
        }
        for (SignalsDef.Transmission t : signals.getTransmissions()) {
            String where = "transmission " + t.getId();
            if (t.getBand() != null) {
                checkBand(t.getBand(), where);
            }
            check(t.getReward() != null, where + ": reward");
            check(t.getText() != null && !com.estatica.domain.radio.Cipher.normalize(
                    t.getText().replace("{zone}", "")).isEmpty(), where + ": text");
            checkResources(t.getResources(), where);
            check(t.getReward() != SignalsDef.Transmission.Reward.LORE || t.getLore() != null, where + ": lore");
        }

        List<ZoneDef> allZones = new ArrayList<>(content.getZones().values());
        allZones.addAll(content.getWilds());
        java.util.Set<String> bandsWithPlaces = new java.util.HashSet<>();
        for (ZoneDef z : content.getZones().values()) {
            bandsWithPlaces.add(z.getBand());
        }
        for (String band : bandsWithPlaces) {
            check(content.wilds(band) != null, "band " + band + ": places but no scouting ground (wilds) to find them");
        }
        TerminalText.BaseText mapText = content.getTerminal().getBase();
        check(mapText.getMapScouted() != null && mapText.getMapFoundZone() != null && mapText.getMapNothing() != null
                        && mapText.getMapHeard() != null && mapText.getDirections().size() == 8,
                "terminal.base: map texts and eight directions");
        for (ZoneDef z : allZones) {
            String where = "zone " + z.getId();
            check(z.getName() != null, where + ": name");
            checkRange(z.getDurationMinutes(), 1, where + ": durationMinutes");
            checkRange(z.getSituations(), 0, where + ": situations");
            checkRange(z.getFinds(), 0, where + ": finds");
            check(z.getDanger() >= 1, where + ": danger ≥ 1");
            checkBand(z.getBand(), where);
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
            // Anyone sees the open options plus the ones that fit them: 2–4 open, 6 at most in all.
            long open = options == null ? 0 : options.stream()
                    .filter(o -> o.getRequiresTrait().isEmpty() && o.getRequiresBackground() == null).count();
            check(options != null && open >= 2 && open <= 4 && options.size() <= 6,
                    where + ": needs 2–4 options anyone can take, 6 at most in all");
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
        for (String mood : List.of("content", "steady", "tense", "breaking")) {
            check(content.getTerminal().getMoods() != null && content.getTerminal().getMoods().containsKey(mood),
                    "terminal: moods." + mood);
        }
        for (DialogueDef d : content.getDialogues().values()) {
            check(d.getRequiresRelation() == null
                            || List.of("friend", "inseparable", "rival").contains(d.getRequiresRelation()),
                    "dialogue " + d.getId() + ": unknown relation " + d.getRequiresRelation());
        }
        check(content.getBalance().getBarks() != null, "balance: barks");
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

    private void checkBand(String band, String where) {
        boolean known = false;
        for (BandDef b : content.getBalance().getBands()) {
            known |= b.getId().equals(band);
        }
        check(known, where + ": unknown band " + band);
    }

    private void checkResources(Map<String, Integer> amounts, String where) {
        if (amounts == null) {
            return;
        }
        for (String resource : amounts.keySet()) {
            check(content.getResources().containsKey(resource), where + ": unknown resource " + resource);
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
