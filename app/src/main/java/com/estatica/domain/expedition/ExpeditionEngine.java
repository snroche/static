package com.estatica.domain.expedition;

import com.estatica.core.GameRandom;
import com.estatica.core.Ids;
import com.estatica.core.TextTemplate;
import com.estatica.domain.content.Balance;
import com.estatica.domain.content.EventDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.GearDef;
import com.estatica.domain.content.ItemDrop;
import com.estatica.domain.content.LootRoll;
import com.estatica.domain.content.TerminalText;
import com.estatica.domain.content.VoiceDef;
import com.estatica.domain.content.ZoneDef;
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;
import com.estatica.domain.radio.SignalDegrader;
import com.estatica.domain.scouts.Injury;
import com.estatica.domain.scouts.Memory;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutRules;
import com.estatica.domain.scouts.StatType;
import com.estatica.domain.scouts.VoiceRenderer;
import com.estatica.domain.time.ElapsedFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Expedition rules: dispatch, catch-up of due segments, and operator decisions.
 *
 * <p>Nothing simulates in the background: {@link #advance} resolves, in order, every
 * segment due by {@code now}. Advancing once to T gives the same result as advancing in
 * many smaller steps up to T.
 */
public final class ExpeditionEngine {

    private static final long MINUTE = 60_000L;
    private static final double LAST_WORDS_NOISE = 0.25;
    private static final double LAST_MEMORY_NOISE = 0.4;

    private final GameContent content;
    private final ScoutRules rules;
    private final CheckCalculator checks;
    private final AutoDecider decider;
    private final Balance.Scouts scoutBalance;
    private final Balance.Expedition expeditionBalance;
    private final TerminalText.ExpeditionText text;

    public ExpeditionEngine(GameContent content) {
        this.content = content;
        this.rules = new ScoutRules(content);
        this.checks = new CheckCalculator(content.getBalance().getChecks(), rules);
        this.decider = new AutoDecider(checks);
        this.scoutBalance = content.getBalance().getScouts();
        this.expeditionBalance = content.getBalance().getExpedition();
        this.text = content.getTerminal().getExpedition();
    }

    public ScoutRules rules() {
        return rules;
    }

    // ---- Pairs (GDD §5.3) ----

    /** Who goes along with the lead, and how they get on ("friends", "inseparable", "rivals" or null). */
    public static final class Party {
        public static final Party ALONE = new Party(null, null);

        public final Scout partner;
        public final String relation;

        public Party(Scout partner, String relation) {
            this.partner = partner;
            this.relation = relation;
        }

        boolean isPair() {
            return partner != null;
        }
    }

    // ---- Dispatch ----

    public static final class Dispatch {
        public final Expedition expedition;
        public final List<LogLine> lines;

        Dispatch(Expedition expedition, List<LogLine> lines) {
            this.expedition = expedition;
            this.lines = lines;
        }
    }

    /**
     * Sends a scout to a zone. {@code seed} should come from the game RNG
     * ({@link GameRandom#deriveSeed()}).
     */
    public Dispatch dispatch(Scout scout, ZoneDef zone, long seed, long now) {
        return dispatch(scout, zone, seed, Mission.explore(), now);
    }

    public Dispatch dispatch(Scout scout, ZoneDef zone, long seed, Mission mission, long now) {
        return dispatch(scout, zone, seed, mission, Party.ALONE, now);
    }

    /** A pair moves at the slower one's pace; situations can come up for either of them. */
    public Dispatch dispatch(Scout scout, ZoneDef zone, long seed, Mission mission, Party party, long now) {
        if (!rules.canDispatch(scout) || (party.isPair() && (!rules.canDispatch(party.partner) || party.partner == scout))) {
            throw new IllegalStateException(scout.getName() + " can't be dispatched");
        }
        GameRandom rng = GameRandom.fromSeed(seed);
        double pace = party.isPair()
                ? Math.max(rules.durationMultiplier(scout), rules.durationMultiplier(party.partner))
                : rules.durationMultiplier(scout);
        List<Segment> segments = TimelineGenerator.generate(zone, pace, rng,
                id -> fits(scout, content.event(id)) || (party.isPair() && fits(party.partner, content.event(id))));
        if (mission.getType() == Mission.Type.PERSONAL) {
            forceEvent(segments, content.getStory().personalMission(mission.getTarget()).getEvent());
        }
        rules.markDeparted(scout, now);

        VoiceDef voice = content.voice(scout.getVoiceId());
        Map<String, String> vars = vars(scout);
        vars.put("zone", zone.getName());
        vars.put("eta", ElapsedFormatter.format(TimelineGenerator.plannedDuration(segments),
                content.getTerminal().getElapsedUnits()));
        List<LogLine> lines = new ArrayList<>();
        lines.add(system(now, TextTemplate.format(text.getDispatched(), vars), scout));
        lines.add(radio(now, scout, say(voice, scout, "departure", rng, vars)));
        if (party.isPair()) {
            rules.markDeparted(party.partner, now);
            Map<String, String> pairVars = vars(party.partner);
            pairVars.put("other", scout.getName());
            lines.add(system(now, TextTemplate.format(text.getPairJoins(), pairVars), party.partner));
        }

        Expedition expedition = new Expedition(Ids.newId(), scout.getId(), zone.getId(), seed, now,
                segments, rng.getState(), Expedition.Status.ACTIVE, 0, now, Expedition.NONE,
                Expedition.NO_CHOICE, Expedition.NONE, Map.of(), List.of(), false, mission, false, 0);
        expedition.setPartnerId(party.isPair() ? party.partner.getId() : null);
        return new Dispatch(expedition, lines);
    }

    /**
     * A personal mission plays its own situation: it replaces the first one (or, if the
     * timeline has none, the first leg after leaving).
     */
    private static void forceEvent(List<Segment> segments, String eventId) {
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            if (s.getType() == Segment.Type.SITUATION) {
                segments.set(i, new Segment(Segment.Type.SITUATION, s.getDurationMillis(), eventId));
                return;
            }
        }
        Segment first = segments.get(Math.min(1, segments.size() - 2));
        segments.set(segments.indexOf(first), new Segment(Segment.Type.SITUATION, first.getDurationMillis(), eventId));
    }

    // ---- Decisions ----

    public long timeoutMillis() {
        return expeditionBalance.getSituationTimeoutMinutes() * MINUTE;
    }

    /** When the pending situation is decided by the scout alone. */
    public long deadline(Expedition expedition) {
        return expedition.getPendingSinceMillis() + timeoutMillis();
    }

    /** The situation waiting for the operator, or null. */
    public EventDef pendingEvent(Expedition expedition) {
        return expedition.isAwaitingDecision()
                ? content.event(expedition.currentSegment().getEventId())
                : null;
    }

    /** The pending situation as the scout described it, without radio noise (for the decision card). */
    public List<String> situationText(Expedition expedition, Scout scout) {
        EventDef event = pendingEvent(expedition);
        if (event == null) {
            return List.of();
        }
        VoiceDef voice = content.voice(scout.getVoiceId());
        List<String> out = new ArrayList<>();
        for (String line : event.getIntro()) {
            out.add(VoiceRenderer.render(voice, line, vars(scout)));
        }
        return out;
    }

    /**
     * What the scout said about each option: "GOOD", "UNSURE", "BAD", "LOST" (didn't come
     * through the static) or null (not theirs to take). Indexes match the event's options.
     */
    public List<String> pendingHints(Expedition expedition, Scout scout, Party party) {
        EventDef event = pendingEvent(expedition);
        if (event == null) {
            return List.of();
        }
        ZoneDef zone = content.zone(expedition.getZoneId());
        List<String> out = new ArrayList<>();
        for (int i = 0; i < event.getOptions().size(); i++) {
            EventDef.Option option = event.getOptions().get(i);
            if (!isAvailable(scout, party, option)) {
                out.add(null);
            } else if (!expedition.heardHint(i)) {
                out.add("LOST");
            } else {
                out.add(checks.hint(chanceFor(expedition, scout, party, option, zone)).name());
            }
        }
        return out;
    }

    /** Whether the situation can come up for this scout (event requirements). */
    public boolean fits(Scout scout, EventDef event) {
        return rules.fits(scout, event.getRequiresTrait(), event.getRequiresBackground());
    }

    /** Whether this scout can take an option: some only exist for certain people. */
    public boolean isAvailable(Scout scout, EventDef.Option option) {
        return rules.fits(scout, option.getRequiresTrait(), option.getRequiresBackground());
    }

    /** On a pair, what either of them can do. */
    public boolean isAvailable(Scout scout, Party party, EventDef.Option option) {
        return isAvailable(scout, option) || (party.isPair() && isAvailable(party.partner, option));
    }

    /**
     * Button labels for the pending situation, including "come home" when offered. Indexes
     * match the event's options; options this scout can't take are null (skip them).
     */
    public List<String> pendingOptions(Expedition expedition, Scout scout) {
        return pendingOptions(expedition, scout, Party.ALONE);
    }

    public List<String> pendingOptions(Expedition expedition, Scout scout, Party party) {
        EventDef event = pendingEvent(expedition);
        if (event == null) {
            return List.of();
        }
        List<String> labels = new ArrayList<>();
        for (EventDef.Option option : event.getOptions()) {
            labels.add(isAvailable(scout, party, option) ? option.getLabel() : null);
        }
        if (expedition.isRetreatOffered()) {
            labels.add(content.getTerminal().getBunker().getRetreatLabel());
        }
        return labels;
    }

    /** What a dying scout can mention (GDD §5.5). */
    public static final class DeathContext {
        public static final DeathContext EMPTY = new DeathContext(List.of(), null);

        /** Names of gifts they carry, most recent last. */
        public final List<String> gifts;
        /** Name of the companion they feel closest to, or null. */
        public final String companion;

        public DeathContext(List<String> gifts, String companion) {
            this.gifts = gifts;
            this.companion = companion;
        }
    }

    /**
     * Records the operator's order. Call {@link #advance} up to {@code now} first, then again
     * afterwards to resolve it.
     *
     * @return false if there is nothing to decide anymore (e.g. it timed out)
     */
    public boolean choose(Expedition expedition, int option, long now) {
        if (!expedition.isAwaitingDecision()
                || now < expedition.getPendingSinceMillis()
                || now >= deadline(expedition)) {
            return false;
        }
        int count = content.event(expedition.currentSegment().getEventId()).getOptions().size()
                + (expedition.isRetreatOffered() ? 1 : 0);
        if (option < 0 || option >= count) {
            throw new IllegalArgumentException("option " + option + " of " + count);
        }
        // GDD §4.4: far away, the order takes a while to reach them. If it arrives after the
        // deadline, they will already have decided on their own.
        expedition.setChoice(option, now + expedition.getMission().getOrderDelayMillis());
        return true;
    }

    /**
     * Estimated return time: the rest of the timeline from now, counting a waiting situation
     * as resolved at its deadline (or when the order arrives). An estimate for the UI.
     */
    public long estimatedReturn(Expedition expedition, long now) {
        if (!expedition.isActive()) {
            return now;
        }
        List<Segment> segments = expedition.getSegments();
        long at;
        int from;
        if (expedition.getPendingSinceMillis() != Expedition.NONE) {
            at = Math.max(now, orderArrivesInTime(expedition) ? expedition.getChosenAtMillis() : deadline(expedition));
            from = expedition.getIndex() + 1;
        } else {
            at = expedition.getCursorAtMillis();
            from = expedition.getIndex();
        }
        for (int i = from; i < segments.size(); i++) {
            at += segments.get(i).getDurationMillis();
        }
        return Math.max(at, now);
    }

    /** Next moment something happens without input, or {@link Expedition#NONE}. For notifications. */
    public long nextEventAt(Expedition expedition) {
        if (!expedition.isActive()) {
            return Expedition.NONE;
        }
        if (expedition.getPendingSinceMillis() != Expedition.NONE) {
            return orderArrivesInTime(expedition) ? expedition.getChosenAtMillis() : deadline(expedition);
        }
        return expedition.getCursorAtMillis() + expedition.currentSegment().getDurationMillis();
    }

    /** Something worth a notification, fully determined by the stored timeline. */
    public static final class Notice {
        public enum Type { SITUATION, RETURN }

        public final Type type;
        public final long atMillis;
        /** For SITUATION. */
        public final String eventId;

        Notice(Type type, long atMillis, String eventId) {
            this.type = type;
            this.atMillis = atMillis;
            this.eventId = eventId;
        }
    }

    /**
     * The next situation or return of this expedition, computed without resolving anything
     * (only transits and finds can lie in between, and those need no decisions). Null if a
     * situation is already waiting: the operator has seen it, and what follows depends on
     * how it gets resolved.
     */
    public Notice nextNotice(Expedition expedition) {
        if (!expedition.isActive() || expedition.getPendingSinceMillis() != Expedition.NONE) {
            return null;
        }
        long at = expedition.getCursorAtMillis();
        List<Segment> segments = expedition.getSegments();
        for (int i = expedition.getIndex(); i < segments.size(); i++) {
            Segment segment = segments.get(i);
            at += segment.getDurationMillis();
            if (segment.getType() == Segment.Type.SITUATION) {
                return new Notice(Notice.Type.SITUATION, at, segment.getEventId());
            }
            if (segment.getType() == Segment.Type.RETURN) {
                return new Notice(Notice.Type.RETURN, at, null);
            }
        }
        return null;
    }

    // ---- Catch-up ----

    /**
     * Resolves every segment due by {@code now}.
     *
     * @param memories the scout's existing memories, newest last (used for last words)
     */
    public ExpeditionResult advance(Expedition expedition, Scout scout, List<Memory> memories, long now) {
        return advance(expedition, scout, memories, DeathContext.EMPTY, now);
    }

    public ExpeditionResult advance(Expedition expedition, Scout scout, List<Memory> memories,
                                    DeathContext death, long now) {
        return advance(expedition, scout, Party.ALONE, memories, death, now);
    }

    public ExpeditionResult advance(Expedition expedition, Scout scout, Party party, List<Memory> memories,
                                    DeathContext death, long now) {
        ExpeditionResult out = new ExpeditionResult();
        if (!expedition.isActive()) {
            return out;
        }
        ZoneDef zone = content.zone(expedition.getZoneId());
        VoiceDef voice = content.voice(scout.getVoiceId());
        GameRandom rng = GameRandom.fromState(expedition.getRngState());
        List<Memory> known = new ArrayList<>(memories);

        while (expedition.isActive()) {
            Segment segment = expedition.currentSegment();
            long due = expedition.getCursorAtMillis() + segment.getDurationMillis();

            if (segment.getType() == Segment.Type.SITUATION) {
                if (expedition.getPendingSinceMillis() == Expedition.NONE) {
                    if (due > now) {
                        break;
                    }
                    presentSituation(expedition, scout, party, zone, voice, rng, due, out);
                }
                long resolveAt;
                boolean ordered;
                if (orderArrivesInTime(expedition)) {
                    if (expedition.getChosenAtMillis() > now) {
                        break; // the order is still on its way
                    }
                    resolveAt = expedition.getChosenAtMillis();
                    ordered = true;
                } else if (now >= deadline(expedition)) {
                    resolveAt = deadline(expedition);
                    ordered = false;
                } else {
                    break;
                }
                resolveSituation(expedition, scout, party, zone, voice, rng, resolveAt, ordered, known, death, out);
            } else {
                if (due > now) {
                    break;
                }
                switch (segment.getType()) {
                    case TRANSIT:
                        out.line(heard(expedition, rng, due, scout, VoiceRenderer.render(voice,
                                rng.pick(zone.getTransit()), vars(scout))));
                        pairChatter(expedition, scout, party, rng, due, out);
                        expedition.advance(due);
                        break;
                    case FIND:
                        resolveFind(expedition, scout, party, zone, voice, rng, due, out);
                        expedition.advance(due);
                        break;
                    case RETURN:
                        resolveReturn(expedition, scout, party, voice, rng, due, out);
                        break;
                    default:
                        throw new IllegalStateException(segment.getType().name());
                }
            }
        }
        expedition.setRngState(rng.getState());
        return out;
    }

    private void presentSituation(Expedition expedition, Scout scout, Party party, ZoneDef zone, VoiceDef voice,
                                  GameRandom rng, long at, ExpeditionResult out) {
        expedition.markPending(at);
        EventDef event = content.event(expedition.currentSegment().getEventId());
        Map<String, String> vars = vars(scout);
        for (String line : event.getIntro()) {
            out.line(heard(expedition, rng, at, scout, VoiceRenderer.render(voice, line, vars)));
        }
        List<EventDef.Option> options = event.getOptions();
        double noise = expedition.getMission().getNoise();
        double hintLoss = Math.min(0.8, noise * content.getBalance().getRadio().getHintLossPerNoise());
        int shown = 0;
        for (int i = 0; i < options.size(); i++) {
            EventDef.Option option = options.get(i);
            if (!isAvailable(scout, party, option)) {
                continue;
            }
            OddsHint hint = checks.hint(chanceFor(expedition, scout, party, option, zone));
            Map<String, String> optionVars = new HashMap<>(vars);
            optionVars.put("n", String.valueOf(++shown));
            optionVars.put("label", option.getLabel());
            if (noise > 0 && rng.chance(hintLoss)) {
                // GDD §4.4: less information from far away.
                expedition.markHintLost(i);
                optionVars.put("hint", content.getTerminal().getRadio().getHintLost());
            } else {
                optionVars.put("hint", VoiceRenderer.phrase(voice, hint.phraseKey(), rng, vars));
            }
            out.line(radio(at, scout, TextTemplate.format(text.getOptionLine(), optionVars)));
        }
        if (rules.asksToReturn(scout)) {
            expedition.setRetreatOffered(true);
            out.line(radio(at, scout, say(voice, scout, "askReturn", rng, vars)));
        }
        out.line(alert(at, TextTemplate.format(text.getAwaitingOrders(), vars), scout));
        out.markSituationArrived();
    }

    private void resolveSituation(Expedition expedition, Scout scout, Party party, ZoneDef zone, VoiceDef voice,
                                  GameRandom rng, long at, boolean ordered, List<Memory> known,
                                  DeathContext death, ExpeditionResult out) {
        EventDef event = content.event(expedition.currentSegment().getEventId());
        Map<String, String> vars = vars(scout);

        if (ordered && expedition.getChosenOption() == event.getOptions().size()) {
            // The operator told them to come home.
            out.line(radio(at, scout, say(voice, scout, "goingHome", rng, vars)));
            rules.addBond(scout, content.getBalance().getBond().getRetreatGranted());
            expedition.abortToReturn(at);
            return;
        }

        int choice;
        boolean byOrder = ordered;
        if (ordered) {
            choice = expedition.getChosenOption();
            if (scout.getTrust() < rules.disobeyThreshold(scout)
                    && rng.chance(expeditionBalance.getDisobeyChance())) {
                int own = decider.pick(event, scout, zone, rules.bias(scout), rng, o -> isAvailable(scout, party, o));
                if (own != choice) {
                    choice = own;
                    byOrder = false;
                    out.line(radio(at, scout, say(voice, scout, "disobey", rng, vars)));
                }
            }
        } else {
            choice = decider.pick(event, scout, zone, rules.bias(scout), rng, o -> isAvailable(scout, party, o));
            out.line(radio(at, scout, say(voice, scout, "autoDecide", rng, vars)));
        }

        EventDef.Option option = event.getOptions().get(choice);
        double chance = chanceFor(expedition, scout, party, option, zone);
        // GDD §5.5: warned = already in bad shape, or the scout said it looked bad and the
        // operator actually heard it through the static.
        boolean badOdds = checks.hint(chance) == OddsHint.BAD;
        boolean warned = rules.isWarned(scout) || (badOdds && expedition.heardHint(choice));
        if (byOrder && badOdds && option.getRisk() == EventDef.Option.Risk.RISKY) {
            // GDD §5.2: exposing them to unnecessary risk hurts the bond.
            rules.addBond(scout, -content.getBalance().getBond().getRiskyOrderPenalty());
        }
        boolean success = rng.nextDouble() < chance;
        EventDef.Outcome outcome = success ? option.getSuccess() : option.getFailure();

        for (String line : outcome.getText()) {
            out.line(heard(expedition, rng, at, scout, VoiceRenderer.render(voice, line, vars)));
        }

        StatType stat = StatType.fromKey(option.getStat());
        if (rules.train(scout, stat, success)) {
            Map<String, String> statVars = vars(scout);
            statVars.put("stat", content.getTerminal().getStatNames().get(stat.key()));
            statVars.put("value", String.valueOf(scout.stat(stat).getValue()));
            out.line(system(at, TextTemplate.format(text.getStatUp(), statVars), scout));
        }
        int stress = outcome.getStress()
                + (success ? scoutBalance.getStressOnSuccess() : scoutBalance.getStressOnFailure());
        rules.addStress(scout, stress);
        if (party.isPair()) {
            rules.addStress(party.partner, stress);
            if (rules.train(party.partner, stat, success)) {
                Map<String, String> statVars = vars(party.partner);
                statVars.put("stat", content.getTerminal().getStatNames().get(stat.key()));
                statVars.put("value", String.valueOf(party.partner.stat(stat).getValue()));
                out.line(system(at, TextTemplate.format(text.getStatUp(), statVars), party.partner));
            }
        }
        if (byOrder) {
            rules.addTrust(scout, success ? scoutBalance.getTrustOnSuccess() : -scoutBalance.getTrustOnFailure());
        }
        if (outcome.getLoot() != null) {
            for (LootRoll roll : outcome.getLoot()) {
                expedition.addLoot(roll.getResource(), rollAmount(roll, scout, party, rng));
            }
        }
        if (outcome.getPlot() != null) {
            out.plot(outcome.getPlot());
        }
        if (outcome.getMemory() != null) {
            remember(Memory.create(scout.getId(), Memory.Type.EVENT,
                    TextTemplate.format(outcome.getMemory(), vars), event.getId(), at), known, out);
        }

        Injury incoming = Injury.fromKey(outcome.getInjury());
        boolean notableOrder = byOrder && (success
                ? option.getRisk() == EventDef.Option.Risk.RISKY
                : incoming != Injury.NONE || outcome.isLethal());
        if (notableOrder) {
            remember(Memory.create(scout.getId(), Memory.Type.ORDER, option.getLabel(),
                    event.getId(), at), known, out);
        }

        // GDD §5.5: a death must follow from the operator's decision. It needs a warning AND
        // an order; choices the scout made alone (timeout, disobedience) hurt but never kill
        // (§4.2: being away doesn't punish hard). Otherwise the listed injury applies, and
        // that injury is the warning for next time.
        boolean lethal = outcome.isLethal()
                || (incoming != Injury.NONE && scout.getInjury() == Injury.CRITICAL);
        if (lethal && warned && byOrder) {
            if (carriedHome(scout, party, rng)) {
                // GDD §5.3: one can carry the other home. Alive, barely.
                rules.injure(scout, Injury.CRITICAL);
                Map<String, String> carryVars = vars(party.partner);
                carryVars.put("other", scout.getName());
                VoiceDef partnerVoice = content.voice(party.partner.getVoiceId());
                out.line(radio(at, party.partner, say(partnerVoice, party.partner, "carryHome", rng, carryVars)));
                remember(Memory.create(scout.getId(), Memory.Type.EVENT,
                        TextTemplate.format(text.getCarriedMemory(), carryVars), event.getId(), at), known, out);
                expedition.abortToReturn(at);
                return;
            }
            die(expedition, scout, voice, rng, at, known, death, out);
            if (party.isPair()) {
                rules.markReturned(party.partner, at); // comes home alone
            }
            return;
        }
        com.estatica.domain.bunker.GearPiece kit = rules.injuryAbsorber(scout);
        if (incoming != Injury.NONE && kit != null) {
            // Used up: one level less of damage.
            GearDef gear = content.gear(kit.getGearId());
            incoming = incoming.better();
            scout.getLife().wear(gear.getSlot(), null);
            kit.moveTo(com.estatica.domain.bunker.GearPiece.GONE);
            Map<String, String> gearVars = vars(scout);
            gearVars.put("gear", gear.getName().toLowerCase(Locale.ROOT));
            out.line(system(at, TextTemplate.format(content.getTerminal().getBase().getGearSaved(), gearVars), scout));
        }
        rules.injure(scout, incoming);
        if (incoming != Injury.NONE) {
            out.line(radio(at, scout, say(voice, scout, "injured", rng, vars)));
        }
        if (scout.getInjury() == Injury.CRITICAL) {
            expedition.abortToReturn(at);
        } else {
            expedition.advance(at);
        }
    }

    private void resolveFind(Expedition expedition, Scout scout, Party party, ZoneDef zone, VoiceDef voice,
                             GameRandom rng, long at, ExpeditionResult out) {
        LootRoll roll = weightedPick(zone.getLoot(), rng);
        int amount = rollAmount(roll, scout, party, rng);
        expedition.addLoot(roll.getResource(), amount);
        Map<String, String> vars = vars(scout);
        vars.put("amount", String.valueOf(amount));
        vars.put("resource", content.resource(roll.getResource()).getName().toLowerCase(Locale.ROOT));
        out.line(heard(expedition, rng, at, scout, VoiceRenderer.phrase(voice, "find", rng, vars)));

        for (ItemDrop drop : zone.getItems()) {
            if (rng.chance(drop.getChance())) {
                expedition.addItem(drop.getItem());
                Map<String, String> itemVars = vars(scout);
                itemVars.put("item", content.item(drop.getItem()).getName().toLowerCase(Locale.ROOT));
                out.line(radio(at, scout, VoiceRenderer.phrase(voice, "findItem", rng, itemVars)));
                break;
            }
        }
    }

    private void resolveReturn(Expedition expedition, Scout scout, Party party, VoiceDef voice, GameRandom rng,
                               long at, ExpeditionResult out) {
        Map<String, String> vars = vars(scout);
        out.line(radio(at, scout, say(voice, scout, "arrival", rng, vars)));
        if (expedition.getMission().hasCacheIntel() && !expedition.isAborted()) {
            // Decoded intel led them to a stash.
            ZoneDef zone = content.zone(expedition.getZoneId());
            for (int i = 0; i < content.getBalance().getRadio().getCacheIntelLootRolls(); i++) {
                LootRoll roll = weightedPick(zone.getLoot(), rng);
                expedition.addLoot(roll.getResource(), rollAmount(roll, scout, party, rng));
            }
        }
        if (party.isPair()) {
            VoiceDef partnerVoice = content.voice(party.partner.getVoiceId());
            out.line(radio(at, party.partner, say(partnerVoice, party.partner, "arrival", rng, vars(party.partner))));
            rules.markReturned(party.partner, at);
        }
        if (!expedition.isAborted()) {
            out.markMissionCompleted();
        }
        Map<String, Integer> loot = expedition.getCarriedLoot();
        if (loot.isEmpty()) {
            out.line(system(at, TextTemplate.format(text.getReturnedEmpty(), vars), scout));
        } else {
            vars.put("loot", describeLoot(loot));
            out.line(system(at, TextTemplate.format(text.getReturnedWithLoot(), vars), scout));
        }
        out.deliver(loot);
        out.deliverItems(expedition.getCarriedItems());
        for (String itemId : expedition.getCarriedItems()) {
            Map<String, String> itemVars = vars(scout);
            itemVars.put("item", content.item(itemId).getName().toLowerCase(Locale.ROOT));
            out.line(system(at, TextTemplate.format(content.getTerminal().getBunker().getItemFound(), itemVars), scout));
        }
        expedition.clearLoot();
        expedition.clearItems();
        rules.markReturned(scout, at);
        expedition.setStatus(Expedition.Status.COMPLETED);
        out.markReturned();
    }

    /**
     * GDD §5.5: the last transmission is built from what they carry and remember: a gift,
     * the companion they love, something the operator said. In their voice, breaking up.
     */
    private void die(Expedition expedition, Scout scout, VoiceDef voice, GameRandom rng, long at,
                     List<Memory> known, DeathContext death, ExpeditionResult out) {
        Map<String, String> vars = vars(scout);
        boolean close = rules.isClose(scout);
        List<String> lastWords = new ArrayList<>();
        lastWords.add(SignalDegrader.degrade(
                VoiceRenderer.phrase(voice, "lastWords", rng, vars, close), LAST_WORDS_NOISE, rng));

        List<String> mentions = new ArrayList<>();
        if (!death.gifts.isEmpty()) {
            Map<String, String> v = vars(scout);
            v.put("gift", death.gifts.get(death.gifts.size() - 1));
            mentions.add(VoiceRenderer.phrase(voice, "lastGift", rng, v));
        }
        if (death.companion != null) {
            Map<String, String> v = vars(scout);
            v.put("companion", death.companion);
            mentions.add(VoiceRenderer.phrase(voice, "lastCompanion", rng, v));
        }
        Memory memory = lastMeaningfulMemory(known);
        if (memory != null) {
            Map<String, String> v = vars(scout);
            v.put("memory", memory.getText());
            String key = memory.getType() == Memory.Type.ORDER ? "lastOrder" : "lastMemory";
            mentions.add(VoiceRenderer.phrase(voice, key, rng, v));
        }
        for (int i = 0; i < Math.min(2, mentions.size()); i++) {
            lastWords.add(SignalDegrader.degrade(mentions.get(i), LAST_MEMORY_NOISE, rng));
        }
        for (String line : lastWords) {
            out.line(radio(at, scout, line));
        }
        out.line(system(at, text.getSignalLost(), scout));
        out.line(system(at, TextTemplate.format(text.getDeathNotice(), vars), scout));
        out.memory(Memory.create(scout.getId(), Memory.Type.LAST_WORDS,
                String.join("\n", lastWords), expedition.getZoneId(), at));

        rules.markDead(scout, at, expedition.getZoneId());
        expedition.clearLoot();
        expedition.clearItems();
        expedition.setStatus(Expedition.Status.LOST);
        out.markScoutDied();
    }

    private static Memory lastMeaningfulMemory(List<Memory> known) {
        for (int i = known.size() - 1; i >= 0; i--) {
            Memory.Type type = known.get(i).getType();
            if (type == Memory.Type.TALK || type == Memory.Type.EVENT || type == Memory.Type.ORDER) {
                return known.get(i);
            }
        }
        return null;
    }

    private static void remember(Memory memory, List<Memory> known, ExpeditionResult out) {
        known.add(memory);
        out.memory(memory);
    }

    private int rollAmount(LootRoll roll, Scout scout, Party party, GameRandom rng) {
        int base = rng.nextIntInclusive(roll.getMin(), roll.getMax());
        double pair = party.isPair() ? content.getBalance().getPairs().getLootMultiplier() : 1.0;
        return Math.max(1, (int) Math.round(base * rules.lootMultiplier(scout) * pair));
    }

    /** Whether the partner manages to drag a dying lead home. */
    private boolean carriedHome(Scout scout, Party party, GameRandom rng) {
        if (!party.isPair() || party.partner.getInjury() == Injury.CRITICAL) {
            return false;
        }
        Balance.Pairs pairs = content.getBalance().getPairs();
        double chance = pairs.getCarryBase()
                + rules.effectiveStat(party.partner, StatType.ENDURANCE) * pairs.getCarryPerEndurance()
                + pairs.relationBonus(party.relation) * 2;
        return rng.nextDouble() < Math.max(0, Math.min(0.95, chance));
    }

    /** On the road, the partner talks: friends about each other, rivals at each other. */
    private void pairChatter(Expedition expedition, Scout scout, Party party, GameRandom rng, long at,
                             ExpeditionResult out) {
        Balance.Pairs pairs = content.getBalance().getPairs();
        if (!party.isPair() || party.relation == null || !rng.chance(pairs.getChatterChance())) {
            return;
        }
        boolean rivals = "rivals".equals(party.relation);
        Map<String, String> v = vars(party.partner);
        v.put("other", scout.getName());
        VoiceDef partnerVoice = content.voice(party.partner.getVoiceId());
        out.line(heard(expedition, rng, at, party.partner,
                say(partnerVoice, party.partner, rivals ? "aboutRival" : "aboutFriend", rng, v)));
        if (rivals) {
            rules.addStress(scout, pairs.getRivalArgueStress());
            rules.addStress(party.partner, pairs.getRivalArgueStress());
        }
    }

    private static LootRoll weightedPick(List<LootRoll> table, GameRandom rng) {
        int total = 0;
        for (LootRoll roll : table) {
            total += roll.getWeight();
        }
        int ticket = rng.nextInt(total);
        for (LootRoll roll : table) {
            ticket -= roll.getWeight();
            if (ticket < 0) {
                return roll;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    public String describeLoot(Map<String, Integer> loot) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : loot.entrySet()) {
            Map<String, String> v = new HashMap<>();
            v.put("amount", String.valueOf(e.getValue()));
            v.put("resource", content.resource(e.getKey()).getName().toLowerCase(Locale.ROOT));
            parts.add(TextTemplate.format(text.getLootItem(), v));
        }
        return String.join(text.getLootSeparator(), parts);
    }

    /** An order counts if it reaches the scout before they decide alone. */
    private boolean orderArrivesInTime(Expedition expedition) {
        return expedition.getChosenOption() != Expedition.NO_CHOICE
                && expedition.getChosenAtMillis() < deadline(expedition);
    }

    private double chanceFor(Expedition expedition, Scout scout, EventDef.Option option, ZoneDef zone) {
        return chanceFor(expedition, scout, Party.ALONE, option, zone);
    }

    /** Intel, plus on a pair: the partner's help where they're better, and how they get on. */
    private double chanceFor(Expedition expedition, Scout scout, Party party, EventDef.Option option, ZoneDef zone) {
        double bonus = expedition.getMission().hasDangerIntel()
                ? content.getBalance().getRadio().getDangerIntelCheckBonus() : 0;
        if (party.isPair()) {
            Balance.Pairs pairs = content.getBalance().getPairs();
            StatType stat = StatType.fromKey(option.getStat());
            int better = rules.effectiveStat(party.partner, stat) - rules.effectiveStat(scout, stat);
            bonus += Math.min(pairs.getMaxSupport(), Math.max(0, better) * pairs.getSupportPerPoint())
                    + pairs.relationBonus(party.relation);
        }
        return checks.chance(scout, option, zone, bonus);
    }

    /** A transmission from the field, broken up by distance (GDD §4.4). */
    private LogLine heard(Expedition expedition, GameRandom rng, long at, Scout scout, String line) {
        double noise = expedition.getMission().getNoise();
        return radio(at, scout, noise > 0 ? SignalDegrader.degrade(line, noise, rng) : line);
    }

    /** A phrase in the scout voice, warmer when the bond is high. */
    private String say(VoiceDef voice, Scout scout, String key, GameRandom rng, Map<String, String> vars) {
        return VoiceRenderer.phrase(voice, key, rng, vars, rules.isClose(scout));
    }

    private static Map<String, String> vars(Scout scout) {
        Map<String, String> vars = new HashMap<>();
        vars.put("name", scout.getName());
        return vars;
    }

    private static LogLine radio(long at, Scout scout, String line) {
        return LogLine.draft(at, LogChannel.RADIO, scout.getName(), line, scout.getId());
    }

    private static LogLine system(long at, String line, Scout scout) {
        return LogLine.draft(at, LogChannel.SYSTEM, null, line, scout.getId());
    }

    private static LogLine alert(long at, String line, Scout scout) {
        return LogLine.draft(at, LogChannel.ALERT, null, line, scout.getId());
    }
}
