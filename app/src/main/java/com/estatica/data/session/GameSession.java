package com.estatica.data.session;

import androidx.annotation.WorkerThread;

import com.estatica.core.GameClock;
import com.estatica.core.GameRandom;
import com.estatica.core.Ids;
import com.estatica.core.TextTemplate;
import com.estatica.data.content.ContentRepository;
import com.estatica.data.db.EstaticaDatabase;
import com.estatica.data.repo.BaseRepository;
import com.estatica.data.repo.BunkerRepository;
import com.estatica.data.repo.ExpeditionRepository;
import com.estatica.data.repo.GameStateRepository;
import com.estatica.data.repo.LogRepository;
import com.estatica.data.repo.MemoryRepository;
import com.estatica.data.repo.RadioRepository;
import com.estatica.data.repo.ResourceRepository;
import com.estatica.data.repo.ScoutRepository;
import com.estatica.domain.bunker.BunkerEvents;
import com.estatica.domain.bunker.BunkerRules;
import com.estatica.domain.bunker.CraftJob;
import com.estatica.domain.bunker.PowerGrid;
import com.estatica.domain.bunker.RaidRules;
import com.estatica.domain.bunker.ItemInstance;
import com.estatica.domain.content.Balance;
import com.estatica.domain.content.BandDef;
import com.estatica.domain.content.ModuleDef;
import com.estatica.domain.content.SignalsDef;
import com.estatica.domain.content.StoryDef;
import com.estatica.domain.content.DialogueDef;
import com.estatica.domain.content.EnergyDef;
import com.estatica.domain.content.GearDef;
import com.estatica.domain.content.RecipeDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.ItemDef;
import com.estatica.domain.content.ResourceDef;
import com.estatica.domain.content.ScoutTemplate;
import com.estatica.domain.content.TaskDef;
import com.estatica.domain.content.TerminalText;
import com.estatica.domain.content.VoiceDef;
import com.estatica.domain.content.ZoneDef;
import com.estatica.domain.dialogue.Conversation;
import com.estatica.domain.dialogue.DialogueEngine;
import com.estatica.domain.expedition.Expedition;
import com.estatica.domain.expedition.ExpeditionEngine;
import com.estatica.domain.expedition.ExpeditionResult;
import com.estatica.domain.expedition.Mission;
import com.estatica.domain.radio.Broadcasts;
import com.estatica.domain.radio.RadioRules;
import com.estatica.domain.radio.Relay;
import com.estatica.domain.radio.SignalDegrader;
import com.estatica.domain.radio.SignalField;
import com.estatica.domain.radio.Transmission;
import com.estatica.domain.time.ElapsedFormatter;
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;
import com.estatica.domain.scouts.Affinities;
import com.estatica.domain.scouts.GiftRules;
import com.estatica.domain.scouts.GriefRules;
import com.estatica.domain.scouts.Injury;
import com.estatica.domain.scouts.Memory;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutFactory;
import com.estatica.domain.scouts.ScoutRules;
import com.estatica.domain.scouts.ScoutStatus;
import com.estatica.domain.scouts.ScoutTrait;
import com.estatica.domain.scouts.Stat;
import com.estatica.domain.scouts.StatType;
import com.estatica.domain.scouts.VoiceRenderer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The running game: loads the save once, applies catch-up and player actions through the
 * domain rules, and persists each step in a single transaction.
 *
 * <p>Threading: every method must run on the game executor (single thread). State held
 * here is never touched from any other thread.
 */
@Singleton
public final class GameSession {

    private static final long HOUR = 3_600_000L;

    private final EstaticaDatabase db;
    private final ContentRepository contentRepository;
    private final GameStateRepository gameStateRepository;
    private final ScoutRepository scoutRepository;
    private final ExpeditionRepository expeditionRepository;
    private final MemoryRepository memoryRepository;
    private final ResourceRepository resourceRepository;
    private final BunkerRepository bunkerRepository;
    private final RadioRepository radioRepository;
    private final BaseRepository baseRepository;
    private final LogRepository logRepository;
    private final GameClock clock;

    private boolean started;
    private GameContent content;
    private ExpeditionEngine engine;
    private ScoutRules rules;
    private BunkerRules bunker;
    private DialogueEngine dialogues;
    private GiftRules gifts;
    private GriefRules grief;
    private com.estatica.domain.scouts.BarkRules barks;
    private com.estatica.domain.scouts.PersonaGenerator personas;
    private com.estatica.domain.bunker.FoodRules food;
    private com.estatica.domain.bunker.GearRules gearRules;
    private com.estatica.domain.map.RegionMap regionMap;
    private com.estatica.domain.world.WeatherRules weather;
    private com.estatica.domain.scouts.VignetteRules vignettes;
    /** Every piece of equipment, by id (stock, equipped, lost in zones). */
    private final Map<String, com.estatica.domain.bunker.GearPiece> gear = new HashMap<>();
    private com.estatica.domain.state.GameState state;
    private GameRandom random;
    /** The save's seed, for dice that must roll the same every time (signal previews). */
    private long rngSeed;
    private long lastSeenAtMillis;
    private boolean newGame;
    private Affinities affinities;
    private final Map<String, Scout> scouts = new LinkedHashMap<>();
    private final Map<String, Expedition> active = new LinkedHashMap<>();
    private final Map<String, Integer> resources = new HashMap<>();
    private final Map<String, ItemInstance> items = new LinkedHashMap<>();
    private final Map<String, Conversation> conversations = new LinkedHashMap<>();

    // Radio (Phase 3)
    private RadioRules radio;
    private SignalField field;
    private final Set<String> gameFlags = new java.util.LinkedHashSet<>();
    private final Set<String> knownZones = new java.util.LinkedHashSet<>();
    private final Set<String> caughtSignals = new java.util.HashSet<>();
    private final Set<String> receivedTransmissions = new java.util.HashSet<>();
    private final Map<String, Transmission> transmissions = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> intel = new HashMap<>();
    private final Map<String, Relay> relays = new TreeMap<>();
    private final Map<String, String> rescueTargets = new TreeMap<>();
    /** Who each waiting survivor is (generated people). */
    private final Map<String, ScoutTemplate> rescuePersonas = new HashMap<>();
    private final Map<String, String> lore = new LinkedHashMap<>();
    private final Map<String, Integer> moduleLevels = new HashMap<>();

    /** Global flag: the Morse reference table was found. */
    public static final String FLAG_MORSE_TABLE = "morse_table";

    // Bunker (Phase 4)
    private static final String KEY_RESERVE = "reserve";
    private static final String KEY_TICK = "base_tick";
    private BunkerEvents events;
    private RaidRules raids;
    private final Map<String, Integer> power = new HashMap<>();
    private double reserve;
    /** Last time energy, workshop, blackouts and raids were brought up to date. */
    private long baseTick;
    private final List<CraftJob> craftJobs = new ArrayList<>();
    /** Last power situation reported, to only log changes. */
    private String lastPowerReport = "";
    /** Numeric bunker values (goal counters and done marks live here too). */
    private final Map<String, Double> baseValues = new HashMap<>();

    @Inject
    GameSession(EstaticaDatabase db, ContentRepository contentRepository,
                GameStateRepository gameStateRepository, ScoutRepository scoutRepository,
                ExpeditionRepository expeditionRepository, MemoryRepository memoryRepository,
                ResourceRepository resourceRepository, BunkerRepository bunkerRepository,
                RadioRepository radioRepository, BaseRepository baseRepository,
                LogRepository logRepository, GameClock clock) {
        this.radioRepository = radioRepository;
        this.baseRepository = baseRepository;
        this.db = db;
        this.contentRepository = contentRepository;
        this.gameStateRepository = gameStateRepository;
        this.scoutRepository = scoutRepository;
        this.expeditionRepository = expeditionRepository;
        this.memoryRepository = memoryRepository;
        this.resourceRepository = resourceRepository;
        this.bunkerRepository = bunkerRepository;
        this.logRepository = logRepository;
        this.clock = clock;
    }

    /** Loads the save (first call only). Existing saves without scouts get the starters. */
    @WorkerThread
    public void start() {
        if (started) {
            return;
        }
        content = contentRepository.get();
        engine = new ExpeditionEngine(content);
        rules = engine.rules();
        bunker = new BunkerRules(content, rules);
        dialogues = new DialogueEngine(content, rules, new DialogueEngine.Relations() {
            @Override
            public Scout find(Scout scout, String kind) {
                for (Scout other : scouts.values()) {
                    if (other == scout || !other.isAlive()) {
                        continue;
                    }
                    String relation = bunker.relationship(affinities, scout, other);
                    boolean match = "friend".equals(kind)
                            ? "friends".equals(relation) || "inseparable".equals(relation)
                            : "inseparable".equals(kind) ? "inseparable".equals(relation)
                            : "rivals".equals(relation);
                    if (match) {
                        return other;
                    }
                }
                return null;
            }

            @Override
            public void adjust(Scout a, Scout b, double delta) {
                affinities.add(a.getId(), b.getId(), delta);
            }
        });
        barks = new com.estatica.domain.scouts.BarkRules(content, rules);
        personas = new com.estatica.domain.scouts.PersonaGenerator(content);
        food = new com.estatica.domain.bunker.FoodRules(content.getBalance().getFood());
        gearRules = new com.estatica.domain.bunker.GearRules(content);
        vignettes = new com.estatica.domain.scouts.VignetteRules(content, rules);
        gifts = new GiftRules(content, rules);
        grief = new GriefRules(content, rules);
        state = gameStateRepository.loadOrCreate();
        random = GameRandom.fromState(state.getRngState());
        com.estatica.domain.scouts.VoiceRenderer.setOperatorName(state.getOperatorName());
        newGame = state.isNewGame();
        lastSeenAtMillis = state.getLastSeenAtMillis();

        List<Scout> loaded = scoutRepository.loadAll();
        if (loaded.isEmpty()) {
            long now = clock.nowMillis();
            db.runInTransaction(() -> {
                for (ScoutTemplate template : startingCrew()) {
                    int trust = content.getBalance().getScouts().getInitialTrust();
                    Scout scout = ScoutFactory.fromTemplate(template, content.getBalance().getScouts(), now, trust);
                    scoutRepository.insertNew(scout);
                    loaded.add(scout);
                }
            });
        }
        for (Scout scout : loaded) {
            scouts.put(scout.getId(), scout);
            ScoutTemplate template = scout.getPersona() == null ? content.starter(scout.getTemplateId()) : null;
            if (template != null) {
                // Content updates may give a template new traits: older saves catch up.
                for (String trait : template.getTraits()) {
                    rules.addTrait(scout, trait);
                }
            }
        }
        for (Expedition expedition : expeditionRepository.loadActive()) {
            active.put(expedition.getId(), expedition);
        }
        resources.putAll(resourceRepository.all());
        for (ItemInstance item : bunkerRepository.items()) {
            items.put(item.getId(), item);
        }
        loadGear();
        for (Conversation c : bunkerRepository.pendingConversations()) {
            // Content updates can remove a dialogue (Tano's, in 0.6.3): drop it instead of crashing.
            if (content.getDialogues().containsKey(c.getDialogueId())) {
                conversations.put(c.getId(), c);
            }
        }
        affinities = bunkerRepository.affinities(content.getBalance().getAffinity().getMax());

        radio = new RadioRules(content);
        field = new SignalField(content.getBalance(), state.getRngSeed());
        rngSeed = state.getRngSeed();
        regionMap = new com.estatica.domain.map.RegionMap(content, state.getRngSeed());
        weather = new com.estatica.domain.world.WeatherRules(content.getBalance().getWeather(), state.getRngSeed());
        engine.setKeyOwnership(this::hasKey);
        engine.setRecentEvents(id -> clock.nowMillis() - value(EVENT_SEEN + id)
                < content.getBalance().getExpedition().getEventRecentHours() * HOUR);
        engine.setWorld(new com.estatica.domain.world.World() {
            @Override
            public com.estatica.domain.world.WeatherRules.Weather weatherAt(long millis) {
                return weather.at(millis);
            }

            @Override
            public boolean nightAt(long millis) {
                return content.getBalance().getEnergy().isNight(clock.localHour(millis));
            }

            @Override
            public double checkBonus(String stat, long millis) {
                return weather.checkBonus(weatherAt(millis), nightAt(millis), stat);
            }

            @Override
            public double noiseAt(long millis) {
                return weather.noise(weatherAt(millis));
            }
        });
        gameFlags.addAll(radioRepository.flags());
        // Saves from before key items: the Morse table they earned becomes the radio manual.
        if (gameFlags.contains(FLAG_MORSE_TABLE) && gameFlags.add("key:radio_manual")) {
            radioRepository.addFlag("key:radio_manual", clock.nowMillis());
        }
        knownZones.addAll(radioRepository.knownZones());
        caughtSignals.addAll(radioRepository.caughtSignals());
        receivedTransmissions.addAll(radioRepository.receivedTransmissionSources());
        for (Transmission t : radioRepository.pendingTransmissions()) {
            transmissions.put(t.getId(), t);
        }
        intel.putAll(radioRepository.intel());
        relays.putAll(radioRepository.relays());
        rescueTargets.putAll(radioRepository.rescueTargets());
        rescuePersonas.putAll(radioRepository.rescuePersonas());
        lore.putAll(radioRepository.lore());
        moduleLevels.putAll(radioRepository.moduleLevels());
        long now = clock.nowMillis();
        db.runInTransaction(() -> {
            // New games: module levels. No zone starts known: everything is found by scouting.
            for (ModuleDef m : content.getBalance().getModules()) {
                if (!moduleLevels.containsKey(m.getId())) {
                    moduleLevels.put(m.getId(), m.getStartLevel());
                    radioRepository.saveModule(m.getId(), m.getStartLevel());
                }
            }
        });
        EnergyDef energy = content.getBalance().getEnergy();
        events = new BunkerEvents(energy, state.getRngSeed());
        raids = new RaidRules(content, rules);
        power.putAll(baseRepository.power());
        Map<String, Double> values = baseRepository.values();
        baseValues.putAll(values);
        List<String> recentDone = new ArrayList<>();
        for (Map.Entry<String, Double> v : baseValues.entrySet()) {
            if (v.getKey().startsWith(LOG_DONE) && clock.nowMillis() - v.getValue() < LOG_CHIP_MILLIS) {
                recentDone.add(v.getKey().substring(LOG_DONE.length()));
            }
        }
        doneTeams.putAll(expeditionRepository.teams(recentDone));
        craftJobs.addAll(baseRepository.craftJobs());
        db.runInTransaction(() -> {
            for (EnergyDef.Consumer c : energy.getConsumers()) {
                if (!power.containsKey(c.getId())) {
                    power.put(c.getId(), c.getStart());
                    baseRepository.savePower(c.getId(), c.getStart());
                }
            }
            if (!values.containsKey(KEY_TICK)) {
                // New games and saves from before Phase 4: no retroactive raids or blackouts.
                baseRepository.saveValue(KEY_TICK, now);
                baseRepository.saveValue(KEY_RESERVE, reserveCapacity());
            }
        });
        baseTick = values.containsKey(KEY_TICK) ? values.get(KEY_TICK).longValue() : now;
        reserve = values.containsKey(KEY_RESERVE) ? values.get(KEY_RESERVE)
                : reserveCapacity();
        gameStateRepository.saveContentVersion(content.getBalance().getContentVersion());
        started = true;
    }

    public GameContent content() {
        return content;
    }

    public GameRandom random() {
        return random;
    }

    public String operatorId() {
        return state.getOperatorId();
    }

    /** Null until the operator says what to call them. */
    public String operatorName() {
        return state.getOperatorName();
    }

    /** Sets what the crew calls the operator (trimmed, at most 16 characters). */
    @WorkerThread
    public String setOperatorName(String name) {
        String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (clean.length() > 16) {
            clean = clean.substring(0, 16).trim();
        }
        if (clean.isEmpty()) {
            return state.getOperatorName();
        }
        state.setOperatorName(clean);
        gameStateRepository.saveOperatorName(clean);
        com.estatica.domain.scouts.VoiceRenderer.setOperatorName(clean);
        return clean;
    }

    public long createdAtMillis() {
        return state.getCreatedAtMillis();
    }

    /** True once, in the first session of a new save (shows the boot sequence). */
    public boolean takeNewGame() {
        boolean value = newGame;
        newGame = false;
        return value;
    }

    /** "Mara (in the bunker), Tano (out)…" for terminal texts. */
    public String describeScouts() {
        List<String> parts = new ArrayList<>();
        for (Scout s : scouts.values()) {
            parts.add(s.getName() + " (" + content.getTerminal().getScoutStatus().get(s.getStatus().name()) + ")");
        }
        return String.join(", ", parts);
    }

    /** "3 chatarra, 0 componentes…" for terminal texts. */
    public String describeResources() {
        List<String> parts = new ArrayList<>();
        for (ResourceDef r : content.getResources().values()) {
            parts.add(resources.getOrDefault(r.getId(), 0) + " " + r.getName().toLowerCase(Locale.ROOT));
        }
        return String.join(", ", parts);
    }

    public long lastSeenAtMillis() {
        return lastSeenAtMillis;
    }

    @WorkerThread
    public void markSeen() {
        lastSeenAtMillis = clock.nowMillis();
        gameStateRepository.saveRngState(random.getState());
        gameStateRepository.touchLastSeen();
    }

    /** Persists free-standing lines (boot, commands) and returns them with their seq. */
    @WorkerThread
    public List<LogLine> log(List<LogLine> drafts) {
        List<LogLine> saved = new ArrayList<>();
        db.runInTransaction(() -> saved.addAll(logRepository.insert(drafts)));
        return saved;
    }

    // ---- Catch-up ----

    /** What one step produced, persisted together. */
    private final class Step {
        final long now = clock.nowMillis();
        final List<LogLine> lines = new ArrayList<>();
        final List<Memory> memories = new ArrayList<>();
        final Map<String, Integer> resourceDelta = new HashMap<>();
        final List<Expedition> expeditions = new ArrayList<>();
        final List<Expedition> newExpeditions = new ArrayList<>();
        final List<Conversation> conversationsChanged = new ArrayList<>();
        final List<Scout> newScouts = new ArrayList<>();
        final List<Runnable> radioWrites = new ArrayList<>();
        /** Already applied in memory by pay(); only persisted on commit. */
        final Map<String, Integer> paid = new HashMap<>();

