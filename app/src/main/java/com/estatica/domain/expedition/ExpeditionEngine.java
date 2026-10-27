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
import java.util.Set;

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
    private final com.estatica.domain.scouts.RetrainRules retrain;
    private final com.estatica.domain.bunker.GearRules gearRules;
    private final CheckCalculator checks;
    private final AutoDecider decider;
    private final Balance.Scouts scoutBalance;
    private final Balance.Expedition expeditionBalance;
    private final TerminalText.ExpeditionText text;

    public ExpeditionEngine(GameContent content) {
        this.content = content;
        this.rules = new ScoutRules(content);
        this.retrain = new com.estatica.domain.scouts.RetrainRules(content.getBalance().getRetrain());
        this.gearRules = new com.estatica.domain.bunker.GearRules(content);
        this.checks = new CheckCalculator(content.getBalance().getChecks(), rules);
        this.decider = new AutoDecider(checks);
        this.scoutBalance = content.getBalance().getScouts();
        this.expeditionBalance = content.getBalance().getExpedition();
        this.text = content.getTerminal().getExpedition();
    }

    public ScoutRules rules() {
        return rules;
    }

    /** Weather and darkness: calm unless the session wires the real thing. */
    private com.estatica.domain.world.World world = com.estatica.domain.world.World.CALM;

    public void setWorld(com.estatica.domain.world.World world) {
        this.world = world;
    }

    /** Key items the bunker already has: situations that only give one of those don't come up. */
    private java.util.function.Predicate<String> hasKey = k -> false;

    public void setKeyOwnership(java.util.function.Predicate<String> hasKey) {
        this.hasKey = hasKey;
    }

    /** Story fragments whose chapter is open (the next chapter waits for the last one). */
    private java.util.function.Predicate<String> plotOpen = f -> true;

    public void setPlotOpen(java.util.function.Predicate<String> plotOpen) {
        this.plotOpen = plotOpen;
    }

    /** Bunker-wide flags set by earlier choices (and "seen:id" for once-only situations). */
    private java.util.function.Predicate<String> worldFlag = f -> false;

    public void setWorldFlags(java.util.function.Predicate<String> worldFlag) {
        this.worldFlag = worldFlag;
    }

    /** Flag a once-only situation leaves behind when it comes up. */
    /** What the bunker has of a resource (for prices out there). Set by the session. */
    private java.util.function.ToIntFunction<String> stock = id -> 0;

    public void setStock(java.util.function.ToIntFunction<String> stock) {
        this.stock = stock;
    }

    /** Whether they can pay for an option: what they carry plus what the bunker has. */
    public boolean affordable(Expedition expedition, EventDef.Option option) {
        for (EventDef.Cost c : option.getCost()) {
            int carried = expedition.getCarriedLoot().getOrDefault(c.getResource(), 0);
            if (carried + stock.applyAsInt(c.getResource()) < c.getAmount()) {
                return false;
            }
        }
        return true;
    }

    /** Someone on the team can take it, and it can be paid for. */
    private boolean canTake(Expedition expedition, Scout scout, Party party, EventDef.Option option) {
        return isAvailable(scout, party, option) && affordable(expedition, option);
    }

    /** Pays an option's price: out of what they carry first, the rest from the bunker. */
    private void pay(Expedition expedition, EventDef.Option option, Map<String, Integer> paid, ExpeditionResult out) {
        for (EventDef.Cost c : option.getCost()) {
            int carried = expedition.getCarriedLoot().getOrDefault(c.getResource(), 0);
            int fromBag = Math.min(carried, c.getAmount());
            if (fromBag > 0) {
                expedition.addLoot(c.getResource(), -fromBag);
            }
            int fromBunker = Math.min(c.getAmount() - fromBag, stock.applyAsInt(c.getResource()));
            if (fromBunker > 0) {
                out.spend(c.getResource(), fromBunker);
            }
            if (fromBag + fromBunker > 0) {
                paid.merge(c.getResource(), fromBag + fromBunker, Integer::sum);
            }
        }
    }

    public static String seenFlag(String eventId) {
        return "seen:" + eventId;
    }

    /** Carried-home prefixes, next to key items ("key:"). */
    public static final String GEAR_PREFIX = "gear:";
    public static final String PART_PREFIX = "part:";

    /**
     * Whether a situation can come up on a trip leaving now: the weather and the hour, earlier
     * choices, once-only ones already had, and the luck of the draw for rare ones (its own dice,
     * so the rest of the trip comes out the same).
     */
    private boolean inTheCards(EventDef event, long seed, long now) {
        if (!event.getWeather().isEmpty() && !event.getWeather().contains(world.weatherAt(now).name())) {
            return false;
        }
        if (event.getNight() != null && event.getNight() != world.nightAt(now)) {
            return false;
        }
        for (String f : event.getRequiresWorld()) {
            if (!worldFlag.test(f)) {
                return false;
            }
        }
        for (String f : event.getForbidsWorld()) {
            if (worldFlag.test(f)) {
                return false;
            }
        }
        if (event.isOnce() && worldFlag.test(seenFlag(event.getId()))) {
            return false;
        }
        return event.getChance() >= 1.0
                || GameRandom.fromSeed(seed ^ (event.getId().hashCode() * 0x9E3779B97F4A7C15L)).chance(event.getChance());
    }

    private boolean stillUseful(EventDef event) {
        if (event.isFollowUp()) {
            return false; // only reached from the situation before it
        }
        if (event.getGrantsKey() != null && hasKey.test(event.getGrantsKey())) {
            return false;
        }
        for (EventDef.Option o : event.getOptions()) {
            for (EventDef.Outcome out : new EventDef.Outcome[]{o.getSuccess(), o.getFailure()}) {
                if (out != null && out.getPlot() != null && !plotOpen.test(out.getPlot())) {
                    return false; // part of a chapter that hasn't started
                }
            }
        }
        return true;
    }

    // ---- Teams (GDD §5.3) ----

    /**
     * Who goes along with the lead (up to two), and how each gets on with the lead
     * ("friends", "inseparable", "rivals" or none).
     */
    public static final class Party {
        public static final Party ALONE = new Party(List.of(), Map.of());

        /** Everyone going along with the lead, in pick order. */
        public final List<Scout> companions;
        /** Companion id to their relation with the lead (absent: nothing special). */
        public final Map<String, String> relations;
        /** The first companion, or null. */
        public final Scout partner;
        /** The first companion's relation with the lead, or null. */
        public final String relation;
        /** Gear ids of rides left parked (no fuel). */
        public final Set<String> stalled;

        public Party(Scout partner, String relation) {
            this(partner == null ? List.of() : List.of(partner),
                    partner == null || relation == null ? Map.of() : Map.of(partner.getId(), relation));
        }

        public Party(List<Scout> companions, Map<String, String> relations) {
            this(companions, relations, Set.of());
        }

        /** @param stalled rides that stay parked: no fuel for them */
        public Party(List<Scout> companions, Map<String, String> relations, Set<String> stalled) {
            this.stalled = Set.copyOf(stalled);
            this.companions = List.copyOf(companions);
            this.relations = Map.copyOf(relations);
            this.partner = companions.isEmpty() ? null : companions.get(0);
            this.relation = partner == null ? null : relations.get(partner.getId());
        }

        boolean isPair() {
            return !companions.isEmpty();
        }

        String relationOf(Scout s) {
            return relations.get(s.getId());
        }

        /** Lead first, then the companions. */
        public List<Scout> everyone(Scout lead) {
            List<Scout> out = new ArrayList<>();
            out.add(lead);
            out.addAll(companions);
            return out;
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
        if (!rules.canDispatch(scout)) {
            throw new IllegalStateException(scout.getName() + " can't be dispatched");
        }
        for (Scout c : party.companions) {
            if (c == scout || !rules.canDispatch(c)) {
                throw new IllegalStateException(c.getName() + " can't go along");
            }
        }
        GameRandom rng = GameRandom.fromSeed(seed);
        double pace = teamPace(scout, party) * deepDuration(mission);
        List<String> pool = new ArrayList<>(zone.getEvents());
        for (EventDef e : content.getEvents().values()) {
            if (e.getBands().contains(zone.getBand()) && !pool.contains(e.getId())) {
                pool.add(e.getId());
            }
        }
        List<Segment> segments = TimelineGenerator.generate(zone, pace, rng, pool,
                id -> stillUseful(content.event(id))
                        && inTheCards(content.event(id), seed, now)
                        && anyoneFits(scout, party, content.event(id)),
                recentEvents);
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
        for (Scout c : party.companions) {
            rules.markDeparted(c, now);
            Map<String, String> pairVars = vars(c);
            pairVars.put("other", scout.getName());
            lines.add(system(now, TextTemplate.format(text.getPairJoins(), pairVars), c));
        }

        Expedition expedition = new Expedition(Ids.newId(), scout.getId(), zone.getId(), seed, now,
                segments, rng.getState(), Expedition.Status.ACTIVE, 0, now, Expedition.NONE,
                Expedition.NO_CHOICE, Expedition.NONE, Map.of(), List.of(), false, mission, false, 0);
        expedition.setPartnerId(party.companions.size() > 0 ? party.companions.get(0).getId() : null);
        expedition.setThirdId(party.companions.size() > 1 ? party.companions.get(1).getId() : null);
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
            if (!canTake(expedition, scout, party, option)) {
                out.add(null);
            } else if (!expedition.heardHint(i)) {
                out.add("LOST");
            } else {
                OddsHint hint = checks.hint(chanceFor(expedition, scout, party, option, zone,
                        expedition.getPendingSinceMillis()));
                out.add(hint == OddsHint.BAD && couldKill(scout, option) ? "DEADLY" : hint.name());
            }
        }
        return out;
    }

    /**
     * The operator calls them home. The order takes the usual radio delay to arrive; from then
     * on they head straight back. Nothing happens if they're already on their way.
     *
     * @return what they say, or an empty list if there was nothing to recall
     */
    public List<LogLine> recall(Expedition expedition, Scout scout, long now) {
        if (!expedition.isActive() || expedition.isAborted()) {
            return List.of();
        }
        long arrives = now + expedition.getMission().getOrderDelayMillis();
        expedition.abortToReturn(Math.max(arrives, expedition.getCursorAtMillis()));
        VoiceDef voice = content.voice(scout.getVoiceId());
        List<LogLine> lines = new ArrayList<>();
        lines.add(radio(arrives, scout, say(voice, scout, "goingHome",
                GameRandom.fromState(expedition.getRngState()), vars(scout))));
        return lines;
    }

    /**
     * Ordered into this with a bad feeling heard, failing it could kill them (GDD §5.5): the
     * decision card says so plainly.
     */
    private boolean couldKill(Scout scout, EventDef.Option option) {
        EventDef.Outcome f = option.getFailure();
        return f != null && (f.isLethal()
                || (Injury.fromKey(f.getInjury()) != Injury.NONE && scout.getInjury() == Injury.CRITICAL));
    }

    /** Whether the situation can come up for this scout (event requirements). */
    public boolean fits(Scout scout, EventDef event) {
        return rules.fits(scout, event.getRequiresTrait(), event.getRequiresBackground());
    }

    /** Whether this scout can take an option: some only exist for certain people. */
    public boolean isAvailable(Scout scout, EventDef.Option option) {
        return rules.fits(scout, option.getRequiresTrait(), option.getRequiresBackground());
    }

    /** On a team, what any of them can do. */
    public boolean isAvailable(Scout scout, Party party, EventDef.Option option) {
        if (isAvailable(scout, option)) {
            return true;
        }
        for (Scout c : party.companions) {
            if (isAvailable(c, option)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyoneFits(Scout scout, Party party, EventDef event) {
        if (fits(scout, event)) {
            return true;
        }
        for (Scout c : party.companions) {
            if (fits(c, event)) {
                return true;
            }
        }
        return false;
    }

    /** Their gear takes some of the stress out there (canteen, poncho…). */
    private int softened(Scout scout, int stress) {
        return stress <= 0 ? stress : (int) Math.round(stress * rules.gearStressFactor(scout));
    }

    /**
     * Trouble is hard on gear: a failed check scuffs it, an injury ({@code injury} non-null)
     * more so. Says so if something gives out right there.
     */
    private void scuff(Scout scout, String injury, long at, ExpeditionResult out) {
        for (com.estatica.domain.bunker.GearPiece piece : rules.working(scout)) {
            if (injury == null) {
                gearRules.wearForFailure(piece);
            } else {
                gearRules.wearForInjury(piece, injury);
            }
            if (piece.isBroken()) {
                Map<String, String> v = vars(scout);
                v.put("gear", content.gear(piece.getGearId()).getName().toLowerCase(Locale.ROOT));
                v.put("condition", "0");
                out.line(system(at, TextTemplate.format(content.getTerminal().getBase().getGearBroken(), v), scout));
            }
        }
    }

    /** Deep runs take longer the harder the tier. */
    private double deepDuration(Mission mission) {
        return mission.getType() == Mission.Type.DEEP
                ? content.getBalance().getDeepRuns().durationFactor(mission.getTier()) : 1.0;
    }

    /** Everyone walks at the slowest one's pace, unless the team's rides seat them all. */
    public double teamPace(Scout lead, Party party) {
        return rules.teamPace(party.everyone(lead), party.stalled);
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
            labels.add(canTake(expedition, scout, party, option) ? labelOf(expedition, option) : null);
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
                        weatherLine(expedition, scout, voice, rng, due, out);
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
        if (event.isOnce()) {
            out.world(seenFlag(event.getId()));
        }
        String cue = event.getCue();
        for (String line : event.getIntro()) {
            out.line(heard(expedition, rng, at, scout, VoiceRenderer.render(voice, line, vars)).withCue(cue));
            cue = null;
        }
        List<EventDef.Option> options = event.getOptions();
        double noise = Math.min(1.0, expedition.getMission().getNoise() + world.noiseAt(at));
        double hintLoss = Math.min(0.8, noise * content.getBalance().getRadio().getHintLossPerNoise());
        int shown = 0;
        for (int i = 0; i < options.size(); i++) {
            EventDef.Option option = options.get(i);
            if (!canTake(expedition, scout, party, option)) {
                continue;
            }
            OddsHint hint = checks.hint(chanceFor(expedition, scout, party, option, zone, at));
            Map<String, String> optionVars = new HashMap<>(vars);
            optionVars.put("n", String.valueOf(++shown));
            optionVars.put("label", labelOf(expedition, option));
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
        java.util.function.Predicate<EventDef.Option> takeable = o -> canTake(expedition, scout, party, o);
        if (ordered) {
            choice = expedition.getChosenOption();
            if (!takeable.test(event.getOptions().get(choice))) {
                // The bunker spent it before the order got there.
                choice = decider.pick(event, scout, zone, rules.bias(scout), rng, takeable);
                byOrder = false;
                out.line(system(at, TextTemplate.format(text.getCantAfford(), vars), scout));
            } else if (scout.getTrust() < rules.disobeyThreshold(scout)
                    && rng.chance(expeditionBalance.getDisobeyChance())) {
                int own = decider.pick(event, scout, zone, rules.bias(scout), rng, takeable);
                if (own != choice) {
                    choice = own;
                    byOrder = false;
                    out.line(radio(at, scout, say(voice, scout, "disobey", rng, vars)));
                }
            }
        } else {
            choice = decider.pick(event, scout, zone, rules.bias(scout), rng, takeable);
            out.line(radio(at, scout, say(voice, scout, "autoDecide", rng, vars)));
        }

        EventDef.Option option = event.getOptions().get(choice);
        double chance = chanceFor(expedition, scout, party, option, zone, at);
        // GDD §5.5: warned = already in bad shape, or the scout said it looked bad and the
        // operator actually heard it through the static.
        boolean badOdds = checks.hint(chance) == OddsHint.BAD;
        boolean warned = rules.isWarned(scout) || (badOdds && expedition.heardHint(choice));
        if (byOrder && badOdds && option.getRisk() == EventDef.Option.Risk.RISKY) {
            // GDD §5.2: exposing them to unnecessary risk hurts the bond.
            // Sending someone who loves you into that hurts twice.
            rules.addBond(scout, -content.getBalance().getBond().getRiskyOrderPenalty() * (rules.isLover(scout) ? 2 : 1));
        }
        boolean success = rng.nextDouble() < chance;
        EventDef.Outcome outcome = success ? option.getSuccess() : option.getFailure();
        Map<String, Integer> paid = new java.util.LinkedHashMap<>();
        Map<String, Integer> gained = new java.util.LinkedHashMap<>();
        List<String> found = new ArrayList<>();
        Map<String, Integer> lost = new java.util.LinkedHashMap<>();
        if (!outcome.isRefund()) {
            pay(expedition, option, paid, out);
        }
        if (outcome.getLose() > 0) {
            for (Map.Entry<String, Integer> e : new java.util.LinkedHashMap<>(expedition.getCarriedLoot()).entrySet()) {
                int gone = (int) Math.ceil(e.getValue() * outcome.getLose());
                if (gone > 0) {
                    expedition.addLoot(e.getKey(), -gone);
                    lost.put(e.getKey(), gone);
                }
            }
        }

        String cue = outcome.getCue();
        for (String line : outcome.getText()) {
            out.line(heard(expedition, rng, at, scout, VoiceRenderer.render(voice, line, vars)).withCue(cue));
            cue = null;
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
        rules.addStress(scout, softened(scout, stress));
        if (!success) {
            scuff(scout, null, at, out);
        }
        for (Scout c : party.companions) {
            rules.addStress(c, softened(c, stress));
            if (rules.train(c, stat, success)) {
                Map<String, String> statVars = vars(c);
                statVars.put("stat", content.getTerminal().getStatNames().get(stat.key()));
                statVars.put("value", String.valueOf(c.stat(stat).getValue()));
                out.line(system(at, TextTemplate.format(text.getStatUp(), statVars), c));
            }
        }
        if (byOrder) {
            rules.addTrust(scout, success ? scoutBalance.getTrustOnSuccess() : -scoutBalance.getTrustOnFailure());
        }
        if (outcome.getLoot() != null) {
            for (LootRoll roll : outcome.getLoot()) {
                int amount = rollAmount(roll, scout, party, rng);
                expedition.addLoot(roll.getResource(), amount);
                if (amount > 0) {
                    gained.merge(roll.getResource(), amount, Integer::sum);
                }
            }
        }
        if (outcome.getPlot() != null) {
            out.plot(outcome.getPlot());
        }
        if (outcome.getKeyItem() != null && !hasKey.test(outcome.getKeyItem())
                && !expedition.getCarriedItems().contains(com.estatica.domain.content.KeyItemDef.CARRIED_PREFIX + outcome.getKeyItem())) {
            expedition.addItem(com.estatica.domain.content.KeyItemDef.CARRIED_PREFIX + outcome.getKeyItem());
            found.add(com.estatica.domain.content.KeyItemDef.CARRIED_PREFIX + outcome.getKeyItem());
        }
        if (outcome.getGear() != null) {
            EventDef.GearFind g = outcome.getGear();
            expedition.addItem(GEAR_PREFIX + g.getId() + ":" + g.getRarity() + ":" + g.getCondition());
            found.add(GEAR_PREFIX + g.getId());
        }
        if (outcome.getPart() != null && !worldFlag.test(PART_PREFIX + outcome.getPart())
                && !expedition.getCarriedItems().contains(PART_PREFIX + outcome.getPart())) {
            expedition.addItem(PART_PREFIX + outcome.getPart());
            found.add(PART_PREFIX + outcome.getPart());
        }
        if (outcome.getItem() != null) {
            expedition.addItem(outcome.getItem());
            found.add(outcome.getItem());
        }
        for (String flag : outcome.getSetWorld()) {
            out.world(flag);
        }
        if (outcome.getBond() != 0) {
            List<LogLine> bondLines = new ArrayList<>();
            com.estatica.domain.scouts.DialogueOutcomes.applyBond(content, rules, scout, outcome.getBond(), at, bondLines);
            for (LogLine l : bondLines) {
                out.line(l);
            }
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
            Scout carrier = carriedHome(scout, party, rng);
            if (carrier != null) {
                // GDD §5.3: one can carry the other home. Alive, barely.
                rules.injure(scout, Injury.CRITICAL);
                Map<String, String> carryVars = vars(carrier);
                carryVars.put("other", scout.getName());
                VoiceDef partnerVoice = content.voice(carrier.getVoiceId());
                out.line(radio(at, carrier, say(partnerVoice, carrier, "carryHome", rng, carryVars)));
                remember(Memory.create(scout.getId(), Memory.Type.EVENT,
                        TextTemplate.format(text.getCarriedMemory(), carryVars), event.getId(), at), known, out);
                expedition.abortToReturn(at);
                return;
            }
            die(expedition, scout, voice, rng, at, known, death, out);
            for (Scout c : party.companions) {
                rules.markReturned(c, at); // they come home without the lead
            }
            return;
        }
        com.estatica.domain.bunker.GearPiece kit = rules.injuryAbsorber(scout);
        if (incoming != Injury.NONE && kit != null) {
            // Takes the edge off: one level (two for a rare kit). Usually used up.
            GearDef gear = content.gear(kit.getGearId());
            for (int i = 0; i < com.estatica.domain.bunker.GearRules.absorbLevels(kit); i++) {
                incoming = incoming.better();
            }
            if (!rng.chance(com.estatica.domain.bunker.GearRules.absorbSurvives(kit))) {
                scout.getLife().wear(gear.getSlot(), null);
                kit.moveTo(com.estatica.domain.bunker.GearPiece.GONE);
            }
            Map<String, String> gearVars = vars(scout);
            gearVars.put("gear", gear.getName().toLowerCase(Locale.ROOT));
            out.line(system(at, TextTemplate.format(content.getTerminal().getBase().getGearSaved(), gearVars), scout));
        }
        if (incoming != Injury.NONE) {
            scuff(scout, incoming.name(), at, out);
        }
        rules.injure(scout, incoming);
        if (incoming != Injury.NONE) {
            out.line(radio(at, scout, say(voice, scout, "injured", rng, vars)));
        }
        tally(scout, gained, paid, lost, found, incoming, at, out);
        if (scout.getInjury() == Injury.CRITICAL) {
            expedition.abortToReturn(at);
        } else {
            long shift = timeShift(expedition, option, success);
            if (shift != 0) {
                Map<String, String> timeVars = vars(scout);
                timeVars.put("time", ElapsedFormatter.format(Math.abs(shift), content.getTerminal().getElapsedUnits()));
                out.line(system(at, TextTemplate.format(shift > 0 ? text.getTimeLost() : text.getTimeSaved(), timeVars),
                        scout));
            }
            // The next leg starts later (waited it out) or sooner (got through fast).
            String next = outcome.getNext();
            if (next != null && content.event(next) != null) {
                expedition.chainTo(next, at + Math.max(0, shift), expeditionBalance.getFollowUpMillis());
            } else {
                expedition.advance(at + shift);
            }
        }
    }

    /**
     * What came of a situation, plainly: "+3 rations, paid 2 batteries, found a racing bike
     * frame, light injury". The scout's words say what happened; this says what it means.
     */
    private void tally(Scout scout, Map<String, Integer> gained, Map<String, Integer> paid, Map<String, Integer> lost,
                       List<String> found, Injury injury, long at, ExpeditionResult out) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : gained.entrySet()) {
            parts.add(tallyPiece(text.getTallyGain(), e.getKey(), e.getValue()));
        }
        for (Map.Entry<String, Integer> e : paid.entrySet()) {
            parts.add(tallyPiece(text.getTallyPaid(), e.getKey(), e.getValue()));
        }
        for (Map.Entry<String, Integer> e : lost.entrySet()) {
            parts.add(tallyPiece(text.getTallyLost(), e.getKey(), e.getValue()));
        }
        for (String itemId : found) {
            Map<String, String> v = new HashMap<>();
            v.put("item", carriedName(itemId).toLowerCase(Locale.ROOT));
            parts.add(TextTemplate.format(text.getTallyFound(), v));
        }
        if (injury != Injury.NONE) {
            Map<String, String> v = new HashMap<>();
            v.put("injury", content.getTerminal().getInjuries().get(injury.name()));
            parts.add(TextTemplate.format(text.getTallyHurt(), v));
        }
        if (parts.isEmpty()) {
            return;
        }
        Map<String, String> v = vars(scout);
        v.put("tally", String.join(text.getLootSeparator(), parts));
        out.line(system(at, TextTemplate.format(text.getOutcomeTally(), v), scout));
    }

    private String tallyPiece(String template, String resource, int amount) {
        Map<String, String> v = new HashMap<>();
        v.put("amount", String.valueOf(amount));
        v.put("resource", content.resource(resource).getName().toLowerCase(Locale.ROOT));
        return TextTemplate.format(template, v);
    }

    /** What an option does to the trip's length, in millis (0 for nothing, or a failed shortcut). */
    long timeShift(Expedition expedition, EventDef.Option option, boolean success) {
        if (option.getTime() == 0 || (option.getTime() < 0 && !success)) {
            return 0;
        }
        long shift = Math.round(TimelineGenerator.plannedDuration(expedition.getSegments()) * option.getTime());
        if (shift < 0) {
            // A shortcut can't make the next leg arrive before this one ends.
            int next = expedition.getIndex() + 1;
            long room = next < expedition.getSegments().size()
                    ? expedition.getSegments().get(next).getDurationMillis() * 4 / 5 : 0;
            shift = Math.max(shift, -room);
        }
        return shift;
    }

    /** "Wait it out (+4 min)": the label with what it does to the trip. */
    private String labelOf(Expedition expedition, EventDef.Option option) {
        String label = option.getLabel();
        if (!option.getCost().isEmpty()) {
            List<String> prices = new ArrayList<>();
            for (EventDef.Cost c : option.getCost()) {
                Map<String, String> v = new HashMap<>();
                v.put("amount", String.valueOf(c.getAmount()));
                v.put("resource", content.resource(c.getResource()).getName().toLowerCase(Locale.ROOT));
                prices.add(TextTemplate.format(text.getLootItem(), v));
            }
            Map<String, String> v = new HashMap<>();
            v.put("label", label);
            v.put("cost", String.join(text.getLootSeparator(), prices));
            label = TextTemplate.format(text.getOptionCost(), v);
        }
        if (option.getTime() == 0) {
            return label;
        }
        long shift = Math.round(TimelineGenerator.plannedDuration(expedition.getSegments()) * Math.abs(option.getTime()));
        Map<String, String> vars = new HashMap<>();
        vars.put("label", label);
        vars.put("time", ElapsedFormatter.format(Math.max(60_000L, shift), content.getTerminal().getElapsedUnits()));
        return TextTemplate.format(option.getTime() > 0 ? text.getOptionTimeMore() : text.getOptionTimeLess(), vars);
    }

    /** Situations seen recently go to the back of the queue. Set by the session. */
    private java.util.function.Predicate<String> recentEvents = id -> false;

    public void setRecentEvents(java.util.function.Predicate<String> recent) {
        this.recentEvents = recent;
    }

    private void resolveFind(Expedition expedition, Scout scout, Party party, ZoneDef zone, VoiceDef voice,
                             GameRandom rng, long at, ExpeditionResult out) {
        LootRoll roll = weightedPick(zone.getLoot(), rng);
        int amount = rollAmount(roll, scout, party, expedition.getMission().getLootFactor(), rng);
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
        for (Scout c : party.companions) {
            VoiceDef partnerVoice = content.voice(c.getVoiceId());
            out.line(radio(at, c, say(partnerVoice, c, "arrival", rng, vars(c))));
            rules.markReturned(c, at);
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
            itemVars.put("item", carriedName(itemId).toLowerCase(Locale.ROOT));
            out.line(system(at, TextTemplate.format(content.getTerminal().getBunker().getItemFound(), itemVars), scout));
        }
        expedition.clearLoot();
        expedition.clearItems();
        rules.markReturned(scout, at);
        expedition.setStatus(Expedition.Status.COMPLETED);
        out.markReturned();
    }

    /** What a carried-home entry is called: a key item, gear, a project part or a personal object. */
    public String carriedName(String itemId) {
        if (itemId.startsWith(com.estatica.domain.content.KeyItemDef.CARRIED_PREFIX)) {
            return content.keyItem(itemId.substring(com.estatica.domain.content.KeyItemDef.CARRIED_PREFIX.length())).getName();
        }
        if (itemId.startsWith(GEAR_PREFIX)) {
            return content.gear(itemId.substring(GEAR_PREFIX.length()).split(":")[0]).getName();
        }
        if (itemId.startsWith(PART_PREFIX)) {
            com.estatica.domain.content.RecipeDef.Part part = content.part(itemId.substring(PART_PREFIX.length()));
            return part != null ? part.getName() : itemId;
        }
        return content.item(itemId).getName();
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
        return rollAmount(roll, scout, party, 1.0, rng);
    }

    /** @param lootFactor how picked-over the place is (1 = untouched) */
    private int rollAmount(LootRoll roll, Scout scout, Party party, double lootFactor, GameRandom rng) {
        int base = rng.nextIntInclusive(roll.getMin(), roll.getMax());
        Balance.Pairs pairs = content.getBalance().getPairs();
        double team = party.companions.size() >= 2 ? pairs.getTrioLootMultiplier()
                : party.isPair() ? pairs.getLootMultiplier() : 1.0;
        // A ride with room in the back (the truck) brings more home, whoever drives it.
        double ride = rules.rideLootMultiplier(scout);
        for (Scout c : party.companions) {
            ride = Math.max(ride, rules.rideLootMultiplier(c));
        }
        return Math.max(1, (int) Math.round(base * rules.lootMultiplier(scout) * team * ride * lootFactor));
    }

    /** Now and then the road report mentions the sky (or the dark). */
    private void weatherLine(Expedition expedition, Scout scout, VoiceDef voice, GameRandom rng, long at,
                             ExpeditionResult out) {
        String key = world.nightAt(at) && world.weatherAt(at) == com.estatica.domain.world.WeatherRules.Weather.CLEAR
                ? "NIGHT" : world.weatherAt(at).name();
        List<String> lines = content.getTerminal().getWeather().getLines().get(key);
        if (lines == null || lines.isEmpty() || !rng.chance(content.getBalance().getWeather().getFlavorChance())) {
            return;
        }
        out.line(heard(expedition, rng, at, scout, VoiceRenderer.render(voice, rng.pick(lines), vars(scout))));
    }

    /** Who, if anyone, manages to drag a dying lead home: the strongest one still standing tries. */
    private Scout carriedHome(Scout scout, Party party, GameRandom rng) {
        Balance.Pairs pairs = content.getBalance().getPairs();
        Scout best = null;
        double bestChance = 0;
        for (Scout c : party.companions) {
            if (c.getInjury() == Injury.CRITICAL) {
                continue;
            }
            double chance = pairs.getCarryBase()
                    + rules.effectiveStat(c, StatType.ENDURANCE) * pairs.getCarryPerEndurance()
                    + pairs.relationBonus(party.relationOf(c)) * 2;
            if (best == null || chance > bestChance) {
                best = c;
                bestChance = chance;
            }
        }
        if (best == null) {
            return null;
        }
        // Two to carry: a little easier.
        double help = party.companions.size() >= 2 ? pairs.getSecondCarrierBonus() : 0;
        return rng.nextDouble() < Math.max(0, Math.min(0.95, bestChance + help)) ? best : null;
    }

    /** On the road, the partner talks: friends about each other, rivals at each other. */
    private void pairChatter(Expedition expedition, Scout scout, Party party, GameRandom rng, long at,
                             ExpeditionResult out) {
        Balance.Pairs pairs = content.getBalance().getPairs();
        List<Scout> talkers = new ArrayList<>();
        for (Scout c : party.companions) {
            if (party.relationOf(c) != null) {
                talkers.add(c);
            }
        }
        if (talkers.isEmpty() || !rng.chance(pairs.getChatterChance())) {
            return;
        }
        Scout talker = talkers.size() == 1 ? talkers.get(0) : rng.pick(talkers);
        boolean rivals = "rivals".equals(party.relationOf(talker));
        Map<String, String> v = vars(talker);
        v.put("other", scout.getName());
        VoiceDef partnerVoice = content.voice(talker.getVoiceId());
        out.line(heard(expedition, rng, at, talker,
                say(partnerVoice, talker, rivals ? "aboutRival" : "aboutFriend", rng, v)));
        if (rivals) {
            rules.addStress(scout, pairs.getRivalArgueStress());
            rules.addStress(talker, pairs.getRivalArgueStress());
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
        return chanceFor(expedition, scout, Party.ALONE, option, zone, expedition.getPendingSinceMillis());
    }

    /** Intel, plus on a pair: the partner's help where they're better, and how they get on. */
    private double chanceFor(Expedition expedition, Scout scout, Party party, EventDef.Option option, ZoneDef zone,
                             long at) {
        double bonus = expedition.getMission().hasDangerIntel()
                ? content.getBalance().getRadio().getDangerIntelCheckBonus() : 0;
        // Fog hides you, rain wears you down, the dark is on the sneaky one's side.
        bonus += world.checkBonus(option.getStat(), at);
        // Past the map's edge it only gets worse; and far out, veterans meet harder trouble.
        bonus -= content.getBalance().getDeepRuns().oddsPenalty(expedition.getMission().getTier());
        if (content.getBalance().bandIndex(zone.getBand()) >= content.getBalance().bandIndex("long")) {
            bonus -= retrain.farPenalty(scout);
        }
        if (party.isPair()) {
            // The best hand on the team helps; how each one gets on with the lead counts too.
            Balance.Pairs pairs = content.getBalance().getPairs();
            StatType stat = StatType.fromKey(option.getStat());
            int better = 0;
            for (Scout c : party.companions) {
                better = Math.max(better, rules.effectiveStat(c, stat) - rules.effectiveStat(scout, stat));
                bonus += pairs.relationBonus(party.relationOf(c));
            }
            bonus += Math.min(pairs.getMaxSupport(), better * pairs.getSupportPerPoint());
        }
        return checks.chance(scout, option, zone, bonus);
    }

    /** A transmission from the field, broken up by distance (GDD §4.4). */
    private LogLine heard(Expedition expedition, GameRandom rng, long at, Scout scout, String line) {
        double noise = Math.min(1.0, expedition.getMission().getNoise() + world.noiseAt(at));
        return radio(at, scout, noise > 0 ? SignalDegrader.degrade(line, noise, rng) : line);
    }

    /** A phrase in the scout voice, warmer when the bond is high. */
    private String say(VoiceDef voice, Scout scout, String key, GameRandom rng, Map<String, String> vars) {
        return VoiceRenderer.phrase(voice, key, rng, vars, rules.isClose(scout));
    }

    private static Map<String, String> vars(Scout scout) {
        Map<String, String> vars = new HashMap<>();
        vars.put("name", scout.getName());
        if (scout.getLife().hasFlag(com.estatica.domain.scouts.RomanceRules.FLAG)) {
            vars.put(VoiceRenderer.LOVE, "1"); // their "@love" lines
        }
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