        /** Lines that belong to an expedition or a chat, not to the system log. */
        void into(List<LogLine> source, String thread) {
            for (LogLine line : source) {
                lines.add(line.withThread(thread));
            }
        }

        void system(String template, Map<String, String> vars, Scout scout) {
            lines.add(LogLine.draft(now, LogChannel.SYSTEM, null, TextTemplate.format(template, vars),
                    scout != null ? scout.getId() : null));
        }

        List<LogLine> commit() {
            // New recruits must exist before their rows are updated with everyone else.
            for (Scout scout : newScouts) {
                scouts.remove(scout.getId());
            }
            // Several sources may have produced lines: interleave them by time.
            lines.sort(Comparator.comparingLong(LogLine::getTimestampMillis));
            List<LogLine> saved = new ArrayList<>();
            db.runInTransaction(() -> {
                saved.addAll(logRepository.insert(lines));
                for (Scout scout : scouts.values()) {
                    scoutRepository.save(scout);
                }
                for (Expedition expedition : newExpeditions) {
                    expeditionRepository.insertNew(expedition);
                }
                for (Expedition expedition : expeditions) {
                    expeditionRepository.save(expedition);
                }
                memoryRepository.insert(memories);
                resourceRepository.add(resourceDelta);
                resourceRepository.add(paid);
                bunkerRepository.saveItems(items.values());
                bunkerRepository.saveGear(gear.values());
                bunkerRepository.saveAffinities(affinities);
                for (Conversation c : conversationsChanged) {
                    bunkerRepository.save(c);
                }
                for (Scout scout : newScouts) {
                    scoutRepository.insertNew(scout);
                }
                for (Runnable write : radioWrites) {
                    write.run();
                }
                for (Relay relay : relays.values()) {
                    radioRepository.save(relay);
                }
                gameStateRepository.saveRngState(random.getState());
            });
            for (Scout scout : newScouts) {
                scouts.put(scout.getId(), scout);
            }
            resourceDelta.forEach((k, v) -> resources.merge(k, v, Integer::sum));
            for (Iterator<Expedition> it = active.values().iterator(); it.hasNext(); ) {
                if (!it.next().isActive()) {
                    it.remove();
                }
            }
            conversations.values().removeIf(c -> !c.isPending());
            return saved;
        }
    }

    /** Resolves everything due by now: expeditions, life in the bunker, conversations. */
    @WorkerThread
    public List<LogLine> catchUp() {
        Step step = new Step();
        advance(step);
        return step.commit();
    }

    private void advance(Step step) {
        long now = step.now;
        for (Expedition expedition : new ArrayList<>(active.values())) {
            Scout scout = scouts.get(expedition.getScoutId());
            ExpeditionEngine.Party party = party(expedition);
            ExpeditionResult result = engine.advance(expedition, scout, party,
                    memoryRepository.forScout(scout.getId()), deathContext(scout), now);
            step.expeditions.add(expedition);
            step.into(result.getLines(), LogLine.expeditionThread(expedition.getId()));
            summarize(expedition, scout, party, result, step);
            step.memories.addAll(result.getMemories());
            result.getDelivered().forEach((k, v) -> step.resourceDelta.merge(k, v, Integer::sum));
            for (String itemId : result.getDeliveredItems()) {
                if (itemId.startsWith(com.estatica.domain.content.KeyItemDef.CARRIED_PREFIX)) {
                    grantKey(itemId.substring(com.estatica.domain.content.KeyItemDef.CARRIED_PREFIX.length()), scout, step);
                    continue;
                }
                ItemInstance found = ItemInstance.found(itemId, now);
                items.put(found.getId(), found);
            }
            for (String fragment : result.getPlotFragments()) {
                foundPlot(fragment, step);
            }
            if (result.isReturned()) {
                recoverLostItems(scout, expedition.getZoneId(), step);
                if (!content.zone(expedition.getZoneId()).isWilds()) {
                    pickOver(expedition.getZoneId(), step);
                }
                gearAfterTrip(scout, expedition.getZoneId(), step);
                if (party.partner != null) {
                    gearAfterTrip(party.partner, expedition.getZoneId(), step);
                    // Coming back together builds something, even between rivals.
                    affinities.add(scout.getId(), party.partner.getId(),
                            content.getBalance().getPairs().getAffinityPerTrip());
                }
                finishMission(expedition, scout, result.isMissionCompleted(), step);
                if (result.isMissionCompleted()) {
                    count("expeditions", step);
                }
            }
            if (result.isScoutDied()) {
                afterDeath(scout, expedition.getZoneId(), step);
                if (expedition.getMission().getType() == Mission.Type.RESCUE) {
                    rescueFailed(expedition.getMission().getTarget(), step);
                }
            }
        }

        Balance.Radio radioBalance = content.getBalance().getRadio();
        for (Relay relay : relays.values()) {
            if (relay.decay(now, radioBalance.getRelayDecayPerDay(), radioBalance.getRelayMinCondition())) {
                Map<String, String> vars = new HashMap<>();
                vars.put("zone", content.zone(relay.getZoneId()).getName());
                step.system(content.getTerminal().getRadio().getRelayFailing(), vars, null);
            }
        }

        PowerGrid.State grid = baseAdvance(step);
        boolean hungry = feed(step);
        BunkerRules.Conditions conditions = new BunkerRules.Conditions(
                PowerGrid.infirmaryHeal(grid.units("infirmary"), moduleLevel("infirmary"))
                        * (hungry ? food.hungerHealMultiplier() : 1.0),
                PowerGrid.infirmaryRelief(grid.units("infirmary")),
                grid.filtersShort ? content.getBalance().getEnergy().getBadAirStressPerHour() : 0);
        BunkerRules.Result life = bunker.advance(scouts.values(), affinities, resources, now, conditions);
        step.lines.addAll(life.lines);
        life.resourceDelta.forEach((k, v) -> step.resourceDelta.merge(k, v, Integer::sum));
        for (int i = 0; i < life.passiveSignals; i++) {
            passiveCatch(step);
        }

        expireConversations(step);
        maybeStartConversation(step);
        maybeBark(step);
        maybeVignette(step);
        maybeOmen(step);
        catchBroadcasts(step);
        eagerTreatments(step);
    }

    /** Someone on the infirmary task offering to treat someone else, right now. */
    private static final class Treatment {
        final Scout healer;
        final Scout patient;
        final int cost;

        Treatment(Scout healer, Scout patient, int cost) {
            this.healer = healer;
            this.patient = patient;
            this.cost = cost;
        }

        String id() {
            return TREAT + healer.getId() + "|" + patient.getId();
        }
    }

    private static final String TREAT = "treat:";

    /**
     * One offer per injured person at home: a healer on the infirmary task (not the injured
     * one), not turned down lately. Offers exist only while that's true; nothing is stored.
     */
    private List<Treatment> treatments(long now) {
        Balance.Treatment config = content.getBalance().getTreatment();
        List<Treatment> out = new ArrayList<>();
        for (Scout patient : scouts.values()) {
            if (!patient.isAlive() || patient.getStatus() != ScoutStatus.IDLE || patient.getInjury() == Injury.NONE
                    || now - value(TREAT + "no:" + patient.getId()) < config.getDeclineCooldownHours() * HOUR) {
                continue;
            }
            for (Scout healer : scouts.values()) {
                String task = healer.getLife().getTaskId();
                if (healer != patient && healer.isAlive() && healer.getStatus() == ScoutStatus.IDLE
                        && task != null && content.task(task).isTreats()) {
                    out.add(new Treatment(healer, patient, patient.getInjury().ordinal() * config.getMedicinePerLevel()));
                    break;
                }
            }
        }
        return out;
    }

    private boolean isEager(Scout healer) {
        for (ScoutTrait t : healer.getTraits()) {
            if (t.isVisible() && content.getBalance().getTreatment().getEagerTraits().contains(t.getTraitId())) {
                return true;
            }
        }
        return false;
    }

    /** Some healers don't ask (Gentle, Reckless, Hothead): if there's medicine, they just do it. */
    private void eagerTreatments(Step step) {
        for (Treatment t : treatments(step.now)) {
            if (isEager(t.healer) && resources.getOrDefault("medicine", 0)
                    + step.resourceDelta.getOrDefault("medicine", 0) >= t.cost) {
                treatNow(t, content.getTerminal().getBunker().getTreatEager(), step);
            }
        }
    }

    private void treatNow(Treatment t, String template, Step step) {
        Map<String, String> vars = treatVars(t);
        step.resourceDelta.merge("medicine", -t.cost, Integer::sum);
        rules.treat(t.patient);
        step.system(template, vars, t.patient);
    }

    private Map<String, String> treatVars(Treatment t) {
        Map<String, String> vars = new HashMap<>();
        vars.put("healer", t.healer.getName());
        vars.put("name", t.patient.getName());
        vars.put("injury", content.getTerminal().getInjuries().get(t.patient.getInjury().name()));
        vars.put("cost", String.valueOf(t.cost));
        return vars;
    }

    /** The operator answers a treatment offer: 0 do it, 1 not now. */
    @WorkerThread
    public List<LogLine> treat(String offerId, int choice) {
        Step step = new Step();
        advance(step);
        for (Treatment t : treatments(step.now)) {
            if (!t.id().equals(offerId)) {
                continue;
            }
            if (choice != 0) {
                setValue(TREAT + "no:" + t.patient.getId(), step.now, step);
            } else if (resources.getOrDefault("medicine", 0) + step.resourceDelta.getOrDefault("medicine", 0) < t.cost) {
                step.system(content.getTerminal().getBunker().getTreatNoMedicine(), treatVars(t), t.patient);
            } else {
                treatNow(t, content.getTerminal().getBunker().getTreatDone(), step);
            }
        }
        return step.commit();
    }

    /**
     * Every few hours, maybe, something wrong happens in the bunker. Worse ones unlock as the
     * Voice's story moves forward. Only stress, never harm: it's there to make the dark longer.
     */
    private void maybeOmen(Step step) {
        Balance.Omens config = content.getBalance().getOmens();
        double last = value("last_omen");
        if (last <= 0) {
            setValue("last_omen", step.now, step);
            return;
        }
        if (step.now - last < config.getIntervalHours() * HOUR) {
            return;
        }
        setValue("last_omen", step.now, step);
        if (!random.chance(config.getChance())) {
            return;
        }
        int plot = 0;
        for (String flag : gameFlags) {
            plot += flag.startsWith("plot:") ? 1 : 0;
        }
        boolean night = content.getBalance().getEnergy().isNight(clock.localHour(step.now));
        List<Scout> home = new ArrayList<>();
        for (Scout s : scouts.values()) {
            if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE) {
                home.add(s);
            }
        }
        List<StoryDef.Omen> pool = new ArrayList<>();
        for (StoryDef.Omen o : content.getStory().getOmens()) {
            boolean fits = (!o.isNight() || night) && plot >= o.getMinPlot()
                    && (!o.isNeedsCrew() || !home.isEmpty())
                    && step.now - value("omen:" + o.getId()) >= config.getCooldownHours() * HOUR;
            for (int w = 0; fits && w < o.getWeight(); w++) {
                pool.add(o);
            }
        }
        if (pool.isEmpty()) {
            return;
        }
        StoryDef.Omen omen = random.pick(pool);
        setValue("omen:" + omen.getId(), step.now, step);
        Scout witness = home.isEmpty() ? null : random.pick(home);
        Map<String, String> vars = new HashMap<>();
        vars.put("name", witness != null ? witness.getName() : "");
        for (int i = 0; i < omen.getLines().size(); i++) {
            LogChannel channel = omen.isAlert() && i == 0 ? LogChannel.ALERT : LogChannel.SYSTEM;
            step.lines.add(LogLine.draft(step.now, channel, null,
                    TextTemplate.format(omen.getLines().get(i), vars)));
        }
        for (Scout s : home) {
            rules.addStress(s, omen.getStress());
        }
        if (witness != null && !omen.getReaction().isEmpty()) {
            com.estatica.domain.content.VoiceDef voice = content.voice(witness.getVoiceId());
            List<LogLine> said = new ArrayList<>();
            for (String line : omen.getReaction()) {
                said.add(LogLine.draft(step.now, LogChannel.RADIO, witness.getName(),
                        com.estatica.domain.scouts.VoiceRenderer.render(voice, line, vars), witness.getId()));
            }
            step.into(said, BUNKER_CHAT);
        }
    }

    /** Thread for moments the whole bunker shares. */
    public static final String BUNKER_CHAT = LogLine.chatThread("bunker");

    /** Now and then, two people at home have a moment (shared meal, an argument, a song). */
    private void maybeVignette(Step step) {
        Balance.Vignettes config = content.getBalance().getVignettes();
        double last = value("last_vignette");
        if (last <= 0) {
            setValue("last_vignette", step.now, step);
            return;
        }
        if (step.now - last < config.getIntervalHours() * HOUR) {
            return;
        }
        setValue("last_vignette", step.now, step);
        if (!random.chance(config.getChance())) {
            return;
        }
        Set<String> recent = new java.util.HashSet<>();
        for (com.estatica.domain.content.VignetteDef v : content.getVignettes()) {
            if (step.now - value("vig:" + v.getId()) < config.getCooldownHours() * HOUR) {
                recent.add(v.getId());
            }
        }
        boolean night = content.getBalance().getEnergy().isNight(clock.localHour(step.now));
        com.estatica.domain.scouts.VignetteRules.Moment moment =
                vignettes.pick(scouts.values(), affinities, night, recent, random, step.now);
        if (moment != null) {
            setValue("vig:" + moment.vignette.getId(), step.now, step);
            step.into(moment.lines, BUNKER_CHAT);
        }
    }

    private static final String KEY_FOOD_AT = "food_at";
    private static final String KEY_FOOD_CARRY = "food_carry";
    private static final String KEY_HUNGRY = "hungry";
    private static final String KEY_FOOD_WARNED = "food_warned";

    /** Rations nibbled so far, counting the time since the last meal was written. */
    private double foodCarryNow(long now) {
        double last = value(KEY_FOOD_AT);
        double hours = last <= 0 ? 0 : (now - last) / (double) HOUR;
        return value(KEY_FOOD_CARRY) + food.perHour(mouths(), kitchenSaving()) * hours;
    }

    private int mouths() {
        return aliveScouts();
    }

    /** What the cooks at home save, before the cap. */
    private double kitchenSaving() {
        double saving = moduleBonus("pantry");
        for (Scout s : scouts.values()) {
            String task = s.getLife().getTaskId();
            if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE && task != null) {
                saving += content.task(task).getFoodSaving();
            }
        }
        return saving;
    }

    private int rationsNow(Step step) {
        return resources.getOrDefault("rations", 0) + step.resourceDelta.getOrDefault("rations", 0);
    }

    /**
     * The bunker eats since the last step. Everyone alive eats (those out carry their share);
     * when the stores run out, whoever is home gets hungry.
     *
     * @return true if the crew is hungry now
     */
    private boolean feed(Step step) {
        double last = value(KEY_FOOD_AT);
        if (last <= 0) {
            setValue(KEY_FOOD_AT, step.now, step);
            return false;
        }
        double hours = (step.now - last) / (double) HOUR;
        int rations = rationsNow(step);
        com.estatica.domain.bunker.FoodRules.Meal meal =
                food.eat(mouths(), kitchenSaving(), rations, value(KEY_FOOD_CARRY), hours);
        boolean wasHungry = value(KEY_HUNGRY) > 0;
        if (meal.eaten == 0 && meal.hungryHours <= 0 && !wasHungry) {
            // Nothing to write yet: the clock keeps running from the last meal.
            return false;
        }
        setValue(KEY_FOOD_AT, step.now, step);
        setValue(KEY_FOOD_CARRY, meal.carry, step);
        if (meal.eaten > 0) {
            step.resourceDelta.merge("rations", -meal.eaten, Integer::sum);
        }
        TerminalText.BunkerText text = content.getTerminal().getBunker();
        boolean hungry = meal.hungryHours > 0;
        if (hungry) {
            int stress = food.hungerStress(meal.hungryHours);
            for (Scout s : scouts.values()) {
                if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE && stress > 0) {
                    rules.addStress(s, stress);
                }
            }
            if (!wasHungry) {
                step.system(text.getFoodOut(), Map.of(), null);
            }
        } else if (wasHungry && rationsNow(step) > 0) {
            step.system(text.getFoodBack(), Map.of(), null);
        }
        setValue(KEY_HUNGRY, hungry ? 1 : 0, step);

        double days = food.daysLeft(mouths(), kitchenSaving(), rationsNow(step), meal.carry);
        boolean warned = value(KEY_FOOD_WARNED) > 0;
        if (!hungry && food.isLow(days) && !warned) {
            Map<String, String> vars = new HashMap<>();
            vars.put("days", String.format(Locale.ENGLISH, "%.1f", days));
            step.system(text.getFoodLow(), vars, null);
            setValue(KEY_FOOD_WARNED, 1, step);
        } else if (warned && !food.isLow(days / 2)) {
            setValue(KEY_FOOD_WARNED, 0, step);
        }
        return hungry;
    }

    /** The few lines that matter go to the system log: orders needed, back with what, dead. */
    private void summarize(Expedition expedition, Scout scout, ExpeditionEngine.Party party,
                           ExpeditionResult result, Step step) {
        TerminalText.ExpeditionText text = content.getTerminal().getExpedition();
        Map<String, String> vars = new HashMap<>();
        vars.put("who", scout.getName() + (party.partner != null ? " + " + party.partner.getName() : ""));
        vars.put("zone", content.zone(expedition.getZoneId()).getName());
        String thread = LogLine.summaryThread(expedition.getId());
        if (result.isSituationArrived() && expedition.isAwaitingDecision()) {
            step.lines.add(LogLine.draft(step.now, LogChannel.ALERT, null,
                    TextTemplate.format(text.getSummaryOrders(), vars), scout.getId()).withThread(thread));
        }
        if (result.isScoutDied()) {
            Map<String, String> died = new HashMap<>(vars);
            died.put("name", scout.getName());
            step.lines.add(LogLine.draft(step.now, LogChannel.SYSTEM, null,
                    TextTemplate.format(text.getSummaryDied(), died), scout.getId()).withThread(thread));
            markLogDone(expedition, step);
        }
        if (result.isReturned()) {
            StringBuilder line = new StringBuilder();
            Map<String, Integer> loot = result.getDelivered();
            if (loot.isEmpty()) {
                line.append(TextTemplate.format(text.getSummaryReturnedEmpty(), vars));
            } else {
                vars.put("loot", engine.describeLoot(loot));
                line.append(TextTemplate.format(text.getSummaryReturned(), vars));
            }
            for (Scout s : party.partner != null ? List.of(scout, party.partner) : List.of(scout)) {
                if (s.getInjury() != com.estatica.domain.scouts.Injury.NONE) {
                    Map<String, String> hurt = new HashMap<>();
                    hurt.put("name", s.getName());
                    hurt.put("injury", content.getTerminal().getInjuries().get(s.getInjury().name()));
                    line.append(' ').append(TextTemplate.format(text.getSummaryHurt(), hurt));
                }
            }
            step.lines.add(LogLine.draft(step.now, LogChannel.SYSTEM, null, line.toString(), scout.getId())
                    .withThread(thread));
            markLogDone(expedition, step);
        }
    }

    /** GDD §5.5: what a dying scout can mention. */
    private ExpeditionEngine.DeathContext deathContext(Scout scout) {
        List<String> carried = new ArrayList<>();
        for (ItemInstance item : items.values()) {
            if (item.getLocation().equals(ItemInstance.carriedBy(scout.getId()))) {
                carried.add(content.item(item.getItemId()).getName().toLowerCase(Locale.ROOT));
            }
        }
        List<Scout> alive = new ArrayList<>();
        for (Scout s : scouts.values()) {
            if (s.isAlive()) {
                alive.add(s);
            }
        }
        Scout companion = affinities.closest(scout.getId(), alive);
        return new ExpeditionEngine.DeathContext(carried, companion != null ? companion.getName() : null);
    }

    /** GDD §5.5 "afterwards": belongings stay in the zone, the others grieve, the task is left. */
    private void afterDeath(Scout dead, String zoneId, Step step) {
        for (com.estatica.domain.bunker.GearPiece worn : dead.getLife().takeAllOff()) {
            worn.moveTo(com.estatica.domain.bunker.GearPiece.lostIn(zoneId));
            worn.setOriginalOwnerId(dead.getId());
        }
        for (ItemInstance item : items.values()) {
            if (item.getLocation().equals(ItemInstance.carriedBy(dead.getId()))) {
                item.moveTo(ItemInstance.lostIn(zoneId));
                item.setOriginalOwnerId(dead.getId());
            }
        }
        for (Conversation c : conversations.values()) {
            if (c.getScoutId().equals(dead.getId()) && c.isPending()) {
                c.setStatus(Conversation.Status.EXPIRED);
                step.conversationsChanged.add(c);
            }
        }
        GriefRules.Result reaction = grief.onDeath(dead, scouts.values(), affinities, random, step.now);
        step.lines.addAll(reaction.lines);
        step.memories.addAll(reaction.memories);
    }

    /** GDD §5.5: belongings of the dead can be recovered by going back to that zone. */
    private void recoverLostItems(Scout finder, String zoneId, Step step) {
        for (com.estatica.domain.bunker.GearPiece piece : gear.values()) {
            if (!piece.getLocation().equals(com.estatica.domain.bunker.GearPiece.lostIn(zoneId))) {
                continue;
            }
            piece.moveTo(com.estatica.domain.bunker.GearPiece.BUNKER);
            Scout owner = scouts.get(piece.getOriginalOwnerId());
            Map<String, String> vars = new HashMap<>();
            vars.put("name", finder.getName());
            vars.put("dead", owner != null ? owner.getName() : "?");
            vars.put("gear", gearName(piece));
            step.system(content.getTerminal().getBase().getGearRecovered(), vars, finder);
        }
        for (ItemInstance item : items.values()) {
            if (!item.getLocation().equals(ItemInstance.lostIn(zoneId))) {
                continue;
            }
            item.moveTo(ItemInstance.BUNKER);
            Scout owner = scouts.get(item.getOriginalOwnerId());
            String itemName = content.item(item.getItemId()).getName().toLowerCase(Locale.ROOT);
            Map<String, String> vars = new HashMap<>();
            vars.put("name", finder.getName());
            vars.put("item", itemName);
            vars.put("dead", owner != null ? owner.getName() : "?");
            step.system(content.getTerminal().getBunker().getItemRecovered(), vars, finder);
            step.memories.add(Memory.create(finder.getId(), Memory.Type.EVENT,
                    TextTemplate.format(content.getTerminal().getBunker().getRecoveredMemory(), vars),
                    item.getItemId(), step.now));
        }
    }

    /** Ambient chatter: now and then, someone in the bunker says something. */
    private void maybeBark(Step step) {
        Balance.Barks config = content.getBalance().getBarks();
        double last = value("last_bark");
        if (last <= 0) {
            setValue("last_bark", step.now, step);
            return;
        }
        if (step.now - last < config.getIntervalMinutes() * 60_000L) {
            return;
        }
        setValue("last_bark", step.now, step);
        if (random.chance(config.getChance())) {
            LogLine line = barks.bark(scouts.values(), affinities, random, step.now, value(KEY_HUNGRY) > 0);
            if (line != null) {
                step.lines.add(line.withThread(line.getScoutId() != null
                        ? LogLine.chatThread(line.getScoutId()) : LogLine.SYSTEM_THREAD));
            }
        }
    }

    private void expireConversations(Step step) {
        long expireAfter = content.getBalance().getBond().getInitiatedExpireHours() * HOUR;
        for (Conversation c : conversations.values()) {
            Scout scout = scouts.get(c.getScoutId());
            boolean gone = !scout.isAlive() || scout.getStatus() != ScoutStatus.IDLE;
            boolean stale = c.isInitiated() && step.now - c.getStartedAtMillis() >= expireAfter;
            if (c.isPending() && (gone || stale)) {
                c.setStatus(Conversation.Status.EXPIRED);
                step.conversationsChanged.add(c);
                Map<String, String> vars = new HashMap<>();
                vars.put("name", scout.getName());
                step.system(content.getTerminal().getBunker().getConversationExpired(), vars, scout);
            }
        }
    }

    /** At most one scout starts a conversation per step ("got a minute?"). */
    private void maybeStartConversation(Step step) {
        long cooldown = content.getBalance().getBond().getInitiatedCooldownHours() * HOUR;
        for (Scout scout : scouts.values()) {
            if (hasPendingConversation(scout)
                    || step.now - scout.getLife().getLastInitiatedAtMillis() < cooldown) {
                continue;
            }
            DialogueDef dialogue = dialogues.pick(scout, true, scouts.values(), random, step.now);
            if (dialogue == null) {
                continue;
            }
            step.into(dialogues.open(scout, dialogue, true, scouts.values(), random, step.now),
                    LogLine.chatThread(scout.getId()));
            Conversation c = Conversation.start(scout.getId(), dialogue.getId(), step.now, true);
            conversations.put(c.getId(), c);
            step.conversationsChanged.add(c);
            return;
        }
    }

    private boolean hasPendingConversation(Scout scout) {
        for (Conversation c : conversations.values()) {
            if (c.isPending() && c.getScoutId().equals(scout.getId())) {
                return true;
            }
        }
        return false;
    }

    // ---- Player actions ----

    /** Sends a scout out to explore. */
    @WorkerThread
    public List<LogLine> dispatch(String scoutId, String zoneId) {
        return dispatch(scoutId, zoneId, Mission.Type.EXPLORE, null);
    }

    /**
     * Sends a scout out. Catches up first so the action happens on current state.
     *
     * @param target survivor id for rescues, else null
     */
    @WorkerThread
    public List<LogLine> dispatch(String scoutId, String zoneId, Mission.Type type, String target) {
        return dispatch(scoutId, zoneId, type, target, null);
    }

    /** Who goes along on an expedition, and how they get on with the lead. */
    private ExpeditionEngine.Party party(Expedition expedition) {
        Scout partner = expedition.getPartnerId() != null ? scouts.get(expedition.getPartnerId()) : null;
        if (partner == null || !partner.isAlive()) {
            return ExpeditionEngine.Party.ALONE;
        }
        Scout lead = scouts.get(expedition.getScoutId());
        return new ExpeditionEngine.Party(partner, bunker.relationship(affinities, lead, partner));
    }

    /**
     * Sends a scout out, optionally with a partner (GDD §5.3).
     *
     * @param partnerId second scout, or null to go alone
     */
    @WorkerThread
    public List<LogLine> dispatch(String scoutId, String zoneId, Mission.Type type, String target, String partnerId) {
        Step step = new Step();
        advance(step);
        Scout scout = scouts.get(scoutId);
        ZoneDef zone = content.getZones().get(zoneId);
        if (type == Mission.Type.SCOUT) {
            com.estatica.domain.map.RegionMap.Sector sector = target != null ? regionMap.sector(target) : null;
            zone = sector != null ? content.wilds(sector.bandId) : null;
            if (sector == null || !canScout(sector)) {
                return step.commit();
            }
        }
        if (scout == null || zone == null || !rules.canDispatch(scout) || !canReach(zone)) {
            return step.commit();
        }
        switch (type) {
            case RESCUE:
                if (target == null || !zoneId.equals(rescueTargets.get(target))) {
                    return step.commit();
                }
                break;
            case INSTALL_RELAY:
                if (relays.containsKey(zoneId) || !pay(Map.of("relay_kit", 1), step)) {
                    return step.commit();
                }
                break;
            case MAINTAIN_RELAY:
                if (!relays.containsKey(zoneId)
                        || !pay(content.getBalance().getRadio().getRelayMaintenanceCost(), step)) {
                    return step.commit();
                }
                break;
            case PERSONAL: {
                StoryDef.PersonalMission personal = content.getStory().personalMission(target);
                if (personal == null || !personalAvailable(personal, scout) || !zoneId.equals(personal.getZone())) {
                    return step.commit();
                }
                break;
            }
            default:
                break;
        }
        Map<String, String> zoneIntel = intel.getOrDefault(zoneId, Map.of());
        boolean cache = zoneIntel.containsKey("CACHE");
        boolean danger = zoneIntel.containsKey("DANGER");
        if (cache || danger) {
            String cacheId = zoneIntel.get("CACHE");
            String dangerId = zoneIntel.get("DANGER");
            intel.remove(zoneId);
            step.radioWrites.add(() -> {
                if (cacheId != null) {
                    radioRepository.useIntel(cacheId);
                }
                if (dangerId != null) {
                    radioRepository.useIntel(dangerId);
                }
            });
        }
        RadioRules.Quality quality = radio.quality(zone, relays, moduleLevel("radio"));
        double noise = quality.noise * PowerGrid.radioNoiseFactor(grid(step.now).units("radio"));
        noise *= rules.gearNoiseFactor(scout);
        double lootFactor = zone.isWilds() ? 1.0 : lootFactor(zone.getId(), step.now);
        Mission mission = new Mission(type, target, Math.min(1.0, noise), quality.orderDelayMillis, cache, danger,
                lootFactor);
        Scout partner = partnerId != null ? scouts.get(partnerId) : null;
        if (partner != null && (partner == scout || !rules.canDispatch(partner))) {
            return step.commit();
        }
        ExpeditionEngine.Party party = partner == null ? ExpeditionEngine.Party.ALONE
                : new ExpeditionEngine.Party(partner, bunker.relationship(affinities, scout, partner));
        if (partner != null) {
            // The clearer radio of the two carries the reports.
            noise = Math.min(noise, quality.noise * PowerGrid.radioNoiseFactor(grid(step.now).units("radio"))
                    * rules.gearNoiseFactor(partner));
            mission = new Mission(type, target, Math.min(1.0, noise), quality.orderDelayMillis, cache, danger,
                    lootFactor);
        }
        ExpeditionEngine.Dispatch dispatch = engine.dispatch(scout, zone, random.deriveSeed(), mission, party, step.now);
        step.into(dispatch.lines, LogLine.expeditionThread(dispatch.expedition.getId()));
        for (com.estatica.domain.expedition.Segment segment : dispatch.expedition.getSegments()) {
            if (segment.getEventId() != null) {
                setValue(EVENT_SEEN + segment.getEventId(), step.now, step);
            }
        }
        Map<String, String> left = new HashMap<>();
        left.put("who", scout.getName() + (partner != null ? " + " + partner.getName() : ""));
        left.put("zone", zone.getName());
        step.lines.add(LogLine.draft(step.now, LogChannel.SYSTEM, null,
                TextTemplate.format(content.getTerminal().getExpedition().getSummaryDeparted(), left), scout.getId())
                .withThread(LogLine.summaryThread(dispatch.expedition.getId())));
        step.newExpeditions.add(dispatch.expedition);
        active.put(dispatch.expedition.getId(), dispatch.expedition);
        return step.commit();
    }

    /** The operator answers a situation. Ignored if it already timed out. */
    @WorkerThread
    public List<LogLine> order(String expeditionId, int option) {
        Step step = new Step();
        advance(step);
        Expedition expedition = active.get(expeditionId);
        if (expedition != null && engine.choose(expedition, option, step.now)) {
            long delay = expedition.getMission().getOrderDelayMillis();
            if (delay > 0) {
                Map<String, String> vars = new HashMap<>();
                vars.put("delay", ElapsedFormatter.format(delay, content.getTerminal().getElapsedUnits()));
                step.system(content.getTerminal().getRadio().getOrderDelayed(), vars,
                        scouts.get(expedition.getScoutId()));
            }
            advance(step);
        }
        return step.commit();
    }

    // ---- Radio (GDD §6.1, §6.2) ----

    public int moduleLevel(String moduleId) {
        return moduleLevels.getOrDefault(moduleId, 1);
    }

    /** What a module adds over its first level (perLevel × levels above 1). */
    private double moduleBonus(String moduleId) {
        ModuleDef m = content.getBalance().module(moduleId);
        return m == null ? 0 : m.getPerLevel() * (moduleLevel(moduleId) - 1);
    }

    /** The generator's own storage plus the battery bank. */
    private double reserveCapacity() {
        return content.getBalance().getEnergy().batteryCapacity(moduleLevel("generator")) + moduleBonus("battery_bank");
    }

    /** One battery into the reserve: a bigger bank takes more of each. */
    private double rechargePerBattery() {
        return content.getBalance().getEnergy().getRechargePerBattery() + (moduleLevel("battery_bank") - 1);
    }

    private boolean isUnlocked(BandDef band) {
        if (content.getBalance().bandIndex(band.getId()) > 0
                && !PowerGrid.antennaOn(grid(clock.nowMillis()).units("antenna"))) {
            return false; // an unpowered antenna only hears the short band
        }
        return radio.isUnlocked(band, moduleLevel("antenna"), relays.values());
    }

    /** Known and within reach. */
    private boolean canReach(ZoneDef zone) {
        return (zone.isWilds() || knownZones.contains(zone.getId()))
                && isUnlocked(content.getBalance().band(zone.getBand()));
    }

    // ---- Places run dry ----

    private static final String DEPLETION = "dep:";
    private static final String DEPLETION_AT = "dept:";

    /** How picked-over a place is now (0 untouched, 1 bare), recovering with time. */
    /** How long until a picked-over place is full again ("2 d 4 h"), or null if it already is. */
    private String refillIn(String zoneId, long now) {
        double left = depletion(zoneId, now);
        double perDay = content.getBalance().getDepletion().getRecoveryPerDay();
        if (left < 0.01 || perDay <= 0) {
            return null;
        }
        return ElapsedFormatter.format(Math.round(left / perDay * 24 * HOUR), content.getTerminal().getElapsedUnits());
    }

    public double depletion(String zoneId, long now) {
        double stored = value(DEPLETION + zoneId);
        if (stored <= 0) {
            return 0;
        }
        double days = Math.max(0, now - value(DEPLETION_AT + zoneId)) / (24.0 * HOUR);
        return Math.max(0, stored - days * content.getBalance().getDepletion().getRecoveryPerDay());
    }

    private double lootFactor(String zoneId, long now) {
        return 1 - depletion(zoneId, now) * content.getBalance().getDepletion().getMaxPenalty();
    }

    /** A trip there takes its share. */
    private void pickOver(String zoneId, Step step) {
        double now = Math.min(1, depletion(zoneId, step.now) + content.getBalance().getDepletion().getPerTrip());
        setValue(DEPLETION + zoneId, now, step);
        setValue(DEPLETION_AT + zoneId, step.now, step);
    }

    // ---- The region map (scouting) ----

    private static final String SCOUTED = "scouted:";
    private static final String HEARD = "heard:";
    /** When an expedition's result first reached the operator (its chip stays 4 h past that). */
    private static final String LOG_DONE = "logdone:";
    private static final long LOG_CHIP_MILLIS = 4 * 3_600_000L;
    /** Finished expeditions still shown as chips: id to {lead, partner}. */
    private final Map<String, String[]> doneTeams = new LinkedHashMap<>();

    /**
     * The result is in. Steps only run while the game is open, so this is when the operator
     * could see it: on screen if they were here, or on opening the game if not.
     */
    private void markLogDone(Expedition expedition, Step step) {
        setValue(LOG_DONE + expedition.getId(), step.now, step);
        doneTeams.put(expedition.getId(), new String[]{expedition.getScoutId(), expedition.getPartnerId()});
    }

    /** When a situation last came up: recent ones wait at the back of the queue. */
    private static final String EVENT_SEEN = "evseen:";

    /** Sends a scout to map out a sector. */
    @WorkerThread
    public List<LogLine> scoutSector(String scoutId, String sectorId) {
        com.estatica.domain.map.RegionMap.Sector sector = regionMap.sector(sectorId);
        if (sector == null) {
            return List.of();
        }
        return dispatch(scoutId, content.wilds(sector.bandId).getId(), Mission.Type.SCOUT, sectorId);
    }

    private boolean isScouted(String sectorId) {
        return gameFlags.contains(SCOUTED + sectorId);
    }

    private boolean beingScouted(String sectorId) {
        for (Expedition e : active.values()) {
            if (e.getMission().getType() == Mission.Type.SCOUT && sectorId.equals(e.getMission().getTarget())) {
                return true;
            }
        }
        return false;
    }

    private boolean canScout(com.estatica.domain.map.RegionMap.Sector sector) {
        return !isScouted(sector.id) && !beingScouted(sector.id)
                && isUnlocked(content.getBalance().band(sector.bandId));
    }

    private void addGameFlag(String flag, Step step) {
        if (gameFlags.add(flag)) {
            step.radioWrites.add(() -> radioRepository.addFlag(flag, step.now));
        }
    }

    private Map<String, String> sectorVars(com.estatica.domain.map.RegionMap.Sector sector) {
        Map<String, String> vars = new HashMap<>();
        List<String> names = content.getTerminal().getBase().getDirections();
        // Name the sector by the nearest of the content's compass points.
        int point = names.isEmpty() ? 0 : (int) Math.round(sector.turn() * names.size()) % names.size();
        vars.put("direction", names.isEmpty() ? "?" : names.get(point));
        vars.put("band", content.getBalance().band(sector.bandId).getName().toLowerCase(Locale.ROOT));
        return vars;
    }

    /** The scout came back from a sector: it's on the map now, with whatever was out there. */
    private void mapSector(String sectorId, Scout scout, Step step) {
        com.estatica.domain.map.RegionMap.Sector sector = regionMap.sector(sectorId);
        if (sector == null) {
            return;
        }
        addGameFlag(SCOUTED + sectorId, step);
        TerminalText.BaseText text = content.getTerminal().getBase();
        Map<String, String> vars = sectorVars(sector);
        vars.put("name", scout.getName());
        step.system(text.getMapScouted(), vars, scout);
        boolean found = false;
        for (String zoneId : regionMap.zonesIn(sectorId)) {
            if (!knownZones.contains(zoneId)) {
                discover(zoneId, step);
                found = true;
                Map<String, String> zoneVars = new HashMap<>();
                zoneVars.put("name", scout.getName());
                zoneVars.put("zone", content.zone(zoneId).getName());
                step.system(text.getMapFoundZone(), zoneVars, scout);
            }
        }
        if (!found) {
            step.system(text.getMapNothing(), vars, scout);
        }
    }

    private Map<String, String> glossary;

    /** Every term worth explaining, built once from content. */
    private Map<String, String> glossary() {
        if (glossary != null) {
            return glossary;
        }
        Map<String, String> g = new java.util.LinkedHashMap<>(content.getTerminal().getGlossary());
        for (com.estatica.domain.content.TraitDef t : content.getTraits().values()) {
            g.put(t.getName(), t.getDescription());
        }
        content.getTerminal().getStatNames().forEach((key, name) ->
                g.put(name, content.getTerminal().getStatDescriptions().get(key)));
        content.getTerminal().getMoods().forEach((key, name) ->
                g.put(name, content.getTerminal().getMoodDescriptions().get(key)));
        for (com.estatica.domain.content.ResourceDef r : content.getResources().values()) {
            if (r.getUse() != null) {
                g.put(r.getName(), r.getUse());
            }
        }
        for (GearDef gd : content.getGear().values()) {
            g.put(gd.getName(), gd.getDescription());
        }
        for (ZoneDef z : content.getZones().values()) {
            g.put(z.getName(), z.getDescription());
        }
        for (BandDef b : content.getBalance().getBands()) {
            if (b.getDescription() != null) {
                g.put(b.getName(), b.getDescription());
            }
        }
        for (ModuleDef m : content.getBalance().getModules()) {
            g.put(m.getName(), m.getDescription());
        }
        for (EnergyDef.Consumer c : content.getBalance().getEnergy().getConsumers()) {
            g.putIfAbsent(c.getName(), c.getDescription());
        }
        g.values().removeIf(java.util.Objects::isNull);
        glossary = java.util.Collections.unmodifiableMap(g);
        return glossary;
    }

    // ---- Ciphers: what each scheme needs and what the key item does for you ----

    private SignalsDef.Transmission signalDef(String sourceId) {
        for (SignalsDef.Transmission d : content.getSignals().getTransmissions()) {
            if (d.getId().equals(sourceId)) {
                return d;
            }
        }
        return null;
    }

    /** Key item a transmission needs before you can work on it, or null. */
    private String requiredKey(Transmission t) {
        switch (t.getKind()) {
            case ATBASH:
                return "mirror_card";
            case TAP:
                return "tap_chart";
            case VIGENERE:
                return "cipher_notebook";
            case BASE64:
                return "b64_notebook";
            case PLAYFAIR:
                return "military_codebook";
            case BOOK:
                return "field_handbook";
            case NUMBERS: {
                SignalsDef.Transmission def = signalDef(t.getSourceId());
                return def != null ? def.getKey() : null;
            }
            default:
                return null;
        }
    }

    private SessionSnapshot.TransmissionView transmissionView(Transmission t) {
        TerminalText.RadioText text = content.getTerminal().getRadio();
        String need = requiredKey(t);
        boolean unlocked = need == null || hasKey(need);
        SignalsDef.Transmission def = signalDef(t.getSourceId());
        String cipher = t.cipherText();
        String assisted = null;
        if (unlocked) {
            switch (t.getKind()) {
                case ATBASH:
                    assisted = com.estatica.domain.radio.Ciphers.atbash(cipher);
                    break;
                case BASE64: {
                    String plain = com.estatica.domain.radio.Ciphers.unbase64(cipher);
                    assisted = plain != null ? plain.toUpperCase(Locale.ROOT) : null;
                    break;
                }
                case PLAYFAIR:
                    assisted = com.estatica.domain.radio.Ciphers.fromNato(cipher);
                    break;
                case NUMBERS:
                    assisted = com.estatica.domain.radio.Ciphers.unnumbers(cipher, content.keyItem(need).getPad());
                    break;
                case BOOK:
                    assisted = com.estatica.domain.radio.Ciphers.unbook(cipher, content.keyItem(need).getPages());
                    break;
                default:
                    break;
            }
        }
        String locked = unlocked ? null : text.getLockedHints().getOrDefault(t.getKind().name(), "");
        return new SessionSnapshot.TransmissionView(t.getId(), t.getKind().name(), cipher, t.getBandId(), unlocked,
                locked, def != null ? def.getKeyHint() : null, assisted,
                text.getSchemeNames().getOrDefault(t.getKind().name(), t.getKind().name()));
    }

    // ---- Key items (notebooks, manuals, codebooks…) ----

    private static final String KEY_FLAG = "key:";

    public boolean hasKey(String keyId) {
        return gameFlags.contains(KEY_FLAG + keyId);
    }

    /** Yours now: kept in the inventory, readable, may unlock a way to decode. */
    private void grantKey(String keyId, Scout finder, Step step) {
        com.estatica.domain.content.KeyItemDef def = content.keyItem(keyId);
        if (def == null || hasKey(keyId)) {
            return;
        }
        addGameFlag(KEY_FLAG + keyId, step);
        Map<String, String> vars = new HashMap<>();
        vars.put("item", def.getName());
        vars.put("name", finder != null ? finder.getName() : "");
        step.system(content.getTerminal().getBase().getKeyItemFound(), vars, finder);
    }

    private List<SessionSnapshot.KeyItemView> keyItemViews() {
        List<SessionSnapshot.KeyItemView> out = new ArrayList<>();
        for (com.estatica.domain.content.KeyItemDef k : content.getKeyItems().values()) {
            if (hasKey(k.getId())) {
                out.add(new SessionSnapshot.KeyItemView(k.getId(), k.getName(), k.getDescription(), k.getText()));
            }
        }
        return out;
    }

    private List<String> placeNames() {
        List<String> out = new ArrayList<>();
        for (ZoneDef z : content.getZones().values()) {
            out.add(z.getName());
        }
        for (ZoneDef z : content.getWilds()) {
            out.add(z.getName());
        }
        return out;
    }

    private List<String> resourceNames() {
        List<String> out = new ArrayList<>();
        for (com.estatica.domain.content.ResourceDef r : content.getResources().values()) {
            out.add(r.getName());
        }
        for (com.estatica.domain.content.ItemDef i : content.getItems().values()) {
            out.add(i.getName());
        }
        return out;
    }

    private SessionSnapshot.MapView mapView() {
        List<SessionSnapshot.SectorView> sectors = new ArrayList<>();
        for (com.estatica.domain.map.RegionMap.Sector s : regionMap.sectors()) {
            Map<String, String> vars = sectorVars(s);
            sectors.add(new SessionSnapshot.SectorView(s.id, s.ring, s.direction, s.count,
                    vars.get("direction") + " · " + vars.get("band"), isScouted(s.id),
                    gameFlags.contains(HEARD + s.id), isUnlocked(content.getBalance().band(s.bandId)),
                    beingScouted(s.id)));
        }
        List<SessionSnapshot.PlaceView> places = new ArrayList<>();
        for (String zoneId : knownZones) {
            ZoneDef z = content.getZones().get(zoneId);
            com.estatica.domain.map.RegionMap.Place place = z != null ? regionMap.placeOf(zoneId) : null;
            if (place != null) {
                places.add(new SessionSnapshot.PlaceView(zoneId, z.getName(),
                        content.getBalance().bandIndex(z.getBand()), place.ring, place.turn, z.getDanger(), canReach(z),
                        (int) Math.round(depletion(zoneId, clock.nowMillis()) * 100), z.getIcon(), z.getShortName(),
                        refillIn(zoneId, clock.nowMillis())));
            }
        }
        List<String> bands = new ArrayList<>();
        for (BandDef b : content.getBalance().getBands()) {
            if (content.wilds(b.getId()) != null) {
                bands.add(b.getName());
            }
        }
        return new SessionSnapshot.MapView(sectors, places, bands,
                (int) Math.round(content.getBalance().getDepletion().getRecoveryPerDay() * 100));
    }

    /** What the dial hears at a position: the strongest of a hidden signal, a station or an echo. */
    private static final class Heard {
        double strength;
        SignalField.Spot spot;
        SignalsDef.Station station;
        Scout echo;
        /** The spot was already locked onto: still on the air, nothing more to get from it. */
        boolean caught;

        boolean anything() {
            return spot != null || station != null || echo != null;
        }
    }

    private Heard listen(BandDef band, int position, long now) {
        Heard heard = new Heard();
        long window = field.window(now);
        SignalField.Reading fresh = field.tune(band, position, window, caughtSignals);
        SignalField.Reading any = field.tune(band, position, window, Set.of());
        // A signal you already logged stays on the dial, it just can't be locked again.
        SignalField.Reading reading = any.strength > fresh.strength ? any : fresh;
        heard.strength = reading.strength;
        heard.spot = reading.spot;
        heard.caught = reading.spot != null && caughtSignals.contains(reading.spot.key());
        boolean night = content.getBalance().getEnergy().isNight(clock.localHour(now));
        for (SignalsDef.Station station : content.getSignals().getStations()) {
            if (!band.getId().equals(station.getBand()) || (station.isNight() && !night)
                    || !Broadcasts.onAir(station, clock.localMillis(now))) {
                continue;
            }
            double strength = closeness(band, position, station.getPosition());
            if (strength > heard.strength) {
                heard.strength = strength;
                heard.station = station;
                heard.spot = null;
                heard.caught = false;
            }
        }
        for (Scout dead : echoes(band, now)) {
            double strength = closeness(band, position, echoPosition(dead, now));
            if (strength > heard.strength) {
                heard.strength = strength;
                heard.echo = dead;
                heard.station = null;
                heard.spot = null;
                heard.caught = false;
            }
        }
        return heard;
    }

    private static double closeness(BandDef band, int position, int at) {
        return Math.max(0, 1.0 - Math.abs(position - at) / (double) band.getTuneRadius());
    }

    /** Echoes already heard this session, by scout and window: each comes once. */
    private final Set<String> echoesHeard = new java.util.HashSet<>();

    /**
     * The fallen turn up close to home, on the short band, in one window out of a few, some
     * hours after they went quiet (GDD §5.5: the channel stays open).
     */
    private List<Scout> echoes(BandDef band, long now) {
        SignalsDef.Echo echo = content.getSignals().getEcho();
        List<Scout> out = new ArrayList<>();
        if (echo == null || content.getBalance().bandIndex(band.getId()) != 0) {
            return out;
        }
        long window = field.window(now);
        for (Scout s : scouts.values()) {
            if (s.isAlive() || now - s.getLife().getDiedAtMillis() < echo.getAfterHours() * HOUR
                    || echoesHeard.contains(s.getId() + "@" + window)
                    || Math.floorMod((s.getId() + "~" + window).hashCode(), echo.getEveryWindows()) != 0) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    private int echoPosition(Scout s, long now) {
        return Math.floorMod((s.getId() + "#" + field.window(now)).hashCode(),
                content.getBalance().getRadio().getDialMax());
    }

    /**
     * Seeded per signal, so previewing what a signal is and locking onto it roll the same dice:
     * a transmission that sounds like Morse is Morse.
     */
    private GameRandom spotRandom(SignalField.Spot spot) {
        return GameRandom.fromSeed(rngSeed ^ (spot.key().hashCode() * 0x9E3779B97F4A7C15L));
    }

    private static final String RARE_HEARD = "rare:";

    private Set<String> heardRares() {
        Set<String> out = new java.util.HashSet<>();
        for (String flag : gameFlags) {
            if (flag.startsWith(RARE_HEARD)) {
                out.add(flag.substring(RARE_HEARD.length()));
            }
        }
        return out;
    }

    private RadioRules.Outcome resolve(SignalField.Spot spot, GameRandom rng) {
        boolean roomForSurvivor = rescueTargets.size() < content.getBalance().getPeople().getMaxPendingRescues();
        return radio.resolve(spot, knownZones, roomForSurvivor, receivedTransmissions, heardRares(), rng);
    }

    /** A rare find that leaves nothing behind (or lore already kept) costs no reserve. */
    private boolean isFree(RadioRules.Outcome outcome) {
        return outcome.kind == SignalField.Kind.RARE && (outcome.rare.isFlavor()
                || (outcome.rare.getResources().isEmpty() && lore.containsKey(outcome.rare.getId())));
    }

    /** The sound a scheme makes on the air (SoundEngine picks the loop). */
    private static String audioOf(Transmission.Kind scheme) {
        switch (scheme) {
            case MORSE:
            case TAP:
                return "MORSE";
            case NUMBERS:
                return "NUMBERS";
            case BOOK:
            case PLAYFAIR:
                return "VOICE";
            default:
                return "DATA";
        }
    }

    /** What a station is saying right now: its lines take turns every few minutes. */
    private String stationLine(SignalsDef.Station station, long now) {
        List<String> lines = new ArrayList<>(station.getLines());
        List<String> fallen = new ArrayList<>();
        for (Scout s : scouts.values()) {
            if (!s.isAlive()) {
                fallen.add(s.getName());
            }
        }
        if (!fallen.isEmpty()) {
            lines.addAll(station.getDeadLines());
        }
        if (lines.isEmpty()) {
            return content.getTerminal().getRadio().getStaticNoise();
        }
        long turn = station.isPerAiring() && station.getSchedule() != null
                ? Broadcasts.airing(station, clock.localMillis(now))
                : now / (station.getMinutesPerLine() * 60_000L);
        Map<String, String> vars = new HashMap<>();
        vars.put("dead", fallen.isEmpty() ? "" : fallen.get((int) Math.floorMod(turn, (long) fallen.size())));
        return TextTemplate.format(lines.get((int) Math.floorMod(turn, (long) lines.size())), vars);
    }

    /** Their last words, or the last thing they kept. */
    private String echoWords(Scout dead) {
        String words = null;
        for (Memory m : memoryRepository.forScout(dead.getId())) {
            if (m.getType() == Memory.Type.LAST_WORDS || words == null) {
                words = m.getText();
            }
        }
        return words != null ? words : "...";
    }

    /**
     * What the dial picks up at a position. Read-only: moving the dial changes nothing, only
     * locking on does.
     */
    @WorkerThread
    public SessionSnapshot.Tuning tune(String bandId, int position) {
        BandDef band = content.getBalance().band(bandId);
        long now = clock.nowMillis();
        TerminalText.RadioText text = content.getTerminal().getRadio();
        String frequency = band.frequency(position);
        if (!isUnlocked(band)) {
            return SessionSnapshot.Tuning.quiet(text.getStaticNoise(), frequency);
        }
        Heard heard = listen(band, position, now);
        if (heard.strength < 0.1 || !heard.anything()) {
            return SessionSnapshot.Tuning.quiet(text.getStaticNoise(), frequency);
        }
        String key;
        String teaser;
        String audio;
        String label = null;
        int cost = 0;
        if (heard.station != null) {
            key = heard.station.getId();
            teaser = stationLine(heard.station, now);
            audio = heard.station.getAudio();
            label = heard.station.getName();
        } else if (heard.echo != null) {
            key = heard.echo.getId();
            List<String> teasers = content.getSignals().getEcho().getTeasers();
            Map<String, String> vars = new HashMap<>();
            vars.put("name", heard.echo.getName());
            teaser = teasers.isEmpty() ? text.getStaticNoise()
                    : TextTemplate.format(teasers.get(Math.floorMod(key.hashCode(), teasers.size())), vars);
            audio = "WHISPER";
        } else if (heard.caught) {
            key = heard.spot.key();
            teaser = text.getAlreadyCaught();
            audio = heard.spot.kind == SignalField.Kind.TRANSMISSION ? "DATA" : "VOICE";
            GameRandom flavor = GameRandom.fromSeed(key.hashCode() * 31L + position);
            return new SessionSnapshot.Tuning(heard.strength,
                    SignalDegrader.degrade(teaser, Math.max(0, 1.0 - heard.strength), flavor), false, frequency,
                    null, audio, 0);
        } else {
            key = heard.spot.key();
            GameRandom rng = spotRandom(heard.spot);
            RadioRules.Outcome outcome = resolve(heard.spot, rng);
            List<String> teasers;
            switch (outcome.kind) {
                case TRANSMISSION: {
                    Transmission.Kind scheme = radio.scheme(outcome.transmission, heard.spot.bandId, rng);
                    teasers = content.getSignals().teasers("TRANSMISSION", scheme.name());
                    audio = audioOf(scheme);
                    break;
                }
                case RARE:
                    teasers = outcome.rare.getTeaser() != null ? List.of(outcome.rare.getTeaser())
                            : content.getSignals().teasers("RARE", null);
                    audio = outcome.rare.getAudio();
                    break;
                default:
                    teasers = content.getSignals().teasers(outcome.kind.name(), null);
                    audio = "VOICE";
            }
            teaser = teasers.get(Math.floorMod(key.hashCode(), teasers.size()));
            cost = isFree(outcome) ? 0 : (int) Math.ceil(content.getBalance().getEnergy().getSweepCost());
        }
        GameRandom flavor = GameRandom.fromSeed(key.hashCode() * 31L + position);
        String shown = SignalDegrader.degrade(teaser, Math.max(0, 1.0 - heard.strength), flavor);
        boolean lockable = heard.strength >= content.getBalance().getRadio().getLockThreshold();
        return new SessionSnapshot.Tuning(heard.strength, shown, lockable, frequency, label, audio, cost);
    }

    /** Locks onto whatever the dial hears, if it's clear enough. */
    @WorkerThread
    public List<LogLine> capture(String bandId, int position) {
        Step step = new Step();
        advance(step);
        BandDef band = content.getBalance().band(bandId);
        Heard heard = isUnlocked(band) ? listen(band, position, step.now) : null;
        if (heard == null || heard.strength < content.getBalance().getRadio().getLockThreshold()
                || !heard.anything()) {
            step.system(content.getTerminal().getRadio().getNothingThere(), Map.of(), null);
            return step.commit();
        }
        if (heard.station != null) {
            listenToStation(heard.station, band.frequency(position), step);
            return step.commit();
        }
        if (heard.echo != null) {
            listenToEcho(heard.echo, band.frequency(position), step);
            return step.commit();
        }
        if (heard.caught) {
            step.system(content.getTerminal().getRadio().getAlreadyCaught(), Map.of(), null);
            return step.commit();
        }
        SignalField.Spot spot = heard.spot;
        boolean free = isFree(resolve(spot, spotRandom(spot)));
        double cost = content.getBalance().getEnergy().getSweepCost();
        if (!free && reserve < cost) {
            step.system(content.getTerminal().getBase().getReserveLow(), Map.of(), null);
            return step.commit();
        }
        count("sweeps", step);
        if (!free) {
            reserve -= cost;
            step.radioWrites.add(() -> baseRepository.saveValue(KEY_RESERVE, reserve));
        }
        receive(spot, step);
        return step.commit();
    }

    /** Stations are free: you only listen. The first time goes in the Diary. */
    private void listenToStation(SignalsDef.Station station, String frequency, Step step) {
        Map<String, String> vars = new HashMap<>();
        vars.put("frequency", frequency);
        vars.put("station", station.getName());
        vars.put("line", stationLine(station, step.now));
        step.system(content.getTerminal().getRadio().getStationHeard(), vars, null);
        if (station.getLore() != null) {
            addLore(station.getId(), station.getLore(), step);
        }
        unsettle(station.getStress(), step);
        if (station.hasBulletin()) {
            takeBulletin(station, Broadcasts.airing(station, clock.localMillis(step.now)), step);
        }
    }

    private static final String AIRING_TAKEN = "air:";

    /** Each airing's bulletin is caught once, by you or by whoever is on the radio. */
    private boolean takeBulletin(SignalsDef.Station station, long airing, Step step) {
        if (value(AIRING_TAKEN + station.getId()) >= airing + 1) {
            return false;
        }
        setValue(AIRING_TAKEN + station.getId(), airing + 1, step);
        grant(station.getResources(), step);
        if (station.getIntel() != null) {
            List<String> places = new ArrayList<>();
            for (String id : knownZones) {
                ZoneDef z = content.getZones().get(id);
                if (z != null && !z.isWilds() && canReach(z)) {
                    places.add(id);
                }
            }
            java.util.Collections.sort(places);
            if (!places.isEmpty()) {
                addIntel(random.pick(places), station.getIntel(), step);
            }
        }
        return true;
    }

    /** Intel on a place: the next expedition there uses it. */
    private void addIntel(String zoneId, String type, Step step) {
        intel.computeIfAbsent(zoneId, k -> new HashMap<>()).put(type, "pending");
        step.radioWrites.add(() -> {
            String id = radioRepository.addIntel(zoneId, type, step.now);
            intel.get(zoneId).put(type, id);
        });
        Map<String, String> vars = new HashMap<>();
        vars.put("zone", content.zone(zoneId).getName());
        step.system("CACHE".equals(type) ? content.getTerminal().getRadio().getIntelCache()
                : content.getTerminal().getRadio().getIntelDanger(), vars, null);
    }

    /**
     * Bulletins that aired since the last step, caught by whoever is on the radio task. This
     * is what the radio assistant is for, besides sweeping: being there at 06:00.
     */
    private void catchBroadcasts(Step step) {
        double last = value("air_check");
        setValue("air_check", step.now, step);
        if (last <= 0) {
            return;
        }
        Scout assistant = null;
        for (Scout s : scouts.values()) {
            String task = s.getLife().getTaskId();
            if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE && task != null
                    && content.task(task).isPassiveSweep()) {
                assistant = s;
            }
        }
        if (assistant == null) {
            return;
        }
        long from = clock.localMillis((long) last);
        long to = clock.localMillis(step.now);
        java.text.DateFormat time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT);
        for (SignalsDef.Station station : content.getSignals().getStations()) {
            if (!station.hasBulletin() || !isUnlocked(content.getBalance().band(station.getBand()))) {
                continue;
            }
            long newest = Broadcasts.airing(station, to);
            // Only the latest few: a week away doesn't fill the log.
            for (long n = Math.max(Broadcasts.airing(station, from) + 1, newest - 2); n <= newest; n++) {
                long startLocal = Broadcasts.startOf(station, n);
                long startUtc = step.now - (to - startLocal);
                if (station.isNight() && !content.getBalance().getEnergy().isNight(clock.localHour(startUtc))) {
                    continue;
                }
                Map<String, String> vars = new HashMap<>();
                vars.put("name", assistant.getName());
                vars.put("station", station.getName());
                vars.put("time", time.format(new Date(startUtc)));
                vars.put("line", stationLine(station, startUtc));
                if (value(AIRING_TAKEN + station.getId()) < n + 1) {
                    step.system(content.getTerminal().getRadio().getAssistantBulletin(), vars, assistant);
                    takeBulletin(station, n, step);
                }
            }
        }
    }

    private void listenToEcho(Scout dead, String frequency, Step step) {
        SignalsDef.Echo echo = content.getSignals().getEcho();
        echoesHeard.add(dead.getId() + "@" + field.window(step.now));
        Map<String, String> vars = new HashMap<>();
        vars.put("frequency", frequency);
        vars.put("name", dead.getName());
        vars.put("words", echoWords(dead));
        List<String> heard = echo.getHeard();
        if (!heard.isEmpty()) {
            step.system(heard.get(Math.floorMod((dead.getId() + step.now).hashCode(), heard.size())), vars, dead);
        }
        if (echo.getLore() != null) {
            addLore("echo:" + dead.getId(), TextTemplate.format(echo.getLore(), vars), step);
        }
        unsettle(echo.getStress(), step);
    }

    /** Everyone at home heard it too. */
    private void unsettle(int stress, Step step) {
        if (stress <= 0) {
            return;
        }
        boolean anyone = false;
        for (Scout s : scouts.values()) {
            if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE) {
                rules.addStress(s, stress);
                anyone = true;
            }
        }
        if (anyone) {
            step.system(content.getTerminal().getRadio().getCrewUneasy(), Map.of(), null);
        }
    }

    /** The radio assistant caught a signal on their own while the operator was away. */
    private void passiveCatch(Step step) {
        long window = field.window(step.now);
        List<SignalField.Spot> free = new ArrayList<>();
        for (BandDef band : content.getBalance().getBands()) {
            if (!isUnlocked(band)) {
                continue;
            }
            for (SignalField.Spot spot : field.spots(band, window)) {
                if (!caughtSignals.contains(spot.key())) {
                    free.add(spot);
                }
            }
        }
        if (free.isEmpty()) {
            return;
        }
        Scout assistant = null;
        for (Scout s : scouts.values()) {
            String task = s.getLife().getTaskId();
            if (s.isAlive() && task != null && content.task(task).isPassiveSweep()) {
                assistant = s;
            }
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("name", assistant != null ? assistant.getName() : "?");
        step.system(content.getTerminal().getRadio().getAssistantCaught(), vars, assistant);
        receive(random.pick(free), step);
    }

    private void receive(SignalField.Spot spot, Step step) {
        // Resolved before it's marked caught, with the same dice the preview rolled.
        GameRandom rng = spotRandom(spot);
        RadioRules.Outcome outcome = resolve(spot, rng);
        caughtSignals.add(spot.key());
        step.radioWrites.add(() -> radioRepository.addCaughtSignal(spot.key(), step.now));
        TerminalText.RadioText text = content.getTerminal().getRadio();
        Map<String, String> vars = new HashMap<>();
        switch (outcome.kind) {
            case ZONE: {
                com.estatica.domain.map.RegionMap.Place place = regionMap.placeOf(outcome.zoneId);
                if (place != null && !isScouted(place.sectorId)) {
                    // All places are found by scouting: the radio only says where to look.
                    if (gameFlags.add(HEARD + place.sectorId)) {
                        String heard = HEARD + place.sectorId;
                        step.radioWrites.add(() -> radioRepository.addFlag(heard, step.now));
                        step.system(content.getTerminal().getBase().getMapHeard(),
                                sectorVars(regionMap.sector(place.sectorId)), null);
                    }
                    break;
                }
                discover(outcome.zoneId, step);
                ZoneDef zone = content.zone(outcome.zoneId);
                vars.put("zone", zone.getName());
                vars.put("band", content.getBalance().band(zone.getBand()).getName().toLowerCase(Locale.ROOT));
                step.system(text.getZoneFound(), vars, null);
                break;
            }
            case SURVIVOR: {
                discover(outcome.zoneId, step);
                ScoutTemplate person = newPerson(outcome.zoneId);
                rescueTargets.put(person.getId(), outcome.zoneId);
                rescuePersonas.put(person.getId(), person);
                step.radioWrites.add(() -> radioRepository.addRescueTarget(person, outcome.zoneId, step.now));
                vars.put("name", person.getName());
                vars.put("zone", content.zone(outcome.zoneId).getName());
                step.system(text.getSurvivorFound(), vars, null);
                break;
            }
            case TRANSMISSION: {
                SignalsDef.Transmission def = outcome.transmission;
                String plain = def.getText();
                if (outcome.zoneId != null) {
                    plain = plain.replace("{zone}", content.zone(outcome.zoneId).getName());
                }
                Transmission t = radio.encrypt(def, spot.bandId, outcome.zoneId, plain.replace("{zone}", ""),
                        rng, step.now);
                transmissions.put(t.getId(), t);
                receivedTransmissions.add(def.getId());
                step.radioWrites.add(() -> radioRepository.save(t));
                step.system(text.getTransmissionFound(), vars, null);
                break;
            }
            default: {
                String heardFlag = RARE_HEARD + outcome.rare.getId();
                if (gameFlags.add(heardFlag)) {
                    step.radioWrites.add(() -> radioRepository.addFlag(heardFlag, step.now));
                }
                for (String line : outcome.rare.getText()) {
                    step.system(line, vars, null);
                }
                grant(outcome.rare.getResources(), step);
                if (outcome.rare.getLore() != null) {
                    addLore(outcome.rare.getId(), outcome.rare.getLore(), step);
                }
            }
        }
    }

    private void discover(String zoneId, Step step) {
        if (knownZones.add(zoneId)) {
            step.radioWrites.add(() -> radioRepository.addKnownZone(zoneId, step.now));
        }
    }

    private void addLore(String sourceId, String text, Step step) {
        if (!lore.containsKey(sourceId)) {
            lore.put(sourceId, text);
            step.radioWrites.add(() -> radioRepository.addLore(sourceId, text, step.now));
            step.system(content.getTerminal().getRadio().getLoreFound(), Map.of(), null);
        }
    }

    private void grant(Map<String, Integer> amounts, Step step) {
        if (amounts.isEmpty()) {
            return;
        }
        amounts.forEach((k, v) -> step.resourceDelta.merge(k, v, Integer::sum));
        Map<String, String> vars = new HashMap<>();
        vars.put("loot", describe(amounts));
        step.system(content.getTerminal().getRadio().getResourcesFound(), vars, null);
    }

    /** "3 scrap, 2 components". */
    private String describe(Map<String, Integer> amounts) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : new TreeMap<>(amounts).entrySet()) {
            Map<String, String> v = new HashMap<>();
            v.put("amount", String.valueOf(e.getValue()));
            v.put("resource", content.resource(e.getKey()).getName().toLowerCase(Locale.ROOT));
            parts.add(TextTemplate.format(content.getTerminal().getExpedition().getLootItem(), v));
        }
        return String.join(content.getTerminal().getExpedition().getLootSeparator(), parts);
    }

    private boolean canAfford(Map<String, Integer> cost) {
        for (Map.Entry<String, Integer> e : cost.entrySet()) {
            if (resources.getOrDefault(e.getKey(), 0) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Spends resources right away (so a second check in the same step sees the new balance),
     * or logs what is missing and returns false. Persisted with the step.
     */
    private boolean pay(Map<String, Integer> cost, Step step) {
        if (!canAfford(cost)) {
            Map<String, String> vars = new HashMap<>();
            vars.put("cost", describe(cost));
            step.system(content.getTerminal().getRadio().getCantAfford(), vars, null);
            return false;
        }
        for (Map.Entry<String, Integer> e : cost.entrySet()) {
            resources.merge(e.getKey(), -e.getValue(), Integer::sum);
            step.paid.merge(e.getKey(), -e.getValue(), Integer::sum);
        }
        return true;
    }

    /** The operator submits a decoding attempt. */
    @WorkerThread
    public List<LogLine> decode(String transmissionId, String answer) {
        Step step = new Step();
        advance(step);
        Transmission t = transmissions.get(transmissionId);
        TerminalText.RadioText text = content.getTerminal().getRadio();
        if (t == null || t.getStatus() != Transmission.Status.PENDING) {
            return step.commit();
        }
        if (!t.check(answer)) {
            step.system(text.getDecodeWrong(), Map.of(), null);
            return step.commit();
        }
        t.markSolved();
        count("decode", step);
        transmissions.remove(t.getId());
        step.radioWrites.add(() -> radioRepository.save(t));
        Map<String, String> vars = new HashMap<>();
        vars.put("text", t.getPlainText());
        step.system(text.getDecoded(), vars, null);

        SignalsDef.Transmission def = null;
        for (SignalsDef.Transmission d : content.getSignals().getTransmissions()) {
            if (d.getId().equals(t.getSourceId())) {
                def = d;
            }
        }
        if (def == null) {
            return step.commit();
        }
        switch (def.getReward()) {
            case CACHE:
            case DANGER:
                if (t.getZoneId() != null) {
                    addIntel(t.getZoneId(), def.getReward().name(), step);
                }
                break;
            case LORE:
                addLore(def.getId(), def.getLore(), step);
                break;
            case RESOURCES:
                grant(def.getResources(), step);
                break;
            case PLOT:
                foundPlot(def.getPlot(), step);
                break;
            case MORSE_TABLE:
                grantKey("radio_manual", null, step);
                if (gameFlags.add(FLAG_MORSE_TABLE)) {
                    step.radioWrites.add(() -> radioRepository.addFlag(FLAG_MORSE_TABLE, step.now));
                }
                step.system(text.getMorseTableUnlocked(), Map.of(), null);
                break;
            default:
                break;
        }
        return step.commit();
    }

    /** Upgrades a bunker module (the antenna opens farther bands). */
    @WorkerThread
    public List<LogLine> upgradeModule(String moduleId) {
        Step step = new Step();
        advance(step);
        ModuleDef module = content.getBalance().module(moduleId);
        int level = moduleLevel(moduleId);
        Map<String, Integer> cost = module.costFrom(level);
        if (cost != null && pay(cost, step)) {
            moduleLevels.put(moduleId, level + 1);
            step.radioWrites.add(() -> radioRepository.saveModule(moduleId, level + 1));
            Map<String, String> vars = new HashMap<>();
            vars.put("module", module.getName());
            vars.put("level", String.valueOf(level + 1));
            step.system(content.getTerminal().getRadio().getModuleUpgraded(), vars, null);
        }
        return step.commit();
    }

    /** Rescue / relay effects when an expedition comes back. */
    private void finishMission(Expedition expedition, Scout scout, boolean completed, Step step) {
        Mission mission = expedition.getMission();
        TerminalText.RadioText text = content.getTerminal().getRadio();
        Map<String, String> vars = new HashMap<>();
        vars.put("zone", content.zone(expedition.getZoneId()).getName());
        switch (mission.getType()) {
            case RESCUE:
                if (completed) {
                    recruit(mission.getTarget(), step);
                } else {
                    rescueFailed(mission.getTarget(), step);
                }
                break;
            case INSTALL_RELAY:
                if (completed) {
                    relays.put(expedition.getZoneId(), Relay.installed(expedition.getZoneId(), step.now));
                    step.system(text.getRelayInstalled(), vars, scout);
                } else {
                    step.resourceDelta.merge("relay_kit", 1, Integer::sum); // they bring it back
                }
                break;
            case MAINTAIN_RELAY:
                Relay relay = relays.get(expedition.getZoneId());
                if (completed && relay != null) {
                    relay.repair(step.now);
                    step.system(text.getRelayMaintained(), vars, scout);
                }
                break;
            case PERSONAL:
                if (completed) {
                    completePersonal(content.getStory().personalMission(mission.getTarget()), scout, step);
                }
                break;
            case SCOUT:
                if (completed) {
                    mapSector(mission.getTarget(), scout, step);
                }
                break;
            default:
                break;
        }
    }

    /**
     * The authored starters plus generated newcomers, so every game starts with someone
     * different. Seeded from the game seed: the main RNG is untouched.
     */
    private List<ScoutTemplate> startingCrew() {
        List<ScoutTemplate> crew = new ArrayList<>(content.getStarters());
        Set<String> names = new java.util.HashSet<>();
        for (ScoutTemplate t : crew) {
            names.add(t.getName());
        }
        GameRandom rng = GameRandom.fromSeed(state.getRngSeed() ^ 0x5741525453L);
        for (int i = 0; i < content.getBalance().getPeople().getGeneratedStarters(); i++) {
            ScoutTemplate person = personas.generate(com.estatica.domain.scouts.PersonaGenerator.ID_PREFIX + Ids.newId(),
                    0, crew, List.of(), names, rng);
            crew.add(person);
            names.add(person.getName());
        }
        return crew;
    }

    /** Someone new on the dial, unlike anyone in the bunker (see PersonaGenerator). */
    private ScoutTemplate newPerson(String zoneId) {
        List<ScoutTemplate> crew = new ArrayList<>();
        List<ScoutTemplate> recent = new ArrayList<>();
        Set<String> names = new java.util.HashSet<>();
        for (Scout s : scouts.values()) {
            names.add(s.getName());
            ScoutTemplate persona = content.persona(s);
            if (persona != null) {
                recent.add(0, persona); // roster order: newest last
                if (s.isAlive()) {
                    crew.add(persona);
                }
            }
        }
        for (String id : rescueTargets.keySet()) {
            ScoutTemplate waiting = rescuePersona(id);
            if (waiting != null) {
                crew.add(waiting);
                names.add(waiting.getName());
            }
        }
        int band = content.getBalance().bandIndex(content.zone(zoneId).getBand());
        return personas.generate(com.estatica.domain.scouts.PersonaGenerator.ID_PREFIX + Ids.newId(), band,
                crew, recent, names, random);
    }

    /** A waiting survivor: generated, or hand-made from an older save. */
    private ScoutTemplate rescuePersona(String survivorId) {
        ScoutTemplate persona = rescuePersonas.get(survivorId);
        return persona != null ? persona : content.starter(survivorId);
    }

    private void rescueFailed(String survivorId, Step step) {
        ScoutTemplate template = rescuePersona(survivorId);
        if (template != null) {
            Map<String, String> vars = new HashMap<>();
            vars.put("name", template.getName());
            step.system(content.getTerminal().getRadio().getRescueFailed(), vars, null);
        }
    }

    /** Roster limit (bunks). */
    public int rosterCap() {
        return content.getBalance().getRadio().getMaxScouts() + moduleLevel("bunks") - 1;
    }

    private int aliveScouts() {
        int count = 0;
        for (Scout s : scouts.values()) {
            if (s.isAlive()) {
                count++;
            }
        }
        return count;
    }

    private void recruit(String survivorId, Step step) {
        ScoutTemplate template = rescuePersona(survivorId);
        if (template == null) {
            return;
        }
        if (aliveScouts() >= rosterCap()) {
            step.system(content.getTerminal().getRadio().getRosterFull(), Map.of(), null);
            return;
        }
        int trust = template.getInitialTrust() != null
                ? template.getInitialTrust() : content.getBalance().getRadio().getSurvivorTrust();
        Scout recruit = ScoutFactory.fromTemplate(template, content.getBalance().getScouts(), step.now, trust);
        rescueTargets.remove(survivorId);
        rescuePersonas.remove(survivorId);
        step.radioWrites.add(() -> radioRepository.removeRescueTarget(survivorId));
        step.newScouts.add(recruit);
        scouts.put(recruit.getId(), recruit);
        Map<String, String> vars = new HashMap<>();
        vars.put("name", recruit.getName());
        step.system(content.getTerminal().getRadio().getRescued(), vars, recruit);
        for (String line : template.getRescueLines()) {
            step.lines.add(LogLine.draft(step.now, LogChannel.RADIO, recruit.getName(),
                    VoiceRenderer.render(content.voice(recruit.getVoiceId()), line, vars), recruit.getId()));
        }
    }

    // ---- Bunker: energy, workshop, raids (GDD §6.4, §6.5, §7) ----

    private PowerGrid.State grid(long now) {
        EnergyDef energy = content.getBalance().getEnergy();
        return PowerGrid.compute(energy, power, moduleLevel("generator"), events.blackoutWattsAt(now),
                energy.isNight(clock.localHour(now)));
    }

    /** Brings energy, the workshop, blackouts and raids up to date. */
    private PowerGrid.State baseAdvance(Step step) {
        long now = step.now;
        EnergyDef energy = content.getBalance().getEnergy();
        TerminalText.BaseText text = content.getTerminal().getBase();
        if (now > baseTick) {
            for (BunkerEvents.Blackout b : events.blackoutsStarting(baseTick, now)) {
                Map<String, String> vars = new HashMap<>();
                vars.put("watts", String.valueOf(b.watts));
                vars.put("hours", String.valueOf((b.end - b.start) / HOUR));
                step.lines.add(LogLine.draft(b.start, LogChannel.ALERT, null, TextTemplate.format(text.getBlackout(), vars)));
            }
            for (long at : events.raidsBetween(baseTick, now)) {
                raid(at, step);
            }
            double hours = (now - baseTick) / (double) HOUR;
            PowerGrid.State g = grid(now);
            reserve = Math.min(reserveCapacity(),
                    reserve + g.spare() * energy.getChargePerSpareWattHour() * hours);
            craft(hours, g, step);
            baseTick = now;
            double savedReserve = reserve;
            step.radioWrites.add(() -> {
                baseRepository.saveValue(KEY_TICK, now);
                baseRepository.saveValue(KEY_RESERVE, savedReserve);
            });
        }
        PowerGrid.State g = grid(now);
        String report = String.join(",", g.cut) + (g.filtersShort ? "|air" : "");
        if (!report.equals(lastPowerReport)) {
            for (String id : g.cut) {
                Map<String, String> vars = new HashMap<>();
                vars.put("consumer", energy.consumer(id).getName().toLowerCase(Locale.ROOT));
                step.system(text.getPowerCut(), vars, null);
            }
            if (g.filtersShort) {
                step.system(text.getBadAir(), Map.of(), null);
            }
            lastPowerReport = report;
        }
        return g;
    }

    private void raid(long at, Step step) {
        TerminalText.BaseText text = content.getTerminal().getBase();
        Map<String, Integer> stock = new HashMap<>(resources);
        step.resourceDelta.forEach((k, v) -> stock.merge(k, v, Integer::sum));
        RaidRules.Result result = raids.resolve(scouts.values(), stock, moduleLevel("antenna"), (int) moduleBonus("barricades"), random);
        Map<String, String> vars = new HashMap<>();
        if (result.repelled) {
            List<String> names = new ArrayList<>();
            for (Scout g : result.guards) {
                names.add(g.getName());
            }
            vars.put("guards", String.join(", ", names));
            step.lines.add(LogLine.draft(at, LogChannel.ALERT, null, TextTemplate.format(text.getRaidRepelled(), vars)));
            return;
        }
        if (result.unguarded) {
            step.lines.add(LogLine.draft(at, LogChannel.ALERT, null, text.getRaidUnguarded()));
        }
        if (!result.stolen.isEmpty()) {
            result.stolen.forEach((k, v) -> step.resourceDelta.merge(k, -v, Integer::sum));
            vars.put("loot", describe(result.stolen));
            step.lines.add(LogLine.draft(at, LogChannel.ALERT, null, TextTemplate.format(text.getRaidLost(), vars)));
        }
        if (result.injured != null) {
            vars.put("name", result.injured.getName());
            step.lines.add(LogLine.draft(at, LogChannel.ALERT, null,
                    TextTemplate.format(text.getRaidInjured(), vars), result.injured.getId()));
        }
    }

    /** Workshop speed: power, and the best Oficio among scouts on the workshop task. */
    private double workshopSpeed(PowerGrid.State g) {
        double base = PowerGrid.workshopSpeed(g.units("workshop"));
        return base * (1 + 0.05 * bestWorkshopCraft());
    }

    private int bestWorkshopCraft() {
        int bestCraft = 0;
        for (Scout s : scouts.values()) {
            if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE && "taller".equals(s.getLife().getTaskId())) {
                bestCraft = Math.max(bestCraft, rules.effectiveStat(s, StatType.CRAFT));
            }
        }
        return bestCraft;
    }

    private void craft(double hours, PowerGrid.State g, Step step) {
        if (craftJobs.isEmpty()) {
            return;
        }
        List<Double> minutes = new ArrayList<>();
        for (CraftJob job : craftJobs) {
            minutes.add(content.recipe(job.getRecipeId()).getMinutes());
        }
        List<CraftJob> done = CraftJob.work(craftJobs, minutes, hours, workshopSpeed(g));
        for (CraftJob job : craftJobs) {
            if (!done.contains(job)) {
                step.radioWrites.add(() -> baseRepository.save(job));
            }
        }
        for (CraftJob job : done) {
            RecipeDef recipe = content.recipe(job.getRecipeId());
            Map<String, String> vars = new HashMap<>();
            vars.put("item", recipe.getName());
            if (content.gear(recipe.getOutput()) != null) {
                for (int i = 0; i < recipe.getAmount(); i++) {
                    com.estatica.domain.bunker.GearPiece piece = com.estatica.domain.bunker.GearPiece.fresh(recipe.getOutput(),
                            gearRules.crafted(bestWorkshopCraft(), random));
                    gear.put(piece.getId(), piece);
                    vars.put("item", gearName(piece));
                }
            } else {
                step.resourceDelta.merge(recipe.getOutput(), recipe.getAmount(), Integer::sum);
            }
            step.system(content.getTerminal().getBase().getCraftDone(), vars, null);
            craftJobs.remove(job);
            step.radioWrites.add(() -> baseRepository.delete(job));
        }
    }

    /** Next craft completion, blackout or raid, or -1 (for wake-ups while the app is open). */
    private long nextBaseEvent(long now) {
        long best = events.nextEventAfter(now);
        double speed = workshopSpeed(grid(now));
        for (CraftJob job : craftJobs) {
            double left = job.minutesLeft(content.recipe(job.getRecipeId()).getMinutes(), speed);
            if (left >= 0) {
                long at = now + (long) Math.ceil(left * 60_000);
                if (best < 0 || at < best) {
                    best = at;
                }
            }
        }
        return best;
    }

    /** The operator sets how many watts a consumer gets. */
    @WorkerThread
    public List<LogLine> setPower(String consumerId, int units) {
        Step step = new Step();
        advance(step);
        EnergyDef.Consumer c = content.getBalance().getEnergy().consumer(consumerId);
        int value = Math.max(0, Math.min(c.getMax(), units));
        power.put(consumerId, value);
        step.radioWrites.add(() -> baseRepository.savePower(consumerId, value));
        baseAdvance(step);
        return step.commit();
    }

    /** Spends a battery to refill the energy reserve. */
    @WorkerThread
    public List<LogLine> recharge() {
        Step step = new Step();
        advance(step);
        EnergyDef energy = content.getBalance().getEnergy();
        if (pay(Map.of("batteries", 1), step)) {
            reserve = Math.min(reserveCapacity(), reserve + rechargePerBattery());
            double saved = reserve;
            step.radioWrites.add(() -> baseRepository.saveValue(KEY_RESERVE, saved));
            step.system(content.getTerminal().getBase().getReserveRecharged(), Map.of(), null);
        }
        return step.commit();
    }

    /** Starts a workshop job if there is a free slot and the inputs are there. */
    @WorkerThread
    public List<LogLine> craft(String recipeId) {
        Step step = new Step();
        advance(step);
        RecipeDef recipe = content.recipe(recipeId);
        if (craftJobs.size() >= moduleLevel("workshop")) {
            step.system(content.getTerminal().getBase().getCraftNoSlot(), Map.of(), null);
        } else if (moduleLevel("workshop") >= recipe.getMinWorkshopLevel() && pay(recipe.getInputs(), step)) {
            CraftJob job = CraftJob.start(recipeId, step.now);
            craftJobs.add(job);
            step.radioWrites.add(() -> baseRepository.save(job));
            Map<String, String> vars = new HashMap<>();
            vars.put("item", recipe.getName());
            step.system(content.getTerminal().getBase().getCraftStarted(), vars, null);
        }
        return step.commit();
    }

    /** Puts a piece from stock on a scout, in its slot. Whatever was there goes back to stock. */
    @WorkerThread
    public List<LogLine> equip(String scoutId, String pieceId) {
        Step step = new Step();
        advance(step);
        Scout scout = scouts.get(scoutId);
        com.estatica.domain.bunker.GearPiece next = pieceId != null ? gear.get(pieceId) : null;
        if (scout == null || !scout.isAlive() || scout.getStatus() != ScoutStatus.IDLE
                || next == null || !next.isInBunker()) {
            return step.commit();
        }
        rules.equip(scout, next);
        Map<String, String> vars = new HashMap<>();
        vars.put("name", scout.getName());
        vars.put("gear", gearName(next));
        step.system(content.getTerminal().getBase().getEquipped(), vars, scout);
        return step.commit();
    }

    /** Calls someone home from the field (and whoever is with them). */
    @WorkerThread
    public List<LogLine> recall(String scoutId) {
        Step step = new Step();
        advance(step);
        for (Expedition e : active.values()) {
            if (e.involves(scoutId)) {
                Scout lead = scouts.get(e.getScoutId());
                List<LogLine> said = engine.recall(e, lead, step.now);
                if (!said.isEmpty()) {
                    step.expeditions.add(e);
                    step.into(said, LogLine.expeditionThread(e.getId()));
                    ExpeditionEngine.Party team = party(e);
                    Map<String, String> vars = new HashMap<>();
                    vars.put("who", lead.getName() + (team.partner != null ? " + " + team.partner.getName() : ""));
                    step.lines.add(LogLine.draft(step.now, LogChannel.SYSTEM, null,
                            TextTemplate.format(content.getTerminal().getExpedition().getSummaryRecalled(), vars),
                            lead.getId()).withThread(LogLine.summaryThread(e.getId())));
                }
                break;
            }
        }
        return step.commit();
    }

    /** Takes off whatever a scout wears in a slot. */
    @WorkerThread
    public List<LogLine> unequip(String scoutId, String slot) {
        Step step = new Step();
        advance(step);
        Scout scout = scouts.get(scoutId);
        if (scout == null || !scout.isAlive() || scout.getStatus() != ScoutStatus.IDLE) {
            return step.commit();
        }
        com.estatica.domain.bunker.GearPiece old = rules.unequip(scout, GearDef.Slot.valueOf(slot));
        if (old != null) {
            Map<String, String> vars = new HashMap<>();
            vars.put("name", scout.getName());
            vars.put("gear", gearName(old));
            step.system(content.getTerminal().getBase().getUnequipped(), vars, scout);
        }
        return step.commit();
    }

    /** Fixes a worn or broken piece, in stock or on someone at home. */
    @WorkerThread
    public List<LogLine> repairGear(String pieceId) {
        Step step = new Step();
        advance(step);
        com.estatica.domain.bunker.GearPiece piece = gear.get(pieceId);
        if (piece == null || piece.getCondition() >= com.estatica.domain.bunker.GearPiece.MAX_CONDITION || !atHome(piece)
                || !pay(gearRules.repairCost(piece), step)) {
            return step.commit();
        }
        piece.repair();
        Map<String, String> vars = new HashMap<>();
        vars.put("gear", gearName(piece));
        step.system(content.getTerminal().getBase().getGearRepaired(), vars, null);
        return step.commit();
    }

    /** In stock, or on someone who is in the bunker right now. */
    private boolean atHome(com.estatica.domain.bunker.GearPiece piece) {
        if (piece.isInBunker()) {
            return true;
        }
        Scout wearer = wearerOf(piece);
        return wearer != null && wearer.isAlive() && wearer.getStatus() == ScoutStatus.IDLE;
    }

    private Scout wearerOf(com.estatica.domain.bunker.GearPiece piece) {
        for (Scout s : scouts.values()) {
            if (s.getLife().getEquipped().contains(piece)) {
                return s;
            }
        }
        return null;
    }

    private String gearName(com.estatica.domain.bunker.GearPiece piece) {
        return content.gear(piece.getGearId()).getName().toLowerCase(Locale.ROOT);
    }

    private String gearLabel(com.estatica.domain.bunker.GearPiece piece) {
        TerminalText.BaseText text = content.getTerminal().getBase();
        Map<String, String> vars = new HashMap<>();
        vars.put("gear", content.gear(piece.getGearId()).getName());
        vars.put("rarity", text.getRarities().getOrDefault(piece.getRarity().name(), piece.getRarity().name()));
        vars.put("condition", String.valueOf(piece.getCondition()));
        return TextTemplate.format(piece.isBroken() ? text.getGearLabelBroken() : text.getGearLabel(), vars);
    }

    /** Wear on the way back, and sometimes something useful found on the road. */
    private void gearAfterTrip(Scout scout, String zoneId, Step step) {
        TerminalText.BaseText text = content.getTerminal().getBase();
        for (com.estatica.domain.bunker.GearPiece piece : rules.working(scout)) {
            int before = gearRules.wear(piece, scout.getInjury() != com.estatica.domain.scouts.Injury.NONE);
            Map<String, String> vars = new HashMap<>();
            vars.put("name", scout.getName());
            vars.put("gear", gearName(piece));
            vars.put("condition", String.valueOf(piece.getCondition()));
            if (piece.isBroken()) {
                step.system(text.getGearBroken(), vars, scout);
            } else if (gearRules.isWorn(piece) && before > content.getBalance().getGear().getWornAt()) {
                step.system(text.getGearWorn(), vars, scout);
            }
        }
        int band = content.getBalance().bandIndex(content.zone(zoneId).getBand());
        com.estatica.domain.bunker.GearPiece found = gearRules.find(band, random);
        if (found != null) {
            gear.put(found.getId(), found);
            Map<String, String> vars = new HashMap<>();
            vars.put("name", scout.getName());
            vars.put("gear", gearLabel(found));
            step.system(text.getGearFound(), vars, scout);
        }
    }

    /** Loads every piece and puts the worn ones back on their wearers. */
    private void loadGear() {
        for (com.estatica.domain.bunker.GearPiece piece : bunkerRepository.gear()) {
            gear.put(piece.getId(), piece);
            for (Scout s : scouts.values()) {
                if (piece.getLocation().equals(com.estatica.domain.bunker.GearPiece.carriedBy(s.getId()))) {
                    s.getLife().wear(content.gear(piece.getGearId()).getSlot(), piece);
                }
            }
        }
    }

    private SessionSnapshot.BaseView baseView(long now) {
        EnergyDef energy = content.getBalance().getEnergy();
        PowerGrid.State g = grid(now);
        List<SessionSnapshot.ConsumerView> consumers = new ArrayList<>();
        for (EnergyDef.Consumer c : energy.getConsumers()) {
            consumers.add(new SessionSnapshot.ConsumerView(c.getId(), c.getName(), c.getDescription(),
                    power.getOrDefault(c.getId(), c.getStart()), g.units(c.getId()), c.getMax()));
        }
        List<SessionSnapshot.ModuleView> modules = new ArrayList<>();
        for (ModuleDef m : content.getBalance().getModules()) {
            int level = moduleLevel(m.getId());
            Map<String, Integer> cost = m.costFrom(level);
            modules.add(new SessionSnapshot.ModuleView(m.getId(), m.getName(), m.getDescription(), level,
                    m.getMaxLevel(), cost != null ? describe(cost) : null, cost != null && canAfford(cost),
                    m.levelText(level), level < m.getMaxLevel() ? m.levelText(level + 1) : null));
        }
        double speed = workshopSpeed(g);
        List<SessionSnapshot.JobView> jobs = new ArrayList<>();
        for (CraftJob job : craftJobs) {
            RecipeDef recipe = content.recipe(job.getRecipeId());
            double left = job.minutesLeft(recipe.getMinutes(), speed);
            jobs.add(new SessionSnapshot.JobView(recipe.getName(),
                    (int) Math.round(100 * job.getProgressMinutes() / recipe.getMinutes()),
                    left < 0 ? -1 : (int) Math.ceil(left)));
        }
        List<SessionSnapshot.RecipeView> recipes = new ArrayList<>();
        for (RecipeDef r : content.getRecipes().values()) {
            int needs = moduleLevel("workshop") >= r.getMinWorkshopLevel() ? 0 : r.getMinWorkshopLevel();
            recipes.add(new SessionSnapshot.RecipeView(r.getId(), r.getName(), describe(r.getInputs()),
                    (int) Math.ceil(r.getMinutes()), canAfford(r.getInputs()), needs));
        }
        List<Scout> guards = raids.guards(scouts.values());
        List<String> guardNames = new ArrayList<>();
        for (Scout s : guards) {
            guardNames.add(s.getName());
        }
        List<SessionSnapshot.ItemView> gearStock = new ArrayList<>();
        List<SessionSnapshot.GearView> gearViews = new ArrayList<>();
        List<com.estatica.domain.bunker.GearPiece> pieces = new ArrayList<>(gear.values());
        pieces.sort(Comparator.comparing(com.estatica.domain.bunker.GearPiece::getGearId).thenComparing(p -> -p.getRarity().ordinal()));
        for (com.estatica.domain.bunker.GearPiece piece : pieces) {
            if (piece.isGone() || !atHome(piece) && !piece.getLocation().startsWith("scout:")) {
                continue;
            }
            String label = gearLabel(piece);
            if (piece.isInBunker()) {
                GearDef def = content.gear(piece.getGearId());
                gearStock.add(new SessionSnapshot.ItemView(piece.getId(), label, def.getDescription(),
                        def.getSlot().name()));
            }
            Scout wearer = wearerOf(piece);
            String holder = wearer != null ? wearer.getName() : null;
            boolean damaged = piece.getCondition() < com.estatica.domain.bunker.GearPiece.MAX_CONDITION;
            Map<String, Integer> cost = gearRules.repairCost(piece);
            gearViews.add(new SessionSnapshot.GearView(piece.getId(), label, holder,
                    damaged && atHome(piece) ? describe(cost) : null, canAfford(cost)));
        }
        SessionSnapshot.BaseView baseView = new SessionSnapshot.BaseView(goalViews(now), !g.cut.isEmpty(), energy.watts(moduleLevel("generator")), g.available, g.used,
                events.blackoutWattsAt(now) > 0, g.night, g.filtersShort, consumers,
                (int) Math.floor(reserve), (int) reserveCapacity(),
                resources.getOrDefault("batteries", 0) > 0, modules, moduleLevel("workshop"), jobs, recipes,
                guardNames, raids.defense(guards) + (int) moduleBonus("barricades"), gearStock, aliveScouts(), rosterCap(), gearViews);
        baseView.rechargePer = (int) Math.round(rechargePerBattery());
        baseView.batteries = resources.getOrDefault("batteries", 0);
        return baseView;
    }

    // ---- Story: personal missions, La Voz, goals (GDD §5.2, §8, §9) ----

    private boolean personalAvailable(StoryDef.PersonalMission m, Scout s) {
        return s.isAlive() && m.isFor(s.getTemplateId(), content.persona(s))
                && s.getLife().hasFlag(m.offeredFlag()) && !s.getLife().hasFlag(m.doneFlag());
    }

    private void completePersonal(StoryDef.PersonalMission m, Scout scout, Step step) {
        if (m == null || scout.getLife().hasFlag(m.doneFlag())) {
            return;
        }
        scout.getLife().addFlag(m.doneFlag());
        com.estatica.domain.scouts.DialogueOutcomes.applyBond(content, rules, scout, m.getRewardBond(), step.now, step.lines);
        rules.addStress(scout, -m.getRewardStressRelief());
        if (m.getRewardTrait() != null && rules.addTrait(scout, m.getRewardTrait())) {
            com.estatica.domain.scouts.DialogueOutcomes.announceTrait(content, scout, m.getRewardTrait(),
                    content.getTerminal().getBunker().getTraitGained(), step.now, step.lines);
        }
        if (m.getMemory() != null) {
            step.memories.add(Memory.create(scout.getId(), Memory.Type.EVENT, m.getMemory(), m.getId(), step.now));
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("name", scout.getName());
        vars.put("title", m.getTitle());
        for (String line : m.getDoneLines()) {
            step.lines.add(LogLine.draft(step.now, LogChannel.RADIO, scout.getName(),
                    VoiceRenderer.render(content.voice(scout.getVoiceId()), line, vars), scout.getId()));
        }
        step.system(content.getTerminal().getStory().getPersonalDone(), vars, scout);
    }

    /** A La Voz fragment was found. When a chapter is complete, La Voz speaks (GDD §8). */
    private void foundPlot(String fragmentId, Step step) {
        StoryDef.Chapter chapter = content.getStory().chapterOf(fragmentId);
        if (chapter == null || !gameFlags.add("plot:" + fragmentId)) {
            return;
        }
        step.radioWrites.add(() -> radioRepository.addFlag("plot:" + fragmentId, step.now));
        int found = 0;
        for (StoryDef.Fragment f : chapter.getFragments()) {
            found += gameFlags.contains("plot:" + f.getId()) ? 1 : 0;
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("title", chapter.getTitle());
        vars.put("found", String.valueOf(found));
        vars.put("total", String.valueOf(chapter.getFragments().size()));
        step.system(content.getTerminal().getStory().getPlotFound(), vars, null);
        if (found == chapter.getFragments().size() && gameFlags.add("chapter:" + chapter.getId())) {
            step.radioWrites.add(() -> radioRepository.addFlag("chapter:" + chapter.getId(), step.now));
            String speaker = content.getTerminal().getStory().getVoiceSpeaker();
            for (String line : chapter.getFinale()) {
                step.lines.add(LogLine.draft(step.now, LogChannel.RADIO, speaker, line));
            }
            step.lines.add(LogLine.draft(step.now, LogChannel.ALERT, null, chapter.getClosing()));
        }
    }

    private long localDay(long now) {
        return clock.localEpochDay(now);
    }

    /** Monday-based week index (epoch day 0 was a Thursday). */
    private static long week(long day) {
        return Math.floorDiv(day + 3, 7);
    }

    private double value(String key) {
        return baseValues.getOrDefault(key, 0.0);
    }

    private void setValue(String key, double v, Step step) {
        baseValues.put(key, v);
        step.radioWrites.add(() -> baseRepository.saveValue(key, v));
    }

    /** Counts towards daily and weekly goals and pays out those just completed (GDD §9). */
    private void count(String counter, Step step) {
        long day = localDay(step.now);
        String dayKey = "obj:d:" + day + ":" + counter;
        String weekKey = "obj:w:" + week(day) + ":" + counter;
        setValue(dayKey, value(dayKey) + 1, step);
        setValue(weekKey, value(weekKey) + 1, step);
        StoryDef.Objectives objectives = content.getStory().getObjectives();
        for (StoryDef.Goal goal : objectives.getDaily()) {
            checkGoal(goal, dayKey, "done:d:" + day + ":" + goal.getId(), counter, step);
        }
        StoryDef.Goal weekly = objectives.getWeekly();
        checkGoal(weekly, weekKey, "done:w:" + week(day) + ":" + weekly.getId(), counter, step);
    }

    private void checkGoal(StoryDef.Goal goal, String counterKey, String doneKey, String counter, Step step) {
        if (!goal.getCounter().equals(counter) || value(doneKey) > 0 || value(counterKey) < goal.getTarget()) {
            return;
        }
        setValue(doneKey, 1, step);
        TerminalText.StoryText text = content.getTerminal().getStory();
        Map<String, String> vars = new HashMap<>();
        vars.put("label", goal.getLabel());
        step.system(text.getGoalDone(), vars, null);
        if (!goal.getReward().isEmpty()) {
            goal.getReward().forEach((k, v) -> step.resourceDelta.merge(k, v, Integer::sum));
            vars.put("loot", describe(goal.getReward()));
            step.system(text.getGoalReward(), vars, null);
        }
    }

    private List<SessionSnapshot.GoalView> goalViews(long now) {
        long day = localDay(now);
        StoryDef.Objectives objectives = content.getStory().getObjectives();
        List<SessionSnapshot.GoalView> out = new ArrayList<>();
        for (StoryDef.Goal g : objectives.getDaily()) {
            SessionSnapshot.GoalView view = new SessionSnapshot.GoalView(g.getLabel(),
                    (int) Math.min(g.getTarget(), value("obj:d:" + day + ":" + g.getCounter())), g.getTarget(), false);
            view.reward = describe(g.getReward());
            out.add(view);
        }
        StoryDef.Goal w = objectives.getWeekly();
        SessionSnapshot.GoalView weekly = new SessionSnapshot.GoalView(w.getLabel(),
                (int) Math.min(w.getTarget(), value("obj:w:" + week(day) + ":" + w.getCounter())), w.getTarget(), true);
        weekly.reward = describe(w.getReward());
        out.add(weekly);
        return out;
    }

    /** [HABLAR]: the operator starts a conversation (GDD §5.3), with a cooldown per scout. */
    @WorkerThread
    public List<LogLine> talk(String scoutId) {
        Step step = new Step();
        advance(step);
        Scout scout = scouts.get(scoutId);
        if (scout == null || !scout.isAlive() || scout.getStatus() != ScoutStatus.IDLE
                || hasPendingConversation(scout)) {
            return step.commit();
        }
        long readyAt = talkReadyAt(scout);
        if (step.now < readyAt) {
            Map<String, String> vars = new HashMap<>();
            vars.put("name", scout.getName());
            vars.put("time", ElapsedFormatter.format(readyAt - step.now, content.getTerminal().getElapsedUnits()));
            step.system(content.getTerminal().getBunker().getTalkCooldown(), vars, scout);
            return step.commit();
        }
        DialogueDef dialogue = dialogues.pick(scout, false, scouts.values(), random, step.now);
        if (dialogue != null) {
            step.into(dialogues.open(scout, dialogue, false, scouts.values(), random, step.now),
                    LogLine.chatThread(scout.getId()));
            scout.getLife().setLastTalkAtMillis(step.now);
            Conversation c = Conversation.start(scout.getId(), dialogue.getId(), step.now, false);
            conversations.put(c.getId(), c);
            step.conversationsChanged.add(c);
        }
        return step.commit();
    }

    /** The operator picks an answer in a conversation. */
    @WorkerThread
    public List<LogLine> answer(String conversationId, int choice) {
        Step step = new Step();
        advance(step);
        Conversation c = conversations.get(conversationId);
        if (c != null && c.isPending()) {
            Scout scout = scouts.get(c.getScoutId());
            DialogueDef dialogue = content.dialogue(c.getDialogueId());
            Set<String> before = new java.util.HashSet<>(scout.getLife().getFlags());
            DialogueEngine.Answer answer = dialogues.answer(scout, dialogue, choice, scouts.values(), step.now);
            step.into(answer.lines, LogLine.chatThread(scout.getId()));
            for (StoryDef.PersonalMission m : content.getStory().getPersonalMissions()) {
                if (!before.contains(m.offeredFlag()) && scout.getLife().hasFlag(m.offeredFlag())) {
                    Map<String, String> vars = new HashMap<>();
                    vars.put("name", scout.getName());
                    vars.put("title", m.getTitle());
                    step.system(content.getTerminal().getStory().getPersonalAvailable(), vars, scout);
                }
            }
            step.memories.addAll(answer.memories);
            scout.getLife().setLastTalkAtMillis(step.now);
            c.setStatus(Conversation.Status.ANSWERED);
            step.conversationsChanged.add(c);
        }
        return step.commit();
    }

    /** [REGALAR]: gives a personal object from the bunker. The scout carries it from then on. */
    @WorkerThread
    public List<LogLine> gift(String scoutId, String itemInstanceId) {
        Step step = new Step();
        advance(step);
        Scout scout = scouts.get(scoutId);
        ItemInstance item = items.get(itemInstanceId);
        if (scout != null && scout.isAlive() && scout.getStatus() == ScoutStatus.IDLE
                && item != null && item.isInBunker()) {
            GiftRules.Result result = gifts.give(scout, content.item(item.getItemId()), random, step.now);
            step.into(result.lines, LogLine.chatThread(scout.getId()));
            step.memories.addAll(result.memories);
            item.moveTo(ItemInstance.carriedBy(scout.getId()));
            scout.getLife().setLastTalkAtMillis(step.now);
        }
        return step.commit();
    }

    /** [TAREA]: assigns a bunker task, or none ({@code taskId} null). */
    @WorkerThread
    public List<LogLine> assignTask(String scoutId, String taskId) {
        Step step = new Step();
        advance(step);
        Scout scout = scouts.get(scoutId);
        if (scout != null && scout.isAlive()) {
            scout.getLife().setTaskId(taskId);
            Map<String, String> vars = new HashMap<>();
            vars.put("name", scout.getName());
            vars.put("task", taskId != null ? content.task(taskId).getName()
                    : content.getTerminal().getBunker().getNoTask());
            step.system(content.getTerminal().getBunker().getTaskAssigned(), vars, scout);
        }
        return step.commit();
    }

    private long talkReadyAt(Scout scout) {
        return scout.getLife().getLastTalkAtMillis()
                + content.getBalance().getBond().getTalkCooldownMinutes() * 60_000L;
    }

    // ---- Notifications ----

    /** A notification to post at a given time if the app stays closed. */
    public static final class ScheduledNotice {
        public final String key;
        public final long atMillis;
        public final String title;
        public final String text;

        ScheduledNotice(String key, long atMillis, String title, String text) {
            this.key = key;
            this.atMillis = atMillis;
            this.title = title;
            this.text = text;
        }
    }

    /**
     * Next notable moment of each expedition, already worded in the scout's voice. Read-only:
     * nothing is simulated, so the worker that posts these never touches game state.
     */
    @WorkerThread
    public List<ScheduledNotice> notices() {
        List<ScheduledNotice> out = new ArrayList<>();
        for (Expedition e : active.values()) {
            ExpeditionEngine.Notice notice = engine.nextNotice(e);
            if (notice == null) {
                continue;
            }
            Scout scout = scouts.get(e.getScoutId());
            Map<String, String> vars = new HashMap<>();
            vars.put("name", scout.getName());
            String template = notice.type == ExpeditionEngine.Notice.Type.SITUATION
                    ? content.event(notice.eventId).getIntro().get(0)
                    : content.getTerminal().getExpedition().getNotifyReturned();
            out.add(new ScheduledNotice(e.getId(), notice.atMillis, scout.getName(),
                    VoiceRenderer.render(content.voice(scout.getVoiceId()), template, vars)));
        }
        return out;
    }

    // ---- Read models ----

    @WorkerThread
    public SessionSnapshot snapshot() {
        TerminalText.BunkerText text = content.getTerminal().getBunker();
        Map<String, Expedition> byScout = new HashMap<>();
        for (Expedition e : active.values()) {
            byScout.put(e.getScoutId(), e);
            if (e.getPartnerId() != null) {
                byScout.put(e.getPartnerId(), e);
            }
        }
        boolean hasInventory = false;
        for (ItemInstance item : items.values()) {
            hasInventory |= item.isInBunker();
        }
        long now = clock.nowMillis();
        Balance.Bond bond = content.getBalance().getBond();

        List<SessionSnapshot.ScoutView> scoutViews = new ArrayList<>();
        for (Scout s : scouts.values()) {
            SessionSnapshot.ScoutView.Builder b = new SessionSnapshot.ScoutView.Builder();
            b.id = s.getId();
            b.name = s.getName();
            ScoutTemplate template = content.persona(s);
            b.bio = template != null ? template.getBio() : null;
            b.status = content.getTerminal().getScoutStatus().get(s.getStatus().name());
            b.injury = content.getTerminal().getInjuries().get(s.getInjury().name());
            b.dead = !s.isAlive();
            b.stress = s.getStress();
            b.trust = s.getTrust();
            b.bondLevel = rules.bondLevel(s);
            b.bondLevels = bond.getLevelThresholds().length;
            b.stats = new ArrayList<>();
            for (StatType type : StatType.values()) {
                Stat stat = s.stat(type);
                b.stats.add(new SessionSnapshot.StatView(
                        content.getTerminal().getStatNames().get(type.key()), stat.getValue(), stat.getCeiling()));
            }
            b.traits = new ArrayList<>();
            for (ScoutTrait t : s.getTraits()) {
                if (t.isVisible()) {
                    b.traits.add(content.trait(t.getTraitId()).getName());
                }
            }
            String taskId = s.getLife().getTaskId();
            b.task = taskId != null ? content.task(taskId).getName() : text.getNoTask();
            b.likes = tastes(s, GiftRules.LIKES, text.getKnownLikes());
            b.dislikes = tastes(s, GiftRules.DISLIKES, text.getKnownDislikes());
            b.gifts = new ArrayList<>();
            for (ItemInstance item : items.values()) {
                if (item.getLocation().equals(ItemInstance.carriedBy(s.getId()))) {
                    b.gifts.add(content.item(item.getItemId()).getName());
                }
            }
            boolean home = s.isAlive() && s.getStatus() == ScoutStatus.IDLE;
            b.canDispatch = rules.canDispatch(s);
            b.canTalk = home && !hasPendingConversation(s) && now >= talkReadyAt(s);
            b.talkReadyAtMillis = home && !hasPendingConversation(s) && now < talkReadyAt(s) ? talkReadyAt(s) : 0;
            b.bondProgress = s.isAlive() ? rules.bondProgress(s) : 0;
            b.canGift = home && hasInventory;
            b.canAssignTask = s.isAlive();
            b.slots = new ArrayList<>();
            for (GearDef.Slot slot : GearDef.Slot.values()) {
                com.estatica.domain.bunker.GearPiece worn = s.getLife().getGear(slot);
                b.slots.add(new SessionSnapshot.SlotView(slot.name(),
                        content.getTerminal().getBase().getSlotNames().getOrDefault(slot.name(), slot.name()),
                        worn != null ? worn.getId() : null, worn != null ? gearLabel(worn) : null));
            }
            List<String> worn = new ArrayList<>();
            for (com.estatica.domain.bunker.GearPiece piece : s.getLife().getEquipped()) {
                worn.add(gearLabel(piece));
            }
            b.gear = worn.isEmpty() ? null : String.join(", ", worn);
            b.canEquip = home;
            Expedition out = byScout.get(s.getId());
            b.canRecall = s.isAlive() && out != null && !out.isAborted();
            b.hurt = s.getInjury() != com.estatica.domain.scouts.Injury.NONE;
            b.moodKey = rules.mood(s);
            b.mood = content.getTerminal().getMoods().get(b.moodKey);
            b.habits = new ArrayList<>();
            if (template != null) {
                for (ScoutTemplate.Habit habit : template.getHabits()) {
                    if (rules.bondLevel(s) >= habit.getMinBondLevel()) {
                        b.habits.add(habit.getText());
                    }
                }
            }
            b.relations = new ArrayList<>();
            b.relationKinds = new HashMap<>();
            for (Scout other : scouts.values()) {
                String relation = other == s || !other.isAlive() || !s.isAlive()
                        ? null : bunker.relationship(affinities, s, other);
                if (relation != null) {
                    b.relationKinds.put(other.getId(), relation);
                    Map<String, String> vars = new HashMap<>();
                    vars.put("other", other.getName());
                    TerminalText.StoryText st = content.getTerminal().getStory();
                    String relationText = "inseparable".equals(relation) ? st.getInseparable()
                            : "friends".equals(relation) ? st.getFriends() : st.getRivals();
                    b.relations.add(TextTemplate.format(relationText, vars));
                }
            }
            Expedition away = byScout.get(s.getId());
            b.awayAt = away != null && s.getStatus() == ScoutStatus.AWAY
                    ? content.zone(away.getZoneId()).getName() : null;
            scoutViews.add(new SessionSnapshot.ScoutView(b));
        }

        List<SessionSnapshot.PendingDecision> orders = new ArrayList<>();
        long nextWake = -1;
        for (Expedition e : active.values()) {
            long at = engine.nextEventAt(e);
            if (at >= 0 && (nextWake < 0 || at < nextWake)) {
                nextWake = at;
            }
            Scout lead = scouts.get(e.getScoutId());
            ExpeditionEngine.Party team = party(e);
            List<String> labels = engine.pendingOptions(e, lead, team);
            if (!labels.isEmpty()) {
                Map<String, String> hintLabels = content.getTerminal().getExpedition().getHintLabels();
                List<String> hints = new ArrayList<>();
                for (String key : engine.pendingHints(e, lead, team)) {
                    hints.add(key != null ? hintLabels.get(key) : null);
                }
                String who = lead.getName() + (team.partner != null ? " + " + team.partner.getName() : "");
                orders.add(new SessionSnapshot.PendingDecision(SessionSnapshot.PendingDecision.Kind.ORDER,
                        e.getId(), who, labels, engine.deadline(e), content.zone(e.getZoneId()).getName(),
                        LogLine.expeditionThread(e.getId()), engine.situationText(e, lead), hints));
            }
        }
        orders.sort(Comparator.comparingLong(p -> p.deadlineMillis));
        List<SessionSnapshot.PendingDecision> pending = new ArrayList<>(orders);
        TerminalText.BunkerText bunkerText = content.getTerminal().getBunker();
        for (Treatment t : treatments(now)) {
            if (isEager(t.healer) || resources.getOrDefault("medicine", 0) < t.cost) {
                continue; // they'll just do it, or there's nothing to do it with
            }
            Map<String, String> vars = treatVars(t);
            VoiceDef voice = content.voice(t.healer.getVoiceId());
            pending.add(new SessionSnapshot.PendingDecision(SessionSnapshot.PendingDecision.Kind.TREAT,
                    t.id(), t.healer.getName(),
                    List.of(TextTemplate.format(bunkerText.getTreatYes(), vars), bunkerText.getTreatNo()),
                    -1, null, BUNKER_CHAT,
                    List.of(com.estatica.domain.scouts.VoiceRenderer.render(voice, bunkerText.getTreatAsk(), vars)),
                    List.of()));
        }
        long expireAfter = bond.getInitiatedExpireHours() * HOUR;
        for (Conversation c : conversations.values()) {
            if (!c.isPending()) {
                continue;
            }
            Scout scout = scouts.get(c.getScoutId());
            Scout subject = dialogues.subjectOf(scout, content.dialogue(c.getDialogueId()), scouts.values());
            Map<String, String> vars = new HashMap<>();
            vars.put("name", scout.getName());
            if (subject != null) {
                vars.put("dead", subject.getName());
                vars.put("other", subject.getName());
            }
            List<String> labels = new ArrayList<>();
            for (DialogueDef.Choice choice : content.dialogue(c.getDialogueId()).getChoices()) {
                labels.add(TextTemplate.format(choice.getLabel(), vars));
            }
            long expiresAt = c.isInitiated() ? c.getStartedAtMillis() + expireAfter : -1;
            pending.add(new SessionSnapshot.PendingDecision(SessionSnapshot.PendingDecision.Kind.TALK,
                    c.getId(), scout.getName(), labels, expiresAt, null, LogLine.chatThread(scout.getId()),
                    dialogues.linesOf(scout, content.dialogue(c.getDialogueId()), scouts.values()), List.of()));
            if (expiresAt >= 0 && (nextWake < 0 || expiresAt < nextWake)) {
                nextWake = expiresAt;
            }
        }

        List<SessionSnapshot.ZoneView> zones = new ArrayList<>();
        for (ZoneDef z : content.getZones().values()) {
            if (!canReach(z)) {
                continue;
            }
            Relay relay = relays.get(z.getId());
            SessionSnapshot.ZoneView zoneView = new SessionSnapshot.ZoneView(z.getId(), z.getName(), z.getDescription(),
                    z.getDanger(), z.getDurationMinutes()[0], z.getDurationMinutes()[1],
                    content.getBalance().band(z.getBand()).getName(),
                    relay != null ? (int) Math.round(relay.getCondition()) : -1,
                    intel.containsKey(z.getId()), (int) Math.round(depletion(z.getId(), now) * 100));
            zoneView.refillIn = refillIn(z.getId(), now);
            zones.add(zoneView);
        }

        TerminalText.RadioText radioText = content.getTerminal().getRadio();
        List<SessionSnapshot.MissionView> missions = new ArrayList<>();
        for (Map.Entry<String, String> target : rescueTargets.entrySet()) {
            ZoneDef z = content.zone(target.getValue());
            if (canReach(z)) {
                Map<String, String> vars = new HashMap<>();
                ScoutTemplate waiting = rescuePersona(target.getKey());
                vars.put("name", waiting != null ? waiting.getName() : "?");
                missions.add(new SessionSnapshot.MissionView(Mission.Type.RESCUE.name(), z.getId(),
                        target.getKey(), TextTemplate.format(radioText.getMissionRescue(), vars), z.getName()));
            }
        }
        for (Scout s : scouts.values()) {
            for (StoryDef.PersonalMission m : content.getStory().getPersonalMissions()) {
                ZoneDef z = content.zone(m.getZone());
                if (personalAvailable(m, s) && canReach(z)) {
                    Map<String, String> vars = new HashMap<>();
                    vars.put("title", m.getTitle());
                    missions.add(new SessionSnapshot.MissionView(Mission.Type.PERSONAL.name(), z.getId(), m.getId(),
                            TextTemplate.format(content.getTerminal().getStory().getMissionPersonal(), vars),
                            z.getName(), s.getId()));
                }
            }
        }
        boolean hasKit = resources.getOrDefault("relay_kit", 0) > 0;
        for (ZoneDef z : content.getZones().values()) {
            if (!canReach(z)) {
                continue;
            }
            Relay relay = relays.get(z.getId());
            if (relay == null && hasKit) {
                missions.add(new SessionSnapshot.MissionView(Mission.Type.INSTALL_RELAY.name(), z.getId(),
                        null, radioText.getMissionInstall(), z.getName()));
            } else if (relay != null && relay.getCondition() < 70) {
                missions.add(new SessionSnapshot.MissionView(Mission.Type.MAINTAIN_RELAY.name(), z.getId(),
                        null, radioText.getMissionMaintain(), z.getName()));
            }
        }

        List<SessionSnapshot.ResourceView> resourceViews = new ArrayList<>();
        for (ResourceDef r : content.getResources().values()) {
            resourceViews.add(new SessionSnapshot.ResourceView(r.getName(), resources.getOrDefault(r.getId(), 0),
                    r.getShortName(), r.getIcon()));
        }

        List<SessionSnapshot.ItemView> inventory = new ArrayList<>();
        for (ItemInstance item : items.values()) {
            if (item.isInBunker()) {
                ItemDef def = content.item(item.getItemId());
                inventory.add(new SessionSnapshot.ItemView(item.getId(), def.getName(), def.getDescription()));
            }
        }

        List<SessionSnapshot.TaskView> tasks = new ArrayList<>();
        tasks.add(new SessionSnapshot.TaskView(null, text.getNoTask(), ""));
        for (TaskDef t : content.getTasks().values()) {
            tasks.add(new SessionSnapshot.TaskView(t.getId(), t.getName(), t.getDescription()));
        }
        long baseWake = nextBaseEvent(now);
        if (baseWake >= 0 && (nextWake < 0 || baseWake < nextWake)) {
            nextWake = baseWake;
        }
        List<SessionSnapshot.ExpeditionView> expeditionViews = new ArrayList<>();
        for (Expedition e : active.values()) {
            long end = engine.estimatedReturn(e, now);
            long span = Math.max(1, end - e.getStartedAtMillis());
            int percent = (int) Math.max(0, Math.min(99, 100 * (now - e.getStartedAtMillis()) / span));
            ExpeditionEngine.Party party = party(e);
            String who = scouts.get(e.getScoutId()).getName()
                    + (party.partner != null ? " + " + party.partner.getName() : "");
            expeditionViews.add(new SessionSnapshot.ExpeditionView(LogLine.expeditionThread(e.getId()), who,
                    content.zone(e.getZoneId()).getName(), percent, end, e.isAwaitingDecision(),
                    e.getPendingSinceMillis() != Expedition.NONE && !e.isAwaitingDecision()));
        }
        List<SessionSnapshot.DoneLog> doneLogs = new ArrayList<>();
        for (Iterator<Map.Entry<String, String[]>> it = doneTeams.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String[]> e = it.next();
            if (now - value(LOG_DONE + e.getKey()) >= LOG_CHIP_MILLIS || active.containsKey(e.getKey())) {
                it.remove();
                continue;
            }
            Scout lead = scouts.get(e.getValue()[0]);
            Scout partner = e.getValue()[1] != null ? scouts.get(e.getValue()[1]) : null;
            if (lead != null) {
                doneLogs.add(new SessionSnapshot.DoneLog(LogLine.expeditionThread(e.getKey()),
                        lead.getName() + (partner != null ? " + " + partner.getName() : "")));
            }
        }
        SessionSnapshot built = new SessionSnapshot(scoutViews, pending, zones, resourceViews, inventory, tasks, missions,
                radioView(), baseView(now), expeditionViews,
                food.daysLeft(mouths(), kitchenSaving(), resources.getOrDefault("rations", 0), foodCarryNow(now)),
                value(KEY_HUNGRY) > 0, mapView(), keyItemViews(), glossary(), weather.at(now).name(),
                content.getTerminal().getWeather().getNames().getOrDefault(weather.at(now).name(), ""),
                placeNames(), resourceNames(),
                content.getTerminal().getHighlight().getDanger(), content.getTerminal().getHighlight().getGood(),
                nextWake);
        built.doneLogs = doneLogs;
        return built;
    }

    private SessionSnapshot.RadioView radioView() {
        List<SessionSnapshot.BandView> bands = new ArrayList<>();
        for (BandDef band : content.getBalance().getBands()) {
            bands.add(new SessionSnapshot.BandView(band.getId(), band.getName(), isUnlocked(band), band::frequency));
        }
        List<SessionSnapshot.TransmissionView> pendingTransmissions = new ArrayList<>();
        for (Transmission t : transmissions.values()) {
            pendingTransmissions.add(transmissionView(t));
        }
        List<String> relayLines = new ArrayList<>();
        for (Relay relay : relays.values()) {
            relayLines.add(content.zone(relay.getZoneId()).getName() + " " + Math.round(relay.getCondition()) + "%");
        }
        return new SessionSnapshot.RadioView(bands, content.getBalance().getRadio().getDialMax(),
                pendingTransmissions, gameFlags.contains(FLAG_MORSE_TABLE),
                resources.getOrDefault("relay_kit", 0), relayLines, stationViews());
    }

    /** Stations you've heard at least once (they're in the Diary), with their timetable. */
    private List<SessionSnapshot.StationView> stationViews() {
        long now = clock.nowMillis();
        boolean night = content.getBalance().getEnergy().isNight(clock.localHour(now));
        TerminalText.RadioText text = content.getTerminal().getRadio();
        List<SessionSnapshot.StationView> out = new ArrayList<>();
        for (SignalsDef.Station st : content.getSignals().getStations()) {
            if (!lore.containsKey(st.getId())) {
                continue;
            }
            BandDef band = content.getBalance().band(st.getBand());
            Map<String, String> vars = new HashMap<>();
            String schedule;
            SignalsDef.Schedule s = st.getSchedule();
            if (s == null) {
                schedule = st.isNight() ? text.getScheduleNight() : text.getScheduleAlways();
            } else {
                long at = Math.round(s.getAtHour() * 60);
                vars.put("time", String.format(Locale.ROOT, "%02d:%02d", at / 60 % 24, at % 60));
                vars.put("length", ElapsedFormatter.format(s.getMinutes() * 60_000L, content.getTerminal().getElapsedUnits()));
                vars.put("hours", s.getEveryHours() == Math.rint(s.getEveryHours())
                        ? String.valueOf((long) s.getEveryHours()) : String.valueOf(s.getEveryHours()));
                schedule = TextTemplate.format(s.getEveryHours() >= 24 ? text.getScheduleDaily() : text.getScheduleEvery(), vars);
            }
            boolean onAir = isUnlocked(band) && (!st.isNight() || night) && Broadcasts.onAir(st, clock.localMillis(now));
            out.add(new SessionSnapshot.StationView(st.getId(), st.getName(), st.getBand(), st.getPosition(),
                    band.frequency(st.getPosition()) + " · " + band.getName(), schedule, onAir, st.hasBulletin()));
        }
        return out;
    }

    /**
     * The Diary (GDD §6.6): the fallen with their story and last transmission, La Voz
     * fragments, decoded transmissions and known zones.
     */
    @WorkerThread
    public List<SessionSnapshot.DiaryEntry> diary() {
        TerminalText.BunkerText text = content.getTerminal().getBunker();
        TerminalText.StoryText story = content.getTerminal().getStory();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.ENGLISH);
        List<SessionSnapshot.DiaryEntry> out = new ArrayList<>();

        out.add(SessionSnapshot.DiaryEntry.section(story.getDiaryFallen()));
        List<SessionSnapshot.DiaryEntry> fallen = new ArrayList<>();
        for (Scout s : scouts.values()) {
            if (s.isAlive()) {
                continue;
            }
            Map<String, String> vars = new HashMap<>();
            vars.put("date", dateFormat.format(new Date(s.getLife().getDiedAtMillis())));
            String zone = s.getLife().getDiedZoneId();
            vars.put("zone", zone != null ? content.zone(zone).getName() : "?");
            List<String> memories = new ArrayList<>();
            String lastWords = null;
            for (Memory m : memoryRepository.forScout(s.getId())) {
                if (m.getType() == Memory.Type.LAST_WORDS) {
                    lastWords = m.getText();
                } else if (m.getType() != Memory.Type.ORDER) {
                    memories.add(m.getText());
                }
            }
            ScoutTemplate template = content.persona(s);
            fallen.add(new SessionSnapshot.DiaryEntry(s.getName(), TextTemplate.format(text.getDiaryFell(), vars),
                    template != null ? template.getBio() : null, memories, lastWords));
        }
        fallen.sort(Comparator.comparing(e -> e.name));
        if (fallen.isEmpty()) {
            fallen.add(new SessionSnapshot.DiaryEntry(text.getDiaryEmpty(), null, null, List.of(), null));
        }
        out.addAll(fallen);

        out.add(SessionSnapshot.DiaryEntry.section(story.getDiaryVoice()));
        for (StoryDef.Chapter chapter : content.getStory().getChapters()) {
            List<String> fragments = new ArrayList<>();
            for (StoryDef.Fragment f : chapter.getFragments()) {
                fragments.add(gameFlags.contains("plot:" + f.getId()) ? f.getText() : story.getDiaryUnknownFragment());
            }
            String finale = gameFlags.contains("chapter:" + chapter.getId())
                    ? String.join("\n", chapter.getFinale()) : null;
            out.add(new SessionSnapshot.DiaryEntry(chapter.getTitle(), null, null, fragments, finale));
        }

        out.add(SessionSnapshot.DiaryEntry.section(story.getDiaryLore()));
        for (String line : lore.values()) {
            out.add(new SessionSnapshot.DiaryEntry(line, null, null, List.of(), null));
        }

        out.add(SessionSnapshot.DiaryEntry.section(story.getDiaryZones()));
        for (ZoneDef z : content.getZones().values()) {
            if (knownZones.contains(z.getId())) {
                out.add(new SessionSnapshot.DiaryEntry(z.getName(),
                        content.getBalance().band(z.getBand()).getName(), z.getDescription(), List.of(), null));
            }
        }
        return out;
    }

    private String tastes(Scout s, String prefix, String template) {
        List<String> names = new ArrayList<>();
        for (String flag : s.getLife().getFlags()) {
            if (flag.startsWith(prefix)) {
                ItemDef item = content.getItems().get(flag.substring(prefix.length()));
                if (item != null) {
                    names.add(item.getName().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("items", String.join(", ", names));
        return TextTemplate.format(template, vars);
    }
}
