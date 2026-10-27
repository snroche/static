package com.estatica.data.session;

import androidx.annotation.WorkerThread;

import com.estatica.core.GameClock;
import com.estatica.core.GameRandom;
import com.estatica.core.Ids;
import com.estatica.core.TextTemplate;
import com.estatica.data.content.ContentRepository;
import com.estatica.data.db.EstaticaDatabase;
import com.estatica.data.db.LinkSignalEntity;
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
import com.estatica.domain.content.RoomDef;
import com.estatica.domain.dialogue.Conversation;
import com.estatica.domain.dialogue.DialogueEngine;
import com.estatica.domain.expedition.Expedition;
import com.estatica.domain.expedition.ExpeditionEngine;
import com.estatica.domain.expedition.ExpeditionResult;
import com.estatica.domain.expedition.Mission;
import com.estatica.domain.link.LinkCodec;
import com.estatica.domain.link.LinkPacket;
import com.estatica.domain.link.LinkRules;
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
    private com.estatica.domain.scouts.RetrainRules retrainRules;
    private com.estatica.domain.bunker.MarketRules marketRules;
    private com.estatica.domain.bunker.WorkshopRules workshopRules;
    private BunkerRules bunker;
    private DialogueEngine dialogues;
    private com.estatica.domain.scouts.RomanceRules romance;
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
        retrainRules = new com.estatica.domain.scouts.RetrainRules(content.getBalance().getRetrain());
        workshopRules = new com.estatica.domain.bunker.WorkshopRules(content);
        bunker = new BunkerRules(content, rules);
        dialogues = new DialogueEngine(content, rules, new DialogueEngine.Relations() {
            @Override
            public Scout find(Scout scout, String kind) {
                for (Scout other : scouts.values()) {
                    if (other == scout || !other.isAlive()) {
                        continue;
                    }
                    if ("partner".equals(kind)) {
                        // Another of the operator's partners (jealousy), or the operator's
                        // partner as seen by someone else (gossip).
                        if (romance.isTogether(other)) {
                            return other;
                        }
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
        romance = new com.estatica.domain.scouts.RomanceRules(content, rules);
        dialogues.setRomance(romanceHooks());
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
        marketRules = new com.estatica.domain.bunker.MarketRules(content, state.getRngSeed());
        weather = new com.estatica.domain.world.WeatherRules(content.getBalance().getWeather(), state.getRngSeed());
        engine.setKeyOwnership(this::hasKey);
        engine.setPlotOpen(this::plotOpen);
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
        loadLinks();
        registerPronouns();
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
                ZoneDef visited = content.zone(expedition.getZoneId());
                if (!visited.isWilds() && !visited.isEndless()) {
                    pickOver(expedition.getZoneId(), step);
                }
                if (visited.getId().equals(content.getBalance().getMarket().getZone()) && !expedition.isAborted()) {
                    pickUp(scout, step);
                }
                maybeFindBlueprint(scout, visited, step);
                double tripHours = (step.now - expedition.getStartedAtMillis()) / (double) HOUR;
                gearAfterTrip(scout, expedition.getZoneId(), tripHours, step);
                List<Scout> team = party.everyone(scout);
                for (int i = 1; i < team.size(); i++) {
                    gearAfterTrip(team.get(i), expedition.getZoneId(), tripHours, step);
                }
                // Coming back together builds something, even between rivals.
                for (int i = 0; i < team.size(); i++) {
                    for (int j = i + 1; j < team.size(); j++) {
                        affinities.add(team.get(i).getId(), team.get(j).getId(),
                                content.getBalance().getPairs().getAffinityPerTrip());
                    }
                }
                finishMission(expedition, scout, result.isMissionCompleted(), step);
                meetWanderers(team, expedition.getZoneId(), step);
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
        deliverCouriers(step);
        maybeWorkOutBlueprint(step);
        maraBike(step);
        maybeStartConversation(step);
        maybeBark(step);
        maybeVignette(step);
        maybeOmen(step);
        catchBroadcasts(step);
        eagerTreatments(step);
        roomsTick(step);
        romanceTick(step);
        expireWanderers(step);
    }

    // ---- Romance with the operator ----

    private static final String ROM = "rom:";
    private static final String ROM_LAST = "romlast:";
    private static final String ROM_END = "romend:";

    private int romanceMeter(Scout s) {
        return (int) Math.round(value(ROM + s.getId()));
    }

    private boolean neglected(Scout s, long now) {
        double last = value(ROM_LAST + s.getId());
        return romance.isTogether(s) && last > 0
                && now - last > content.getBalance().getRomance().getNeglectHours() * HOUR;
    }

    /** Talking to a partner (any conversation) counts as attention. */
    private void attention(Scout s, Step step) {
        if (romance.isTogether(s)) {
            setValue(ROM_LAST + s.getId(), step.now, step);
        }
    }

    private List<Scout> partners() {
        List<Scout> out = new ArrayList<>();
        for (Scout s : scouts.values()) {
            if (s.isAlive() && romance.isTogether(s)) {
                out.add(s);
            }
        }
        return out;
    }

    private Map<String, String> romanceVars(Scout s) {
        Map<String, String> vars = new HashMap<>();
        vars.put("name", s.getName());
        return vars;
    }

    /** The dialogue engine's view of romance; steps write through setValue as usual. */
    private DialogueEngine.Romance romanceHooks() {
        return new DialogueEngine.Romance() {
            @Override
            public boolean adult(Scout s) {
                return romance.isAdult(s);
            }

            @Override
            public int meter(Scout s) {
                return romanceMeter(s);
            }

            @Override
            public boolean together(Scout s) {
                return romance.isTogether(s);
            }

            @Override
            public boolean ready(Scout s) {
                return romance.isReady(s);
            }

            @Override
            public int jealousy(Scout s) {
                return romance.jealousy(s);
            }

            @Override
            public boolean neglected(Scout s) {
                return GameSession.this.neglected(s, clock.nowMillis());
            }

            @Override
            public boolean coolingDown(Scout s) {
                double ended = value(ROM_END + s.getId());
                return ended > 0 && clock.nowMillis() - ended
                        < content.getBalance().getRomance().getCooldownHours() * HOUR;
            }

            @Override
            public boolean wasPartner(Scout dead) {
                return romance.isTogether(dead);
            }

            @Override
            public boolean operatorFemale() {
                return com.estatica.core.Pronouns.isOperatorFemale();
            }

            @Override
            public void add(Scout s, int delta, long now, List<LogLine> lines) {
                double before = value(ROM + s.getId());
                double change = delta > 0 ? delta * romance.gainMultiplier(s) : delta;
                double after = Math.max(0, Math.min(100, before + change));
                pendingValues.put(ROM + s.getId(), after);
                baseValues.put(ROM + s.getId(), after);
                if (romance.isTogether(s)) {
                    pendingValues.put(ROM_LAST + s.getId(), (double) now);
                    baseValues.put(ROM_LAST + s.getId(), (double) now);
                }
                int spark = content.getBalance().getRomance().getSparkAt();
                if (before < spark && after >= spark && !romance.isTogether(s)) {
                    lines.add(LogLine.draft(now, LogChannel.SYSTEM, null,
                            TextTemplate.format(content.getTerminal().getBunker().getRomanceSpark(), romanceVars(s)),
                            s.getId()));
                }
            }

            @Override
            public void start(Scout s, long now, List<LogLine> lines) {
                if (romance.isTogether(s)) {
                    return;
                }
                s.getLife().addFlag(com.estatica.domain.scouts.RomanceRules.FLAG);
                double meter = Math.max(value(ROM + s.getId()), 70);
                pendingValues.put(ROM + s.getId(), meter);
                baseValues.put(ROM + s.getId(), meter);
                pendingValues.put(ROM_LAST + s.getId(), (double) now);
                baseValues.put(ROM_LAST + s.getId(), (double) now);
                lines.add(LogLine.draft(now, LogChannel.SYSTEM, null,
                        TextTemplate.format(content.getTerminal().getBunker().getRomanceStart(), romanceVars(s)),
                        s.getId()));
            }

            @Override
            public void end(Scout s, long now, boolean byOperator, List<LogLine> lines) {
                endRomance(s, now, byOperator, lines);
            }

            @Override
            public void exclusive(Scout keep, long now, List<LogLine> lines) {
                for (Scout other : partners()) {
                    if (other != keep) {
                        endRomance(other, now, true, lines);
                    }
                }
            }
        };
    }

    /**
     * Values written outside a Step (from inside a dialogue answer); flushed by the next
     * commit along with everything else.
     */
    private final Map<String, Double> pendingValues = new HashMap<>();

    private void endRomance(Scout s, long now, boolean byOperator, List<LogLine> lines) {
        if (!romance.isTogether(s)) {
            return;
        }
        Balance.Romance config = content.getBalance().getRomance();
        s.getLife().removeFlag(com.estatica.domain.scouts.RomanceRules.FLAG);
        s.getLife().addFlag(com.estatica.domain.scouts.RomanceRules.EX_FLAG);
        double meter = Math.min(value(ROM + s.getId()), config.getAfterBreakup());
        pendingValues.put(ROM + s.getId(), meter);
        baseValues.put(ROM + s.getId(), meter);
        pendingValues.put(ROM_END + s.getId(), (double) now);
        baseValues.put(ROM_END + s.getId(), (double) now);
        rules.addStress(s, byOperator ? config.getBreakupStress() : config.getBreakupStress() / 2);
        rules.addBond(s, -config.getBreakupBond());
        TerminalText.BunkerText text = content.getTerminal().getBunker();
        lines.add(LogLine.draft(now, LogChannel.SYSTEM, null, TextTemplate.format(
                byOperator ? text.getRomanceEndYou() : text.getRomanceEndThem(), romanceVars(s)), s.getId()));
    }

    /** Neglect wears it down; enough of it (or no trust, or no bond) and they end it. Jealousy flares. */
    private void romanceTick(Step step) {
        double last = value("rom_at");
        setValue("rom_at", step.now, step);
        if (last <= 0) {
            return;
        }
        double hours = (step.now - last) / (double) HOUR;
        if (hours <= 0) {
            return;
        }
        Balance.Romance config = content.getBalance().getRomance();
        List<Scout> partners = partners();
        for (Scout p : partners) {
            if (neglected(p, step.now)) {
                setValue(ROM + p.getId(), Math.max(0, value(ROM + p.getId()) - config.getNeglectPerDay() * hours / 24), step);
            }
            boolean gone = value(ROM + p.getId()) < config.getEndBelow()
                    || rules.bondLevel(p) < config.getEndBelowBondLevel()
                    || p.getTrust() < config.getEndBelowTrust();
            if (gone) {
                endRomance(p, step.now, false, step.lines);
                continue;
            }
            if (partners.size() > 1 && p.getStatus() == ScoutStatus.IDLE) {
                double chance = romance.jealousy(p) / 100.0 * config.getJealousyPerHour()
                        * (partners.size() - 1) * hours;
                if (random.chance(Math.min(0.9, chance))) {
                    Scout rival = null;
                    for (Scout other : partners) {
                        if (other != p) {
                            rival = other;
                        }
                    }
                    rules.addStress(p, config.getJealousyStress());
                    setValue(ROM + p.getId(), Math.max(0, value(ROM + p.getId()) - config.getJealousyMeter()), step);
                    affinities.add(p.getId(), rival.getId(), -config.getJealousyAffinity());
                    List<String> pool = content.getTerminal().getBunker().getJealousLines();
                    if (!pool.isEmpty()) {
                        Map<String, String> vars = romanceVars(p);
                        vars.put("other", rival.getName());
                        com.estatica.domain.content.VoiceDef voice = content.voice(p.getVoiceId());
                        step.into(List.of(LogLine.draft(step.now, LogChannel.RADIO, p.getName(),
                                com.estatica.domain.scouts.VoiceRenderer.render(voice, random.pick(pool), vars),
                                p.getId())), BUNKER_CHAT);
                    }
                }
            }
        }
        flushRomanceValues(step);
    }

    /** "♥ together", "flirting", or null when there's nothing to say. */
    private String romanceLabel(Scout s) {
        if (!romance.isAdult(s)) {
            return null;
        }
        Map<String, String> stages = content.getTerminal().getBunker().getRomanceStages();
        int meter = romanceMeter(s);
        String key = romance.isTogether(s) ? (neglected(s, clock.nowMillis()) ? "neglected" : "together")
                : meter >= content.getBalance().getRomance().getSparkAt() ? "flirting"
                : meter >= 15 ? "spark" : null;
        return key == null ? null : stages.get(key);
    }

    /** Writes values the dialogue hooks set. */
    private void flushRomanceValues(Step step) {
        for (Map.Entry<String, Double> e : pendingValues.entrySet()) {
            setValue(e.getKey(), e.getValue(), step);
        }
        pendingValues.clear();
    }

    // ---- Rooms (restoration projects and room tasks) ----

    private static final String ROOM_OPEN = "room:";
    private static final String ROOM_WORK = "roomwork:";
    private static final String ROOM_PROJECT = "room_project";

    public boolean isRoomOpen(String roomId) {
        RoomDef room = content.getBalance().room(roomId);
        return room == null || room.isOpen() || value(ROOM_OPEN + roomId) > 0;
    }

    /** The room being restored, or null. */
    private RoomDef project() {
        int index = (int) value(ROOM_PROJECT) - 1;
        List<RoomDef> rooms = content.getBalance().getRooms();
        return index >= 0 && index < rooms.size() ? rooms.get(index) : null;
    }

    private boolean requirementsMet(RoomDef room) {
        for (String id : room.getRequires()) {
            if (!isRoomOpen(id)) {
                return false;
            }
        }
        return true;
    }

    private List<Scout> onRole(String role) {
        List<Scout> out = new ArrayList<>();
        for (Scout s : scouts.values()) {
            String task = s.getLife().getTaskId();
            if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE && task != null
                    && role.equals(content.task(task).getRole())
                    && isRoomOpen(content.task(task).getRoom())) {
                out.add(s);
            }
        }
        return out;
    }

    /** [RESTORE] on a sealed room: pay up front, then the Restore task does the work. */
    @WorkerThread
    public List<LogLine> startRestore(String roomId) {
        Step step = new Step();
        advance(step);
        RoomDef room = content.getBalance().room(roomId);
        TerminalText.BunkerText text = content.getTerminal().getBunker();
        Map<String, String> vars = new HashMap<>();
        if (room != null) {
            vars.put("room", room.getName());
        }
        if (room == null || isRoomOpen(roomId) || project() != null || !requirementsMet(room)) {
            return step.commit();
        }
        if (pay(room.getCost(), step)) {
            setValue(ROOM_PROJECT, content.getBalance().getRooms().indexOf(room) + 1, step);
            setValue(ROOM_WORK + roomId, 0, step);
            step.system(text.getRoomStarted(), vars, null);
        }
        return step.commit();
    }

    /** Restoration, hydroponics and teaching, by the hours since the last step. */
    private void roomsTick(Step step) {
        double last = value("rooms_at");
        setValue("rooms_at", step.now, step);
        if (last <= 0) {
            return;
        }
        double hours = (step.now - last) / (double) HOUR;
        if (hours <= 0) {
            return;
        }
        TerminalText.BunkerText text = content.getTerminal().getBunker();
        Balance.Rooms tuning = content.getBalance().getRoomTuning();

        RoomDef project = project();
        List<Scout> restorers = onRole("restore");
        if (project != null && !restorers.isEmpty()) {
            double work = value(ROOM_WORK + project.getId()) + hours * restorers.size();
            setValue(ROOM_WORK + project.getId(), work, step);
            if (work >= project.getHours()) {
                setValue(ROOM_OPEN + project.getId(), 1, step);
                setValue(ROOM_PROJECT, 0, step);
                Map<String, String> vars = new HashMap<>();
                vars.put("room", project.getName());
                step.system(text.getRoomDone(), vars, null);
            }
        }

        List<Scout> growers = onRole("hydro");
        if (!growers.isEmpty()) {
            boolean blighted = step.now < value("blight_until");
            if (!blighted && random.chance(Math.min(1, tuning.getBlightChancePerDay() * hours / 24))) {
                setValue("blight_until", step.now + Math.round(tuning.getBlightHours() * HOUR), step);
                step.system(text.getBlight(), Map.of(), null);
                blighted = true;
            }
            double lamps = tuning.getHydroReservePerHour() * growers.size() * hours;
            if (!blighted && reserve >= lamps) {
                reserve -= lamps;
                step.radioWrites.add(() -> baseRepository.saveValue(KEY_RESERVE, reserve));
                // Grow lights: more per grower, and a slightly bigger share of what the bunker eats.
                double lights = moduleBonus("grow_lights");
                double eaten = food.perHour(mouths(), 0) * (tuning.getHydroMaxShare() + lights * 0.25);
                double grown = value("hydro_carry")
                        + Math.min(growers.size() * tuning.getHydroRationsPerHour() * (1 + lights), eaten) * hours;
                int whole = (int) Math.floor(grown);
                setValue("hydro_carry", grown - whole, step);
                if (whole > 0) {
                    step.resourceDelta.merge("rations", whole, Integer::sum);
                }
            }
        }

        // A record player in the music room: everyone at home unwinds a little, all day.
        double calm = moduleBonus("record_player") * hours;
        if (calm > 0 && isRoomOpen("music_room")) {
            double owed = value("calm_carry") + calm;
            int whole = (int) Math.floor(owed);
            setValue("calm_carry", owed - whole, step);
            if (whole > 0) {
                for (Scout s : scouts.values()) {
                    if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE) {
                        rules.addStress(s, -whole);
                    }
                }
            }
        }

        List<Scout> teachers = onRole("teach");
        if (!teachers.isEmpty()) {
            for (Scout s : scouts.values()) {
                if (!s.isAlive() || s.getStatus() != ScoutStatus.IDLE || teachers.contains(s)) {
                    continue;
                }
                double xp = value("teachxp:" + s.getId())
                        + hours * tuning.getTeachXpPerHour() * teachers.size() * (1 + moduleBonus("library"));
                int whole = (int) Math.floor(xp);
                setValue("teachxp:" + s.getId(), xp - whole, step);
                if (whole > 0) {
                    StatType weakest = null;
                    for (StatType type : StatType.values()) {
                        if (weakest == null || s.stat(type).getValue() < s.stat(weakest).getValue()) {
                            weakest = type;
                        }
                    }
                    if (rules.trainXp(s, weakest, whole)) {
                        Map<String, String> vars = new HashMap<>();
                        vars.put("name", s.getName());
                        vars.put("stat", content.getTerminal().getStatNames().get(weakest.key()));
                        vars.put("value", String.valueOf(s.stat(weakest).getValue()));
                        step.system(content.getTerminal().getExpedition().getStatUp(), vars, s);
                    }
                }
            }
        }
    }

    /** Someone at the periscope saw them coming: extra defense, and a line saying so. */
    private int lookoutDefense(Step step) {
        List<Scout> lookouts = onRole("lookout");
        if (lookouts.isEmpty()) {
            return 0;
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("name", lookouts.get(0).getName());
        step.system(content.getTerminal().getBunker().getLookoutSaw(), vars, lookouts.get(0));
        return content.getBalance().getRoomTuning().getLookoutDefense() + (int) Math.round(moduleBonus("watchtower"));
    }

    private List<SessionSnapshot.RoomView> roomViews() {
        List<SessionSnapshot.RoomView> out = new ArrayList<>();
        RoomDef project = project();
        for (RoomDef r : content.getBalance().getRooms()) {
            boolean open = isRoomOpen(r.getId());
            List<String> who = new ArrayList<>();
            for (Scout s : scouts.values()) {
                String task = s.getLife().getTaskId();
                if (open && s.isAlive() && s.getStatus() == ScoutStatus.IDLE && task != null
                        && r.getId().equals(content.task(task).getRoom())) {
                    who.add(s.getName());
                }
            }
            List<String> needs = new ArrayList<>();
            for (String id : r.getRequires()) {
                if (!isRoomOpen(id)) {
                    needs.add(content.getBalance().room(id).getName());
                }
            }
            List<String> tasks = new ArrayList<>();
            for (TaskDef t : content.getTasks().values()) {
                if (r.getId().equals(t.getRoom())) {
                    tasks.add(t.getName());
                }
            }
            boolean restoring = project != null && project.getId().equals(r.getId());
            double progress = restoring ? Math.min(1, value(ROOM_WORK + r.getId()) / Math.max(1, r.getHours())) : 0;
            out.add(new SessionSnapshot.RoomView(r.getId(), r.getName(), open ? r.getDescription() : r.getSealed(),
                    r.getX(), r.getY(), r.getW(), r.getH(), open, restoring, progress,
                    open ? null : describe(r.getCost()), (int) Math.round(r.getHours()),
                    !open && project == null && needs.isEmpty() && canAfford(r.getCost()), needs, who,
                    r.getModules(), tasks, onRole("restore").size()));
        }
        return out;
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

    /** Everyone the crew might mention, so "{he:other}" reads right (core.Pronouns). */
    private void registerPronouns() {
        for (Scout s : scouts.values()) {
            com.estatica.core.Pronouns.register(s.getName(), content.isFemale(s));
        }
        for (ScoutTemplate p : rescuePersonas.values()) {
            com.estatica.core.Pronouns.register(p.getName(), content.isFemale(p));
        }
        for (LinkPacket p : wanderers.values()) {
            com.estatica.core.Pronouns.register(p.getName(), p.isFemale());
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
        double saving = moduleBonus("pantry") + moduleBonus("stove") + moduleBonus("purifier");
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
        vars.put("who", teamNames(scout, party));
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
            for (Scout s : party.everyone(scout)) {
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
        if (romance.isTogether(dead)) {
            // The one you loved. It hits the whole bunker, and you hardest.
            step.lines.add(LogLine.draft(step.now, LogChannel.ALERT, null, TextTemplate.format(
                    content.getTerminal().getBunker().getRomanceLost(), romanceVars(dead)), dead.getId()));
            for (Scout s : scouts.values()) {
                if (s != dead && s.isAlive()) {
                    rules.addStress(s, content.getBalance().getRomance().getJealousyStress());
                }
            }
        }
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

    /**
     * Now and then someone comes to you ("got a minute?"). Checked once an hour at most, with a
     * gap between any two, so coming back after a while finds one person waiting, not everyone.
     * Grief and requests for guidance skip the dice: those don't wait for a good moment.
     */
    private void maybeStartConversation(Step step) {
        Balance.Bond bond = content.getBalance().getBond();
        if (step.now - value("talk_check") < bond.getInitiatedCheckMinutes() * 60_000L) {
            return;
        }
        setValue("talk_check", step.now, step);
        if (step.now - value("talk_initiated") < bond.getInitiatedGapHours() * HOUR) {
            return;
        }
        boolean inTheMood = random.chance(bond.getInitiatedChance());
        long cooldown = bond.getInitiatedCooldownHours() * HOUR;
        List<Scout> order = new ArrayList<>(scouts.values());
        for (int i = order.size() - 1; i > 0; i--) {
            java.util.Collections.swap(order, i, random.nextInt(i + 1));
        }
        for (Scout scout : order) {
            if (hasPendingConversation(scout)
                    || step.now - scout.getLife().getLastInitiatedAtMillis() < cooldown) {
                continue;
            }
            DialogueDef dialogue = dialogues.pick(scout, true, scouts.values(), random, step.now);
            if (dialogue == null || (dialogue.getKind() == DialogueDef.Kind.INITIATED && !inTheMood)) {
                continue;
            }
            setValue("talk_initiated", step.now, step);
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
        return dispatch(scoutId, zoneId, type, target, List.of());
    }

    /** Who goes along on an expedition, and how they get on with the lead. */
    private ExpeditionEngine.Party party(Expedition expedition) {
        Scout lead = scouts.get(expedition.getScoutId());
        List<Scout> companions = new ArrayList<>();
        for (String id : new String[]{expedition.getPartnerId(), expedition.getThirdId()}) {
            Scout c = id != null ? scouts.get(id) : null;
            if (c != null && c.isAlive()) {
                companions.add(c);
            }
        }
        return party(lead, companions, java.util.Set.of());
    }

    private ExpeditionEngine.Party party(Scout lead, List<Scout> companions, java.util.Set<String> stalled) {
        Map<String, String> relations = new HashMap<>();
        for (Scout c : companions) {
            String relation = bunker.relationship(affinities, lead, c);
            if (relation != null) {
                relations.put(c.getId(), relation);
            }
        }
        return new ExpeditionEngine.Party(companions, relations, stalled);
    }

    /** "Mara + Tano + Irene". */
    private static String teamNames(Scout lead, ExpeditionEngine.Party party) {
        StringBuilder out = new StringBuilder(lead.getName());
        for (Scout c : party.companions) {
            out.append(" + ").append(c.getName());
        }
        return out.toString();
    }

    /** Sends a scout out, optionally with a partner (GDD §5.3). */
    @WorkerThread
    public List<LogLine> dispatch(String scoutId, String zoneId, Mission.Type type, String target, String partnerId) {
        return dispatch(scoutId, zoneId, type, target, partnerId != null ? List.of(partnerId) : List.of());
    }

    /**
     * Sends a team out: the lead and up to two more.
     *
     * @param companionIds who goes along, in pick order
     */
    @WorkerThread
    public List<LogLine> dispatch(String scoutId, String zoneId, Mission.Type type, String target,
                                  List<String> companionIds) {
        Step step = new Step();
        advance(step);
        Scout scout = scouts.get(scoutId);
        ZoneDef zone = type == Mission.Type.DEEP ? deepRun(zoneId) : content.getZones().get(zoneId);
        if (type == Mission.Type.SCOUT) {
            com.estatica.domain.map.RegionMap.Sector sector = target != null ? regionMap.sector(target) : null;
            zone = sector != null ? content.wilds(sector.bandId) : null;
            if (sector == null || !canScout(sector)) {
                return step.commit();
            }
        }
        if (scout == null || zone == null || !rules.canDispatch(scout)
                || (type == Mission.Type.DEEP ? !deepTierOpen(target) : !canReach(zone))) {
            return step.commit();
        }
        List<Scout> companions = new ArrayList<>();
        for (String id : companionIds) {
            Scout c = id != null ? scouts.get(id) : null;
            if (c == null || c == scout || companions.contains(c) || !rules.canDispatch(c)
                    || companions.size() >= MAX_COMPANIONS) {
                return step.commit();
            }
            companions.add(c);
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
        double lootFactor = type == Mission.Type.DEEP
                ? content.getBalance().getDeepRuns().lootFactor(Integer.parseInt(target))
                : zone.isWilds() ? 1.0 : lootFactor(zone.getId(), step.now);
        for (Scout c : companions) {
            // The clearest radio on the team carries the reports.
            noise = Math.min(noise, quality.noise * PowerGrid.radioNoiseFactor(grid(step.now).units("radio"))
                    * rules.gearNoiseFactor(c));
        }
        Mission mission = new Mission(type, target, Math.min(1.0, noise), quality.orderDelayMillis, cache, danger,
                lootFactor);
        List<Scout> team = new ArrayList<>();
        team.add(scout);
        team.addAll(companions);
        java.util.Set<String> stalled = fuelUp(team, zone, mission, step);
        ExpeditionEngine.Party party = party(scout, companions, stalled);
        ExpeditionEngine.Dispatch dispatch = engine.dispatch(scout, zone, random.deriveSeed(), mission, party, step.now);
        step.into(dispatch.lines, LogLine.expeditionThread(dispatch.expedition.getId()));
        for (com.estatica.domain.expedition.Segment segment : dispatch.expedition.getSegments()) {
            if (segment.getEventId() != null) {
                setValue(EVENT_SEEN + segment.getEventId(), step.now, step);
            }
        }
        Map<String, String> left = new HashMap<>();
        left.put("who", teamNames(scout, party));
        left.put("zone", zone.getName());
        step.lines.add(LogLine.draft(step.now, LogChannel.SYSTEM, null,
                TextTemplate.format(content.getTerminal().getExpedition().getSummaryDeparted(), left), scout.getId())
                .withThread(LogLine.summaryThread(dispatch.expedition.getId())));
        step.newExpeditions.add(dispatch.expedition);
        active.put(dispatch.expedition.getId(), dispatch.expedition);
        return step.commit();
    }

    /** Up to two go along with the lead. */
    private static final int MAX_COMPANIONS = 2;

    /**
     * Rides with engines need fuel for the whole trip, paid on leaving. If the rides seat the
     * whole team and there's enough, it's burned; otherwise the engines stay parked (the
     * team walks, or pedals) and nothing is spent.
     *
     * @return ride piece ids left parked
     */
    private java.util.Set<String> fuelUp(List<Scout> team, ZoneDef zone, Mission mission, Step step) {
        java.util.Set<String> engines = new java.util.HashSet<>();
        int seats = 0;
        double perHour = 0;
        List<String> names = new ArrayList<>();
        for (Scout s : team) {
            com.estatica.domain.bunker.GearPiece ride = rules.ride(s);
            if (ride == null) {
                continue;
            }
            GearDef def = content.gear(ride.getGearId());
            seats += def.getSeats();
            if (def.getFuelPerHour() > 0) {
                engines.add(ride.getId());
                perHour += def.getFuelPerHour();
                names.add(def.getName().toLowerCase(Locale.ROOT));
            }
        }
        if (engines.isEmpty() || seats < team.size()) {
            return engines; // nothing burns fuel, or the rides can't take everyone anyway
        }
        double pace = rules.teamPace(team, java.util.Set.of())
                * (mission.getType() == Mission.Type.DEEP
                ? content.getBalance().getDeepRuns().durationFactor(mission.getTier()) : 1.0);
        int[] range = zone.getDurationMinutes();
        double hours = com.estatica.domain.expedition.TimelineGenerator.tripMillis(range[0], range[1], pace)
                / (double) HOUR;
        int cost = Math.max(1, (int) Math.ceil(perHour * hours));
        Map<String, String> vars = new HashMap<>();
        vars.put("who", team.get(0).getName());
        vars.put("ride", String.join(", ", names));
        vars.put("fuel", String.valueOf(cost));
        TerminalText.BaseText text = content.getTerminal().getBase();
        if (!canAfford(Map.of("fuel", cost))) {
            step.system(text.getNoFuel(), vars, team.get(0));
            return engines;
        }
        pay(Map.of("fuel", cost), step);
        step.system(text.getFuelUsed(), vars, team.get(0));
        return java.util.Set.of();
    }

    /** Deep runs, once the antenna reaches the first tier; before that, none. */
    private List<SessionSnapshot.DeepRunView> deepRunViews() {
        Balance.DeepRuns tuning = content.getBalance().getDeepRuns();
        int[] needs = tuning.getTierAntenna();
        int antenna = moduleLevel("antenna");
        int maxTier = 0;
        for (int i = 0; i < needs.length; i++) {
            if (antenna >= needs[i]) {
                maxTier = i + 1;
            }
        }
        List<SessionSnapshot.DeepRunView> out = new ArrayList<>();
        if (maxTier == 0) {
            return out;
        }
        for (ZoneDef z : content.getDeepRuns()) {
            SessionSnapshot.DeepRunView view = new SessionSnapshot.DeepRunView(z.getId(), z.getName(),
                    z.getDescription(), z.getDanger(), z.getDurationMinutes()[0], z.getDurationMinutes()[1], maxTier,
                    tuning.tiers(), needs.clone());
            view.durationPerTier = tuning.durationFactor(2) - 1;
            view.lootPerTier = tuning.lootFactor(2) - 1;
            view.dangerPerTier = tuning.extraDanger(2);
            out.add(view);
        }
        return out;
    }

    /** A deep run by id, or null. */
    private ZoneDef deepRun(String zoneId) {
        for (ZoneDef z : content.getDeepRuns()) {
            if (z.getId().equals(zoneId)) {
                return z;
            }
        }
        return null;
    }

    /** Whether the antenna reaches far enough for this deep-run tier ("1"…). */
    private boolean deepTierOpen(String tier) {
        int[] needs = content.getBalance().getDeepRuns().getTierAntenna();
        try {
            int t = Integer.parseInt(tier);
            return t >= 1 && t <= needs.length && moduleLevel("antenna") >= needs[t - 1];
        } catch (NumberFormatException e) {
            return false;
        }
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
        doneTeams.put(expedition.getId(),
                new String[]{expedition.getScoutId(), expedition.getPartnerId(), expedition.getThirdId()});
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
        SessionSnapshot.MapView view = new SessionSnapshot.MapView(sectors, places, bands,
                (int) Math.round(content.getBalance().getDepletion().getRecoveryPerDay() * 100));
        Map<String, Integer> installed = new LinkedHashMap<>();
        for (Relay relay : relays.values()) {
            installed.put(relay.getZoneId(), (int) Math.round(relay.getCondition()));
        }
        view.relays = installed;
        view.relayKits = resources.getOrDefault("relay_kit", 0);
        return view;
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
        // Story transmissions from a chapter that hasn't started yet stay off the air.
        Set<String> closed = new java.util.HashSet<>(receivedTransmissions);
        for (SignalsDef.Transmission t : content.getSignals().getTransmissions()) {
            if (t.getPlot() != null && !plotOpen(t.getPlot())) {
                closed.add(t.getId());
            }
        }
        return radio.resolve(spot, knownZones, roomForSurvivor, closed, heardRares(), rng);
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
        if (station.getId().equals(content.getBalance().getMarket().getStation())) {
            discover(content.getBalance().getMarket().getZone(), step);
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
        List<String> heard = romance.isTogether(dead) && !echo.getHeardPartner().isEmpty()
                ? echo.getHeardPartner() : echo.getHeard();
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
                // Same message, other words: picked off the clock, so the dial's preview (which
                // replays the random rolls) still matches.
                String plain = def.text(step.now / 60_000L + def.getId().hashCode());
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
        LinkSignalEntity fromLink = t.getSourceId().startsWith(LINK_SOURCE)
                ? linkSignals.get(t.getSourceId().substring(LINK_SOURCE.length())) : null;
        LinkPacket signer = fromLink != null ? unpack(fromLink.code) : null;
        if (signer != null) {
            linkLine("signed", linkVars(signer), null, step);
            // The harder the scheme, the more they left for whoever cracked it.
            Map<String, Integer> stash = linkRules.decodeReward(t.getKind().name(), random);
            if (!stash.isEmpty()) {
                stash.forEach((k, v) -> step.resourceDelta.merge(k, v, Integer::sum));
                Map<String, String> stashVars = linkVars(signer);
                stashVars.put("loot", describe(stash));
                linkLine("decodeReward", stashVars, null, step);
            }
        }

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
        return PowerGrid.compute(energy, power, moduleLevel("generator"), fuelCovers() ? 0 : events.blackoutWattsAt(now),
                energy.isNight(clock.localHour(now)));
    }

    /** Value key: the generator burns fuel through blackouts (1) or not. */
    private static final String KEY_FUEL_GENERATOR = "fuel_generator";
    /** Fuel burned but not yet taken from stock (fractions of a unit). */
    private static final String KEY_FUEL_OWED = "fuel_owed";

    /** In a blackout, fuel keeps the lights on: if the operator allows it and there's some left. */
    private boolean fuelCovers() {
        return value(KEY_FUEL_GENERATOR) > 0 && resources.getOrDefault("fuel", 0) > 0;
    }

    /** Burns fuel for the blackout hours just covered; says so when the tank runs dry. */
    private void burnGeneratorFuel(long from, long to, Step step) {
        if (value(KEY_FUEL_GENERATOR) <= 0 || resources.getOrDefault("fuel", 0) <= 0) {
            return;
        }
        double hours = events.blackoutHours(from, to);
        if (hours <= 0) {
            return;
        }
        double owed = value(KEY_FUEL_OWED) + hours * content.getBalance().getFuel().getGeneratorPerHour();
        int whole = (int) Math.floor(owed);
        int have = resources.getOrDefault("fuel", 0);
        int burn = Math.min(whole, have);
        if (burn > 0) {
            pay(Map.of("fuel", burn), step);
        }
        setValue(KEY_FUEL_OWED, owed - burn, step);
        TerminalText.BaseText text = content.getTerminal().getBase();
        if (resources.getOrDefault("fuel", 0) <= 0) {
            step.system(text.getGeneratorDry(), Map.of(), null);
        } else if (burn > 0) {
            Map<String, String> vars = new HashMap<>();
            vars.put("fuel", String.valueOf(burn));
            vars.put("hours", String.valueOf(Math.max(1, Math.round(hours))));
            step.system(text.getGeneratorFuel(), vars, null);
        }
    }

    /** The operator lets the generator burn fuel in blackouts, or stops it. */
    @WorkerThread
    public List<LogLine> toggleFuelGenerator() {
        Step step = new Step();
        advance(step);
        setValue(KEY_FUEL_GENERATOR, value(KEY_FUEL_GENERATOR) > 0 ? 0 : 1, step);
        return step.commit();
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
            burnGeneratorFuel(baseTick, now, step);
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
        RaidRules.Result result = raids.resolve(scouts.values(), stock, moduleLevel("antenna"),
                (int) moduleBonus("barricades") + lookoutDefense(step), random);
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
    /** Power sets how fast the tools run; whoever is at the bench (Craft) sets the rest. Nobody: nothing. */
    private double workshopSpeed(PowerGrid.State g) {
        return PowerGrid.workshopSpeed(g.units("workshop")) * workshopRules.speed(bestWorkshopCraft());
    }

    /** The jobs being worked on now: one per bench, in order; the rest wait in line. */
    private List<CraftJob> workingJobs() {
        int benches = Math.max(1, moduleLevel("workshop"));
        return new ArrayList<>(craftJobs.subList(0, Math.min(benches, craftJobs.size())));
    }

    /** Whoever is at the bench gets better at it while there's work. */
    private void trainCrafters(double hours) {
        double xp = hours * content.getBalance().getWorkshop().getXpPerHour();
        if (xp <= 0) {
            return;
        }
        for (Scout s : scouts.values()) {
            if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE && "taller".equals(s.getLife().getTaskId())) {
                double owed = value(CRAFT_XP + s.getId()) + xp;
                int whole = (int) Math.floor(owed);
                if (whole > 0) {
                    rules.trainXp(s, StatType.CRAFT, whole);
                }
                baseValues.put(CRAFT_XP + s.getId(), owed - whole);
            }
        }
    }

    private static final String CRAFT_XP = "craftxp:";

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
        List<CraftJob> working = workingJobs();
        List<Double> minutes = new ArrayList<>();
        for (CraftJob job : working) {
            minutes.add(content.recipe(job.getRecipeId()).getMinutes());
        }
        double speed = workshopSpeed(g);
        if (speed > 0) {
            trainCrafters(hours);
        }
        List<CraftJob> done = CraftJob.work(working, minutes, hours, speed);
        for (CraftJob job : working) {
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
        for (CraftJob job : workingJobs()) {
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
        TerminalText.BaseText text = content.getTerminal().getBase();
        Map<String, String> need = new HashMap<>();
        need.put("item", recipe.getName());
        need.put("craft", String.valueOf(recipe.getMinCraft()));
        if (!knowsRecipe(recipe)) {
            return step.commit();
        } else if (craftJobs.size() >= workshopRules.capacity(moduleLevel("workshop"))) {
            step.system(text.getCraftNoSlot(), Map.of(), null);
        } else if (bestWorkshopCraft() <= 0) {
            step.system(text.getBenchEmpty(), need, null);
        } else if (!workshopRules.canMake(recipe, bestWorkshopCraft())) {
            step.system(text.getCraftTooHard(), need, null);
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

    // ---- Blueprints ----

    private static final String BLUEPRINT = "bp:";

    private boolean knowsRecipe(RecipeDef r) {
        return r.isKnown() || value(BLUEPRINT + r.getId()) > 0;
    }

    private void learnBlueprint(RecipeDef r, String template, Scout who, Step step) {
        if (knowsRecipe(r)) {
            return;
        }
        setValue(BLUEPRINT + r.getId(), 1, step);
        Map<String, String> vars = new HashMap<>();
        vars.put("item", r.getName());
        vars.put("name", who != null ? who.getName() : "");
        step.system(template, vars, who);
    }

    /** Plans still out there, nearest band first. */
    private List<RecipeDef> unknownRecipes(int maxBand) {
        List<RecipeDef> out = new ArrayList<>();
        for (RecipeDef r : content.getRecipes().values()) {
            if (!knowsRecipe(r) && content.getBalance().bandIndex(r.getFindBand()) <= maxBand) {
                out.add(r);
            }
        }
        out.sort(Comparator.comparing(RecipeDef::getId));
        return out;
    }

    /** Coming home, now and then someone brings a blueprint they found. */
    private void maybeFindBlueprint(Scout scout, ZoneDef zone, Step step) {
        int band = content.getBalance().bandIndex(zone.getBand());
        List<RecipeDef> left = unknownRecipes(band);
        if (left.isEmpty() || !random.chance(content.getBalance().getWorkshop().findChance(band))) {
            return;
        }
        learnBlueprint(random.pick(left), content.getTerminal().getBase().getBlueprintFound(), scout, step);
    }

    /** Once a day, whoever is at the bench may work out a plan they're good enough for. */
    private void maybeWorkOutBlueprint(Step step) {
        long day = localDay(step.now);
        if (value("bp_day") >= day + 1) {
            return;
        }
        setValue("bp_day", day + 1, step);
        Scout best = null;
        for (Scout s : scouts.values()) {
            if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE && "taller".equals(s.getLife().getTaskId())
                    && (best == null || rules.effectiveStat(s, StatType.CRAFT) > rules.effectiveStat(best, StatType.CRAFT))) {
                best = s;
            }
        }
        // The archive's card index: old plans to compare against.
        double chance = content.getBalance().getWorkshop().getWorkOutChancePerDay() + moduleBonus("card_index");
        if (best == null || !random.chance(chance)) {
            return;
        }
        int craft = rules.effectiveStat(best, StatType.CRAFT);
        List<RecipeDef> fits = new ArrayList<>();
        for (RecipeDef r : unknownRecipes(Integer.MAX_VALUE)) {
            if (r.getMinCraft() <= craft) {
                fits.add(r);
            }
        }
        if (!fits.isEmpty()) {
            learnBlueprint(random.pick(fits), content.getTerminal().getBase().getBlueprintWorkedOut(), best, step);
        }
    }

    // ---- Salvage and upgrades ----

    /** Breaks a piece in stock back into materials. */
    @WorkerThread
    public List<LogLine> salvage(String pieceId) {
        Step step = new Step();
        advance(step);
        com.estatica.domain.bunker.GearPiece piece = gear.get(pieceId);
        if (piece != null && piece.isInBunker()) {
            Map<String, Integer> back = workshopRules.salvage(piece);
            Map<String, String> vars = new HashMap<>();
            vars.put("gear", gearName(piece));
            vars.put("loot", back.isEmpty() ? "-" : describe(back));
            piece.moveTo(com.estatica.domain.bunker.GearPiece.GONE);
            back.forEach((k, v) -> step.resourceDelta.merge(k, v, Integer::sum));
            step.system(content.getTerminal().getBase().getSalvaged(), vars, null);
        }
        return step.commit();
    }

    /** Raises a piece in stock one rarity: needs the Craft at the bench, and materials. */
    @WorkerThread
    public List<LogLine> upgradeGear(String pieceId) {
        Step step = new Step();
        advance(step);
        com.estatica.domain.bunker.GearPiece piece = gear.get(pieceId);
        if (piece == null || !piece.isInBunker()) {
            return step.commit();
        }
        int needs = workshopRules.upgradeCraft(piece);
        TerminalText.BaseText text = content.getTerminal().getBase();
        Map<String, String> vars = new HashMap<>();
        vars.put("item", gearName(piece));
        vars.put("craft", String.valueOf(needs));
        if (needs < 0) {
            return step.commit();
        }
        if (bestWorkshopCraft() <= 0) {
            step.system(text.getBenchEmpty(), vars, null);
        } else if (bestWorkshopCraft() < needs) {
            step.system(text.getCraftTooHard(), vars, null);
        } else if (pay(workshopRules.upgradeCost(piece), step)) {
            com.estatica.domain.bunker.GearPiece.Rarity next =
                    com.estatica.domain.bunker.GearPiece.Rarity.values()[piece.getRarity().ordinal() + 1];
            com.estatica.domain.bunker.GearPiece better = new com.estatica.domain.bunker.GearPiece(piece.getId(),
                    piece.getGearId(), next, piece.getCondition(), piece.getLocation(), piece.getOriginalOwnerId());
            gear.put(better.getId(), better);
            vars.put("gear", gearName(better));
            vars.put("rarity", content.getTerminal().getBase().getRarities()
                    .getOrDefault(next.name(), next.name().toLowerCase(Locale.ROOT)));
            step.system(text.getUpgraded(), vars, null);
        }
        return step.commit();
    }

    // ---- THE FAIR: the trader on the radio ----

    private static final String REPUTATION = "feria_rep";
    private static final String SOLD = "sold:";
    private static final String BOUGHT = "bought:";
    private static final String COURIER = "cour:";
    private static final String HELD = "held:";

    private int reputation() {
        return baseValues.containsKey(REPUTATION) ? (int) Math.round(value(REPUTATION))
                : content.getBalance().getMarket().getStartReputation();
    }

    private SignalsDef.Station marketStation() {
        String id = content.getBalance().getMarket().getStation();
        for (SignalsDef.Station st : content.getSignals().getStations()) {
            if (st.getId().equals(id)) {
                return st;
            }
        }
        return null;
    }

    /** Heard at least once: you know where to find them. */
    private boolean marketKnown() {
        SignalsDef.Station st = marketStation();
        return st != null && lore.containsKey(st.getId());
    }

    private boolean marketOpen(long now) {
        SignalsDef.Station st = marketStation();
        return marketKnown() && Broadcasts.onAir(st, clock.localMillis(now));
    }

    private com.estatica.domain.bunker.MarketRules.Day marketDay(long now) {
        long day = localDay(now);
        return marketRules.day(day, r -> !knowsRecipe(r), reputation());
    }

    private void traded(Step step) {
        setValue(REPUTATION, marketRules.afterTrade(reputation()), step);
        count("trades", step);
    }

    /** Sells {@code qty} of a good in stock. */
    @WorkerThread
    public List<LogLine> sell(String good, int qty) {
        Step step = new Step();
        advance(step);
        if (!marketOpen(step.now) || qty <= 0 || resources.getOrDefault(good, 0) < qty
                || !content.getBalance().getMarket().getGoods().containsKey(good)) {
            return step.commit();
        }
        long day = localDay(step.now);
        String soldKey = SOLD + day + ":" + good;
        int chips = marketRules.sellTotal(good, marketDay(step.now), reputation(), (int) value(soldKey), qty);
        pay(Map.of(good, qty), step);
        step.resourceDelta.merge("chips", chips, Integer::sum);
        setValue(soldKey, value(soldKey) + qty, step);
        traded(step);
        Map<String, String> vars = new HashMap<>();
        vars.put("what", describe(Map.of(good, qty)));
        vars.put("chips", String.valueOf(chips));
        step.system(content.getTerminal().getBase().getMarketSold(), vars, null);
        return step.commit();
    }

    /** Sells a piece of gear from stock. */
    @WorkerThread
    public List<LogLine> sellGear(String pieceId) {
        Step step = new Step();
        advance(step);
        com.estatica.domain.bunker.GearPiece piece = gear.get(pieceId);
        if (!marketOpen(step.now) || piece == null || !piece.isInBunker()) {
            return step.commit();
        }
        int chips = marketRules.gearSellPrice(piece, reputation());
        Map<String, String> vars = new HashMap<>();
        vars.put("what", gearName(piece));
        vars.put("chips", String.valueOf(chips));
        piece.moveTo(com.estatica.domain.bunker.GearPiece.GONE);
        step.resourceDelta.merge("chips", chips, Integer::sum);
        traded(step);
        step.system(content.getTerminal().getBase().getMarketSold(), vars, null);
        return step.commit();
    }

    /**
     * Buys from THE FAIR. {@code what}: "R:good" (with a quantity), or an offer key
     * ("BLUEPRINT:id", "GEAR:id:RARITY", one each, once a day).
     *
     * @param courier delivered for a fee; otherwise it waits at THE FAIR for someone to fetch it
     */
    @WorkerThread
    public List<LogLine> buy(String what, int qty, boolean courier) {
        Step step = new Step();
        advance(step);
        if (!marketOpen(step.now) || qty <= 0) {
            return step.commit();
        }
        com.estatica.domain.bunker.MarketRules.Day today = marketDay(step.now);
        long day = localDay(step.now);
        int price;
        String name;
        if (what.startsWith("R:")) {
            String good = what.substring(2);
            int each = marketRules.buyPrice(good, today, reputation());
            if (each < 0) {
                return step.commit();
            }
            price = each * qty;
            name = describe(Map.of(good, qty));
        } else {
            com.estatica.domain.bunker.MarketRules.Offer offer = null;
            for (com.estatica.domain.bunker.MarketRules.Offer o : today.offers) {
                if (o.key().equals(what)) {
                    offer = o;
                }
            }
            if (offer == null || value(BOUGHT + day + ":" + what) > 0) {
                return step.commit();
            }
            qty = 1;
            price = offer.price;
            name = offerName(offer);
            setValue(BOUGHT + day + ":" + what, 1, step);
        }
        int total = price + (courier ? marketRules.courierFee(price) : 0);
        if (!pay(Map.of("chips", total), step)) {
            return step.commit();
        }
        TerminalText.BaseText text = content.getTerminal().getBase();
        Map<String, String> vars = new HashMap<>();
        vars.put("what", name);
        vars.put("chips", String.valueOf(total));
        if (courier) {
            long eta = step.now + Math.round(content.getBalance().getMarket().getCourierHours() * HOUR);
            setValue(COURIER + com.estatica.core.Ids.newId().substring(0, 8) + ":" + what + ":" + qty, eta, step);
            Map<String, String> h = new HashMap<>();
            h.put("hours", String.valueOf(Math.round(content.getBalance().getMarket().getCourierHours())));
            vars.put("how", TextTemplate.format(text.getMarketCourier(), h));
        } else {
            setValue(HELD + what, value(HELD + what) + qty, step);
            vars.put("how", text.getMarketPickup());
        }
        traded(step);
        step.system(text.getMarketBought(), vars, null);
        return step.commit();
    }

    private String offerName(com.estatica.domain.bunker.MarketRules.Offer offer) {
        if (offer.kind == com.estatica.domain.bunker.MarketRules.Offer.Kind.BLUEPRINT) {
            Map<String, String> v = new HashMap<>();
            v.put("item", content.recipe(offer.id).getName());
            return TextTemplate.format(content.getTerminal().getBase().getBlueprintName(), v);
        }
        return content.gear(offer.id).getName() + " · " + content.getTerminal().getBase().getRarities()
                .getOrDefault(offer.rarity.name(), offer.rarity.name().toLowerCase(Locale.ROOT));
    }

    /** Hands over what was bought: goods into stock, plans onto the bench, gear on the shelf. */
    private String deliver(String what, int qty, Step step) {
        if (what.startsWith("R:")) {
            String good = what.substring(2);
            step.resourceDelta.merge(good, qty, Integer::sum);
            return describe(Map.of(good, qty));
        }
        String[] parts = what.split(":");
        if ("BLUEPRINT".equals(parts[0])) {
            RecipeDef r = content.getRecipes().get(parts[1]);
            if (r != null) {
                learnBlueprint(r, content.getTerminal().getBase().getBlueprintLearned(), null, step);
                Map<String, String> v = new HashMap<>();
                v.put("item", r.getName());
                return TextTemplate.format(content.getTerminal().getBase().getBlueprintName(), v);
            }
            return parts[1];
        }
        com.estatica.domain.bunker.GearPiece.Rarity rarity = com.estatica.domain.bunker.GearPiece.Rarity.fromKey(parts[2]);
        String label = parts[1];
        for (int i = 0; i < qty && content.gear(parts[1]) != null; i++) {
            com.estatica.domain.bunker.GearPiece piece = com.estatica.domain.bunker.GearPiece.fresh(parts[1], rarity);
            gear.put(piece.getId(), piece);
            label = gearName(piece);
        }
        return label;
    }

    /** Couriers that have arrived. */
    private void deliverCouriers(Step step) {
        List<String> due = new ArrayList<>();
        for (Map.Entry<String, Double> e : baseValues.entrySet()) {
            if (e.getKey().startsWith(COURIER) && e.getValue() > 0 && e.getValue() <= step.now) {
                due.add(e.getKey());
            }
        }
        java.util.Collections.sort(due);
        for (String key : due) {
            String rest = key.substring(COURIER.length());
            String body = rest.substring(rest.indexOf(':') + 1);
            int qty = Integer.parseInt(body.substring(body.lastIndexOf(':') + 1));
            String what = body.substring(0, body.lastIndexOf(':'));
            setValue(key, 0, step);
            Map<String, String> vars = new HashMap<>();
            vars.put("what", deliver(what, qty, step));
            step.system(content.getTerminal().getBase().getMarketDelivered(), vars, null);
        }
    }

    /** Back from THE FAIR: whatever was waiting there comes home with them. */
    private void pickUp(Scout lead, Step step) {
        List<String> held = new ArrayList<>();
        for (Map.Entry<String, Double> e : baseValues.entrySet()) {
            if (e.getKey().startsWith(HELD) && e.getValue() > 0) {
                held.add(e.getKey());
            }
        }
        java.util.Collections.sort(held);
        List<String> names = new ArrayList<>();
        for (String key : held) {
            int qty = (int) Math.round(value(key));
            setValue(key, 0, step);
            names.add(deliver(key.substring(HELD.length()), qty, step));
        }
        if (!names.isEmpty()) {
            Map<String, String> vars = new HashMap<>();
            vars.put("name", lead.getName());
            vars.put("what", String.join(", ", names));
            step.system(content.getTerminal().getBase().getMarketPickedUp(), vars, lead);
        }
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
                    vars.put("who", teamNames(lead, team));
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

    /** What a piece does at its rarity: "Wits +2 · +38% loot". */
    private String gearEffects(com.estatica.domain.bunker.GearPiece piece) {
        GearDef def = content.gear(piece.getGearId());
        return com.estatica.domain.bunker.GearRules.describe(def, piece, content.getTerminal().getBase().getGearEffects(),
                content.getTerminal().getStatNames());
    }

    private String gearLabel(com.estatica.domain.bunker.GearPiece piece) {
        TerminalText.BaseText text = content.getTerminal().getBase();
        Map<String, String> vars = new HashMap<>();
        vars.put("gear", content.gear(piece.getGearId()).getName());
        vars.put("rarity", text.getRarities().getOrDefault(piece.getRarity().name(), piece.getRarity().name()));
        vars.put("condition", String.valueOf(piece.getCondition()));
        return TextTemplate.format(piece.isBroken() ? text.getGearLabelBroken() : text.getGearLabel(), vars);
    }

    /**
     * Time on the road wears gear down (trouble on the way already did its part), and
     * sometimes something useful turns up on the way back.
     */
    private void gearAfterTrip(Scout scout, String zoneId, double hours, Step step) {
        TerminalText.BaseText text = content.getTerminal().getBase();
        for (com.estatica.domain.bunker.GearPiece piece : rules.working(scout)) {
            int before = gearRules.wearForTrip(piece, hours);
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
        int bench = bestWorkshopCraft();
        for (RecipeDef r : content.getRecipes().values()) {
            if (!knowsRecipe(r)) {
                continue; // a blueprint still out there
            }
            int needs = moduleLevel("workshop") >= r.getMinWorkshopLevel() ? 0 : r.getMinWorkshopLevel();
            SessionSnapshot.RecipeView view = new SessionSnapshot.RecipeView(r.getId(), r.getName(),
                    describe(r.getInputs()), (int) Math.ceil(r.getMinutes()), canAfford(r.getInputs()), needs);
            view.minCraft = r.getMinCraft();
            view.crafterOk = workshopRules.canMake(r, bench);
            recipes.add(view);
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
                gearStock.add(new SessionSnapshot.ItemView(piece.getId(), label, gearEffects(piece),
                        def.getSlot().name()));
            }
            Scout wearer = wearerOf(piece);
            String holder = wearer != null ? wearer.getName() : null;
            boolean damaged = piece.getCondition() < com.estatica.domain.bunker.GearPiece.MAX_CONDITION;
            Map<String, Integer> cost = gearRules.repairCost(piece);
            SessionSnapshot.GearView view = new SessionSnapshot.GearView(piece.getId(),
                    label + "\n" + gearEffects(piece), holder,
                    damaged && atHome(piece) ? describe(cost) : null, canAfford(cost));
            view.inStock = piece.isInBunker();
            if (view.inStock) {
                Map<String, Integer> back = workshopRules.salvage(piece);
                view.salvage = back.isEmpty() ? null : describe(back);
                int needsCraft = workshopRules.upgradeCraft(piece);
                if (needsCraft >= 0) {
                    Map<String, Integer> up = workshopRules.upgradeCost(piece);
                    view.upgradeCost = describe(up);
                    view.upgradeCraft = needsCraft;
                    view.upgradeOk = bench >= needsCraft && canAfford(up);
                }
            }
            gearViews.add(view);
        }
        SessionSnapshot.BaseView baseView = new SessionSnapshot.BaseView(goalViews(now), !g.cut.isEmpty(), energy.watts(moduleLevel("generator")), g.available, g.used,
                events.blackoutWattsAt(now) > 0, g.night, g.filtersShort, consumers,
                (int) Math.floor(reserve), (int) reserveCapacity(),
                resources.getOrDefault("batteries", 0) > 0, modules, moduleLevel("workshop"), jobs, recipes,
                guardNames, raids.defense(guards) + (int) moduleBonus("barricades"), gearStock, aliveScouts(), rosterCap(), gearViews);
        baseView.rechargePer = (int) Math.round(rechargePerBattery());
        baseView.rooms = roomViews();
        Scout atBench = null;
        for (Scout s : scouts.values()) {
            if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE && "taller".equals(s.getLife().getTaskId())
                    && (atBench == null || rules.effectiveStat(s, StatType.CRAFT) > rules.effectiveStat(atBench, StatType.CRAFT))) {
                atBench = s;
            }
        }
        baseView.crafter = atBench != null ? atBench.getName() : null;
        baseView.crafterCraft = atBench != null ? rules.effectiveStat(atBench, StatType.CRAFT) : 0;
        baseView.capacity = workshopRules.capacity(moduleLevel("workshop"));
        baseView.blueprintsTotal = content.getRecipes().size();
        for (RecipeDef r : content.getRecipes().values()) {
            baseView.blueprintsKnown += knowsRecipe(r) ? 1 : 0;
        }
        baseView.fuel = resources.getOrDefault("fuel", 0);
        baseView.fuelGenerator = value(KEY_FUEL_GENERATOR) > 0;
        baseView.batteries = resources.getOrDefault("batteries", 0);
        return baseView;
    }

    // ---- Story: personal missions, La Voz, goals (GDD §5.2, §8, §9) ----

    /** Fills in reach, trip range and danger from the zone. */
    private SessionSnapshot.MissionView missionView(SessionSnapshot.MissionView m, ZoneDef z) {
        m.reachable = canReach(z);
        m.minMinutes = z.getDurationMinutes()[0];
        m.maxMinutes = z.getDurationMinutes()[1];
        m.danger = z.getDanger();
        return m;
    }

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
    /** A fragment can turn up once its chapter is open: the first always, the rest after the one before. */
    private boolean plotOpen(String fragmentId) {
        List<StoryDef.Chapter> chapters = content.getStory().getChapters();
        StoryDef.Chapter chapter = content.getStory().chapterOf(fragmentId);
        int i = chapters.indexOf(chapter);
        return i <= 0 || gameFlags.contains("chapter:" + chapters.get(i - 1).getId());
    }

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
            attention(scout, step);
            flushRomanceValues(step);
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
            // A gift to someone who's open to you, or yours, means something more.
            if (romance.isTogether(scout) || romance.isReady(scout)) {
                romanceHooks().add(scout, content.getBalance().getRomance().getGift(), step.now, step.lines);
                attention(scout, step);
                flushRomanceValues(step);
            }
        }
        return step.commit();
    }

    /** Every stat maxed: start over higher. {@code statKey} gets the extra ceiling point. */
    @WorkerThread
    public List<LogLine> retrain(String scoutId, String statKey) {
        Step step = new Step();
        advance(step);
        Scout scout = scouts.get(scoutId);
        StatType focus = StatType.fromKey(statKey);
        if (scout != null && retrainRules.retrain(scout, focus)) {
            Map<String, String> vars = new HashMap<>();
            vars.put("name", scout.getName());
            vars.put("stat", content.getTerminal().getStatNames().get(focus.key()));
            vars.put("tier", String.valueOf(com.estatica.domain.scouts.RetrainRules.tier(scout)));
            step.system(content.getTerminal().getBase().getRetrained(), vars, scout);
        }
        return step.commit();
    }

    /** Mara used to run deliveries on a bike: hers turns up in storage, once. */
    private void maraBike(Step step) {
        if (value("mara_bike") > 0 || content.getGear().get("gear_bici") == null) {
            return;
        }
        for (Scout s : scouts.values()) {
            if ("mara".equals(s.getTemplateId()) && s.isAlive()) {
                setValue("mara_bike", 1, step);
                com.estatica.domain.bunker.GearPiece bike = com.estatica.domain.bunker.GearPiece.fresh("gear_bici",
                        com.estatica.domain.bunker.GearPiece.Rarity.COMMON);
                gear.put(bike.getId(), bike);
                Map<String, String> vars = new HashMap<>();
                vars.put("name", s.getName());
                step.system(content.getTerminal().getBase().getMaraBike(), vars, s);
                return;
            }
        }
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

    // ---- Other bunkers: codes, QR, Bluetooth (GDD §12) ----

    /** Transmissions that came from another bunker: sourceId is this plus the packet id. */
    private static final String LINK_SOURCE = "link:";
    /** Incoming over Bluetooth: face to face, so no daily limit. */
    private static final String NEAR = "NEAR";
    private static final long DAY = 24 * HOUR;
    private static final Map<String, String> SCHEME_KEYS = Map.of(
            "ATBASH", "mirror_card", "TAP", "tap_chart", "BASE64", "b64_notebook");

    private final Map<String, LinkSignalEntity> linkSignals = new LinkedHashMap<>();
    /** Wanderers on the road, by packet id. */
    private final Map<String, LinkPacket> wanderers = new LinkedHashMap<>();
    private LinkRules linkRules;
    private String callsign;
    /** The code just made or picked to show again, until the operator closes it. */
    private String shownCode;
    private String shownLabel;

    private void loadLinks() {
        linkRules = new LinkRules(content);
        callsign = com.estatica.domain.link.Callsign.of(state.getOperatorId());
        for (LinkSignalEntity e : db.linkDao().all()) {
            linkSignals.put(e.id, e);
            if (LinkSignalEntity.WAITING.equals(e.status)) {
                LinkPacket p = unpack(e.code);
                if (p != null) {
                    wanderers.put(e.id, p);
                }
            }
        }
    }

    public String callsign() {
        return callsign;
    }

    private LinkPacket unpack(String code) {
        LinkCodec.Decoded d = LinkCodec.decode(code, content.getBalance().getLink().getBroadcastKey());
        return d.ok() ? linkRules.sanitize(d.packet) : null;
    }

    private void linkLine(String key, Map<String, String> vars, Scout scout, Step step) {
        String template = content.getTerminal().getLink().get(key);
        if (template != null) {
            step.system(template, vars, scout);
        }
    }

    private Map<String, String> linkVars(LinkPacket p) {
        Map<String, String> vars = new HashMap<>();
        vars.put("callsign", p.getCallsign());
        vars.put("sender", p.getSender().isEmpty() ? "" : " (" + p.getSender() + ")");
        vars.put("name", p.getName());
        vars.put("words", p.getText());
        vars.put("text", p.getText());
        vars.put("title", p.getTitle());
        return vars;
    }

    private String linkText(String key, Map<String, String> vars) {
        String template = content.getTerminal().getLink().get(key);
        return template != null ? TextTemplate.format(template, vars) : key;
    }

    private static String clip(String text, int max) {
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }

    /** "10 scrap, 2 components, helmet". */
    private String describeDrop(Map<String, Integer> amounts, List<String> gearNames) {
        List<String> parts = new ArrayList<>();
        if (!amounts.isEmpty()) {
            parts.add(describe(amounts));
        }
        parts.addAll(gearNames);
        return parts.isEmpty() ? linkText("nothing", Map.of()) : String.join(", ", parts);
    }

    private void keepSent(LinkPacket p, String code, String label, Step step) {
        LinkSignalEntity e = new LinkSignalEntity();
        e.id = p.getId();
        e.direction = LinkSignalEntity.OUT;
        e.type = p.getType().name();
        e.callsign = callsign;
        e.code = code;
        e.label = label;
        e.atMillis = step.now;
        linkSignals.put(e.id, e);
        step.radioWrites.add(() -> db.linkDao().upsert(e));
        // Only the last few sent are kept to show again.
        List<String> sent = new ArrayList<>();
        for (LinkSignalEntity s : linkSignals.values()) {
            if (LinkSignalEntity.OUT.equals(s.direction)) {
                sent.add(s.id);
            }
        }
        for (int i = 0; i < sent.size() - content.getBalance().getLink().getSentKept(); i++) {
            String old = sent.get(i);
            linkSignals.remove(old);
            step.radioWrites.add(() -> db.linkDao().delete(old));
        }
        shownCode = code;
        shownLabel = label;
        linkLine("codeReady", Map.of(), null, step);
    }

    private String operatorTag() {
        return state.getOperatorName() != null ? state.getOperatorName() : "";
    }

    /** Schemes the operator can send in: plain, Caesar, Morse, and the ones whose key they hold. */
    private boolean canSendIn(String scheme) {
        String key = SCHEME_KEYS.get(scheme);
        return LinkRules.MESSAGE_SCHEMES.contains(scheme) && (key == null || hasKey(key));
    }

    /** A message for another bunker, in the clear or in a scheme they'll have to crack. */
    @WorkerThread
    public List<LogLine> linkMessage(String text, String scheme) {
        Step step = new Step();
        advance(step);
        String clean = text == null ? "" : text.trim();
        int max = content.getBalance().getLink().getMessageMaxChars();
        if (clean.length() > max) {
            clean = clean.substring(0, max);
        }
        boolean ciphered = !LinkRules.PLAIN.equals(scheme);
        if (clean.isEmpty() || !canSendIn(scheme)
                || (ciphered && com.estatica.domain.radio.Cipher.normalize(clean).isEmpty())) {
            return step.commit();
        }
        int shift = "CAESAR".equals(scheme) ? random.nextIntInclusive(1, 25) : 0;
        LinkPacket p = LinkPacket.message(callsign, operatorTag(), step.now, clean, scheme, shift);
        keepSent(p, LinkCodec.encode(p), linkText("labelMessage", Map.of("text", clip(clean, 24))), step);
        return step.commit();
    }

    /** Pieces in stock, by id, ignoring any that aren't. */
    private List<com.estatica.domain.bunker.GearPiece> stockPieces(List<String> pieceIds) {
        List<com.estatica.domain.bunker.GearPiece> out = new ArrayList<>();
        for (String id : pieceIds) {
            com.estatica.domain.bunker.GearPiece piece = gear.get(id);
            if (piece != null && piece.isInBunker() && !out.contains(piece)) {
                out.add(piece);
            }
        }
        return out;
    }

    private static Map<String, Integer> positive(Map<String, Integer> goods) {
        Map<String, Integer> out = new LinkedHashMap<>();
        goods.forEach((k, v) -> {
            if (v != null && v > 0) {
                out.put(k, v);
            }
        });
        return out;
    }

    /** A drop packet for what's in the stores, or null if it isn't there or breaks the limits. */
    private LinkPacket dropPacket(Map<String, Integer> goods, List<String> pieceIds, String note, long now) {
        Map<String, Integer> amounts = positive(goods);
        List<com.estatica.domain.bunker.GearPiece> pieces = stockPieces(pieceIds);
        if (pieces.size() != pieceIds.size() || !linkRules.dropAllowed(amounts, pieces.size()) || !canAfford(amounts)) {
            return null;
        }
        List<LinkPacket.Gear> packed = new ArrayList<>();
        for (com.estatica.domain.bunker.GearPiece piece : pieces) {
            packed.add(new LinkPacket.Gear(piece.getGearId(), piece.getRarity().name(), piece.getCondition()));
        }
        String cleanNote = note == null ? "" : note.trim();
        int max = content.getBalance().getLink().getNoteMaxChars();
        return LinkPacket.drop(callsign, operatorTag(), now, amounts, packed,
                cleanNote.length() > max ? cleanNote.substring(0, max) : cleanNote);
    }

    /** Takes a drop's goods and pieces out of the stores; returns the pieces' names. */
    private List<String> handOver(LinkPacket p, List<String> pieceIds, Step step) {
        pay(p.getResources(), step);
        List<String> names = new ArrayList<>();
        for (com.estatica.domain.bunker.GearPiece piece : stockPieces(pieceIds)) {
            names.add(gearName(piece));
            piece.moveTo(com.estatica.domain.bunker.GearPiece.GONE);
        }
        return names;
    }

    /** Supplies for another bunker: they leave the stores now, so they can't be sent twice. */
    @WorkerThread
    public List<LogLine> linkDrop(Map<String, Integer> goods, List<String> pieceIds, String note) {
        Step step = new Step();
        advance(step);
        LinkPacket p = dropPacket(goods, pieceIds, note, step.now);
        if (p == null) {
            linkLine("invalid", Map.of(), null, step);
            return step.commit();
        }
        List<String> names = handOver(p, pieceIds, step);
        Map<String, String> vars = new HashMap<>();
        vars.put("loot", describeDrop(p.getResources(), names));
        linkLine("dropPacked", vars, null, step);
        keepSent(p, LinkCodec.encode(p), linkText("labelDrop", vars), step);
        return step.commit();
    }

    /** Someone from the crew as another bunker will meet them (or remember them). */
    private LinkPacket personPacket(Scout s, LinkPacket.Type type, long now) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (StatType t : StatType.values()) {
            stats.put(t.key(), s.stat(t).getValue());
        }
        List<String> traits = new ArrayList<>();
        for (ScoutTrait t : s.getTraits()) {
            if (t.isVisible()) {
                traits.add(t.getTraitId());
            }
        }
        String words;
        if (type == LinkPacket.Type.LAST_WORDS) {
            words = echoWords(s);
        } else {
            words = "";
            for (Memory m : memoryRepository.forScout(s.getId())) {
                if (m.getType() != Memory.Type.GRIEF && m.getType() != Memory.Type.LAST_WORDS) {
                    words = m.getText();
                }
            }
        }
        int max = content.getBalance().getLink().getWordsMaxChars();
        return LinkPacket.person(type, callsign, operatorTag(), now, s.getName(), content.isFemale(s), stats, traits,
                words.length() > max ? words.substring(0, max) : words);
    }

    /** One of the crew, out on another bunker's roads for a while. They stay home here. */
    @WorkerThread
    public List<LogLine> linkWanderer(String scoutId) {
        Step step = new Step();
        advance(step);
        Scout s = scouts.get(scoutId);
        if (s == null || !s.isAlive()) {
            return step.commit();
        }
        LinkPacket p = personPacket(s, LinkPacket.Type.WANDERER, step.now);
        keepSent(p, LinkCodec.encode(p), linkText("labelWanderer", linkVars(p)), step);
        return step.commit();
    }

    /** Someone who died, so other bunkers hear them once. */
    @WorkerThread
    public List<LogLine> linkLastWords(String scoutId) {
        Step step = new Step();
        advance(step);
        Scout s = scouts.get(scoutId);
        if (s == null || s.isAlive()) {
            return step.commit();
        }
        LinkPacket p = personPacket(s, LinkPacket.Type.LAST_WORDS, step.now);
        keepSent(p, LinkCodec.encode(p), linkText("labelLastWords", linkVars(p)), step);
        return step.commit();
    }

    /** Shows a code sent before. */
    @WorkerThread
    public void linkShow(String id) {
        LinkSignalEntity e = linkSignals.get(id);
        if (e != null && LinkSignalEntity.OUT.equals(e.direction)) {
            shownCode = e.code;
            shownLabel = e.label;
        }
    }

    @WorkerThread
    public void linkHide() {
        shownCode = null;
        shownLabel = null;
    }

    /** A code typed, pasted or scanned. */
    @WorkerThread
    public List<LogLine> linkReceive(String code) {
        Step step = new Step();
        advance(step);
        takeLink(code, LinkSignalEntity.IN, null, step);
        return step.commit();
    }

    /** A code that came over Bluetooth from a bunker in the same room. */
    @WorkerThread
    public List<LogLine> linkReceiveNearby(String code) {
        Step step = new Step();
        advance(step);
        takeLink(code, NEAR, null, step);
        return step.commit();
    }

    private int takenToday(long now) {
        int n = 0;
        for (LinkSignalEntity e : linkSignals.values()) {
            if (LinkSignalEntity.IN.equals(e.direction) && !"BROADCAST".equals(e.type)
                    && localDay(e.atMillis) == localDay(now)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Checks a code and lets in what it carries. {@code waitAt}: where a wanderer waits (joint
     * runs), or null for the farthest place within reach. Returns the packet, or null.
     */
    private LinkPacket takeLink(String code, String direction, String waitAt, Step step) {
        Balance.Link balance = content.getBalance().getLink();
        LinkCodec.Decoded d = LinkCodec.decode(code, balance.getBroadcastKey());
        if (!d.ok()) {
            linkLine(d.error.name().toLowerCase(Locale.ROOT), Map.of(), null, step);
            return null;
        }
        LinkPacket p = linkRules.sanitize(d.packet);
        if (p == null) {
            linkLine("invalid", Map.of(), null, step);
            return null;
        }
        LinkPacket.Type type = p.getType();
        if (type != LinkPacket.Type.BROADCAST && callsign.equals(p.getCallsign())) {
            linkLine("own", Map.of(), null, step);
            return null;
        }
        if (linkSignals.containsKey(p.getId())) {
            linkLine("used", Map.of(), null, step);
            return null;
        }
        if (type == LinkPacket.Type.BROADCAST) {
            if (p.getExpiresDay() > 0 && localDay(step.now) > p.getExpiresDay()) {
                linkLine("expired", Map.of(), null, step);
                return null;
            }
        } else if (LinkSignalEntity.IN.equals(direction) && takenToday(step.now) >= balance.getDailyLimit()) {
            linkLine("limit", Map.of("limit", String.valueOf(balance.getDailyLimit())), null, step);
            return null;
        }
        LinkSignalEntity e = new LinkSignalEntity();
        e.id = p.getId();
        e.direction = direction;
        e.type = type.name();
        e.callsign = p.getCallsign();
        e.code = code.trim();
        e.atMillis = step.now;
        Map<String, String> vars = linkVars(p);
        switch (type) {
            case MESSAGE:
                e.label = linkText("labelMessage", Map.of("text", clip(p.getText(), 24)));
                receiveMessage(p, vars, step);
                break;
            case DROP:
                e.label = linkText("labelDrop", Map.of("loot", receiveDrop(p, vars, step)));
                break;
            case WANDERER:
                e.label = linkText("labelWanderer", vars);
                e.zoneId = receiveWanderer(p, waitAt, vars, step);
                e.status = e.zoneId != null ? LinkSignalEntity.WAITING : LinkSignalEntity.DONE;
                break;
            case LAST_WORDS:
                e.label = linkText("labelLastWords", vars);
                vars.put("frequency", someFrequency());
                linkLine("lastWords", vars, null, step);
                addLore(LINK_SOURCE + p.getId(), linkText("lastWordsLore", vars), step);
                unsettle(balance.getLastWordsStress(), step);
                break;
            case BROADCAST:
                e.label = linkText("labelBroadcast", vars);
                vars.put("frequency", someFrequency());
                linkLine("broadcast", vars, null, step);
                for (String line : p.getText().split("\n")) {
                    if (!line.isBlank()) {
                        step.system(line.trim(), Map.of(), null);
                    }
                }
                grant(p.getResources(), step);
                if (!p.getLore().isEmpty()) {
                    addLore(LINK_SOURCE + p.getId(), p.getLore(), step);
                }
                break;
            default:
                break;
        }
        linkSignals.put(e.id, e);
        step.radioWrites.add(() -> db.linkDao().upsert(e));
        return p;
    }

    /** A spot on an open band, for flavor. */
    private String someFrequency() {
        List<BandDef> open = new ArrayList<>();
        for (BandDef band : content.getBalance().getBands()) {
            if (isUnlocked(band)) {
                open.add(band);
            }
        }
        BandDef band = open.isEmpty() ? content.getBalance().getBands().get(0) : random.pick(open);
        return band.frequency(random.nextInt(content.getBalance().getRadio().getDialMax()));
    }

    private void receiveMessage(LinkPacket p, Map<String, String> vars, Step step) {
        if (LinkRules.PLAIN.equals(p.getScheme()) || com.estatica.domain.radio.Cipher.normalize(p.getText()).isEmpty()) {
            linkLine("messagePlain", vars, null, step);
            return;
        }
        Transmission.Kind kind = Transmission.Kind.fromName(p.getScheme());
        int shift = kind == Transmission.Kind.CAESAR && p.getShift() == 0 ? 3 : p.getShift();
        Transmission t = Transmission.create(LINK_SOURCE + p.getId(), kind, p.getText(), shift,
                content.getBalance().getBands().get(0).getId(), null, step.now);
        transmissions.put(t.getId(), t);
        step.radioWrites.add(() -> radioRepository.save(t));
        linkLine("messageCiphered", vars, null, step);
    }

    /** Into the stores; returns what came, described. */
    private String receiveDrop(LinkPacket p, Map<String, String> vars, Step step) {
        p.getResources().forEach((k, v) -> step.resourceDelta.merge(k, v, Integer::sum));
        List<String> names = new ArrayList<>();
        for (LinkPacket.Gear g : p.getGear()) {
            com.estatica.domain.bunker.GearPiece piece = new com.estatica.domain.bunker.GearPiece(Ids.newId(), g.gearId,
                    com.estatica.domain.bunker.GearPiece.Rarity.fromKey(g.rarity), g.condition,
                    com.estatica.domain.bunker.GearPiece.BUNKER, null);
            gear.put(piece.getId(), piece);
            names.add(gearName(piece));
        }
        String loot = describeDrop(p.getResources(), names);
        vars.put("loot", loot);
        linkLine("drop", vars, null, step);
        if (!p.getText().isEmpty()) {
            linkLine("dropNote", vars, null, step);
        }
        return loot;
    }

    /** Known places a team could reach now (no wilds or deep runs). */
    private List<String> reachablePlaces() {
        List<String> out = new ArrayList<>();
        for (String id : knownZones) {
            ZoneDef z = content.getZones().get(id);
            if (z != null && !z.isWilds() && !z.isEndless() && canReach(z)) {
                out.add(id);
            }
        }
        java.util.Collections.sort(out);
        return out;
    }

    private Map<String, Integer> bandIndexes(List<String> zoneIds) {
        List<BandDef> bands = content.getBalance().getBands();
        Map<String, Integer> out = new HashMap<>();
        for (String id : zoneIds) {
            for (int i = 0; i < bands.size(); i++) {
                if (bands.get(i).getId().equals(content.zone(id).getBand())) {
                    out.put(id, i);
                }
            }
        }
        return out;
    }

    /** Where they'll wait, or null if they came by the hatch and were met already. */
    private String receiveWanderer(LinkPacket p, String waitAt, Map<String, String> vars, Step step) {
        com.estatica.core.Pronouns.register(p.getName(), p.isFemale());
        List<String> places = reachablePlaces();
        String zone = waitAt != null && places.contains(waitAt) ? waitAt
                : LinkRules.waitingZone(places, bandIndexes(places));
        if (zone == null) {
            // Nowhere known yet: they come by the bunker instead.
            linkLine("wandererHome", vars, null, step);
            List<Scout> home = new ArrayList<>();
            for (Scout s : scouts.values()) {
                if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE) {
                    home.add(s);
                }
            }
            meet(p, home, null, step);
            return null;
        }
        wanderers.put(p.getId(), p);
        vars.put("zone", content.zone(zone).getName());
        vars.put("days", String.valueOf(Math.round(content.getBalance().getLink().getWanderer().getDays())));
        linkLine("wandererHeard", vars, null, step);
        return zone;
    }

    /** A team met someone from another bunker: words, a gift or a tip, and a lighter heart. */
    private void meet(LinkPacket p, List<Scout> team, String zoneId, Step step) {
        Map<String, String> vars = linkVars(p);
        vars.put("zone", zoneId != null ? content.zone(zoneId).getName() : "");
        if (!team.isEmpty() && zoneId != null) {
            vars.put("lead", team.get(0).getName());
            linkLine("wandererMet", vars, team.get(0), step);
        }
        Map<String, Integer> gift = linkRules.wandererGift(p);
        if (!gift.isEmpty()) {
            gift.forEach((k, v) -> step.resourceDelta.merge(k, v, Integer::sum));
            vars.put("loot", describe(gift));
            linkLine("wandererGift", vars, null, step);
        }
        String intelType = linkRules.wandererIntel(p);
        if (intelType != null) {
            List<String> places = reachablePlaces();
            places.remove(zoneId);
            if (!places.isEmpty()) {
                addIntel(random.pick(places), intelType, step);
            }
        }
        int relief = content.getBalance().getLink().getWanderer().getStressRelief();
        if (relief > 0 && !team.isEmpty()) {
            for (Scout s : team) {
                rules.addStress(s, -relief);
            }
            linkLine("wandererCalm", vars, null, step);
        }
        addLore(LINK_SOURCE + p.getId(), linkText("wandererLore", vars), step);
    }

    /** Back from a place where someone from another bunker was waiting. */
    private void meetWanderers(List<Scout> team, String zoneId, Step step) {
        for (LinkSignalEntity e : linkSignals.values()) {
            LinkPacket p = wanderers.get(e.id);
            if (p != null && LinkSignalEntity.WAITING.equals(e.status) && zoneId.equals(e.zoneId)) {
                meet(p, team, zoneId, step);
                settle(e, step);
            }
        }
    }

    /** Wanderers nobody met move on. */
    private void expireWanderers(Step step) {
        double days = content.getBalance().getLink().getWanderer().getDays();
        for (LinkSignalEntity e : linkSignals.values()) {
            LinkPacket p = wanderers.get(e.id);
            if (p != null && LinkSignalEntity.WAITING.equals(e.status) && step.now - e.atMillis > days * DAY) {
                Map<String, String> vars = linkVars(p);
                vars.put("zone", e.zoneId != null && content.getZones().containsKey(e.zoneId)
                        ? content.zone(e.zoneId).getName() : "");
                linkLine("wandererGone", vars, null, step);
                settle(e, step);
            }
        }
    }

    private void settle(LinkSignalEntity e, Step step) {
        e.status = LinkSignalEntity.DONE;
        wanderers.remove(e.id);
        step.radioWrites.add(() -> db.linkDao().upsert(e));
    }

    // ---- Nearby (Bluetooth): trades and joint runs ----

    /** An offer for a live trade: what's in the stores, nothing taken yet. Null if not possible. */
    @WorkerThread
    public String linkOffer(Map<String, Integer> goods, List<String> pieceIds) {
        if (positive(goods).isEmpty() && pieceIds.isEmpty()) {
            // Giving nothing is a fine offer: a gift from the other side.
            return LinkCodec.encode(LinkPacket.drop(callsign, operatorTag(), clock.nowMillis(), Map.of(), List.of(), ""));
        }
        LinkPacket p = dropPacket(goods, pieceIds, "", clock.nowMillis());
        return p != null ? LinkCodec.encode(p) : null;
    }

    /**
     * Both sides agreed: what we offered leaves, what they offered comes in. Each phone does
     * its own half; our offer is checked again in case the stores changed meanwhile.
     */
    @WorkerThread
    public List<LogLine> linkTrade(Map<String, Integer> goods, List<String> pieceIds, String theirOffer) {
        Step step = new Step();
        advance(step);
        LinkCodec.Decoded d = LinkCodec.decode(theirOffer, "");
        boolean giving = !positive(goods).isEmpty() || !pieceIds.isEmpty();
        LinkPacket mine = giving ? dropPacket(goods, pieceIds, "", step.now) : null;
        if (!d.ok() || d.packet.getType() != LinkPacket.Type.DROP || (giving && mine == null)) {
            linkLine("invalid", Map.of(), null, step);
            return step.commit();
        }
        List<String> gave = mine != null ? handOver(mine, pieceIds, step) : new ArrayList<>();
        Map<String, String> vars = new HashMap<>();
        vars.put("callsign", d.packet.getCallsign());
        vars.put("gave", describeDrop(mine != null ? mine.getResources() : Map.of(), gave));
        LinkPacket theirs = linkRules.sanitize(d.packet);
        vars.put("got", theirs != null ? receiveDrop(theirs, linkVars(theirs), step) : linkText("nothing", Map.of()));
        linkLine("tradeDone", vars, null, step);
        return step.commit();
    }

    /** Their side of a trade, described ("3 rations, helmet"). */
    @WorkerThread
    public String linkDescribeOffer(String code) {
        LinkCodec.Decoded d = LinkCodec.decode(code, "");
        LinkPacket p = d.ok() && d.packet.getType() == LinkPacket.Type.DROP ? linkRules.sanitize(d.packet) : null;
        if (p == null) {
            return linkText("nothing", Map.of());
        }
        List<String> names = new ArrayList<>();
        for (LinkPacket.Gear g : p.getGear()) {
            names.add(content.gear(g.gearId).getName().toLowerCase(Locale.ROOT));
        }
        return describeDrop(p.getResources(), names);
    }

    /** A crew member's code for a joint run (they stay home; the other bunker meets them). */
    @WorkerThread
    public String linkWandererCode(String scoutId) {
        Scout s = scouts.get(scoutId);
        if (s == null || !s.isAlive()) {
            return null;
        }
        return LinkCodec.encode(personPacket(s, LinkPacket.Type.WANDERER, clock.nowMillis()));
    }

    /**
     * A joint run: the other bunker watches the route to {@code zoneId} (a cache and dangers
     * called out) and their scout waits there to meet our team.
     */
    @WorkerThread
    public List<LogLine> linkJoint(String zoneId, String theirWanderer) {
        Step step = new Step();
        advance(step);
        if (zoneId == null || !reachablePlaces().contains(zoneId)) {
            return step.commit();
        }
        LinkPacket p = takeLink(theirWanderer, NEAR, zoneId, step);
        Map<String, String> vars = new HashMap<>();
        vars.put("callsign", p != null ? p.getCallsign() : "?");
        vars.put("zone", content.zone(zoneId).getName());
        linkLine("jointSet", vars, null, step);
        addIntel(zoneId, "CACHE", step);
        addIntel(zoneId, "DANGER", step);
        return step.commit();
    }

    private SessionSnapshot.LinkView linkView(long now) {
        Balance.Link balance = content.getBalance().getLink();
        SessionSnapshot.LinkView v = new SessionSnapshot.LinkView();
        v.callsign = callsign;
        v.takenToday = takenToday(now);
        v.dailyLimit = balance.getDailyLimit();
        v.dropMaxUnits = balance.getDropMaxUnits();
        v.dropMaxGear = balance.getDropMaxGear();
        v.messageMaxChars = balance.getMessageMaxChars();
        Map<String, String> schemeNames = content.getTerminal().getRadio().getSchemeNames();
        for (String s : LinkRules.MESSAGE_SCHEMES) {
            String name = LinkRules.PLAIN.equals(s) ? linkText("plain", Map.of()) : schemeNames.getOrDefault(s, s);
            v.schemes.add(new SessionSnapshot.LinkChoice(s, name, 0, canSendIn(s)));
        }
        for (String id : balance.getDropResources()) {
            ResourceDef r = content.getResources().get(id);
            if (r != null) {
                v.goods.add(new SessionSnapshot.LinkChoice(id, r.getName(), resources.getOrDefault(id, 0), true));
            }
        }
        for (com.estatica.domain.bunker.GearPiece piece : gear.values()) {
            if (piece.isInBunker()) {
                v.gear.add(new SessionSnapshot.LinkChoice(piece.getId(), gearLabel(piece), 1, true));
            }
        }
        v.gear.sort(Comparator.comparing(c -> c.name));
        for (Scout s : scouts.values()) {
            (s.isAlive() ? v.crew : v.fallen).add(new SessionSnapshot.LinkChoice(s.getId(), s.getName(), 0, true));
        }
        for (String id : reachablePlaces()) {
            v.places.add(new SessionSnapshot.LinkChoice(id, content.zone(id).getName(), 0, true));
        }
        for (LinkSignalEntity e : linkSignals.values()) {
            if (LinkSignalEntity.OUT.equals(e.direction)) {
                v.sent.add(0, new SessionSnapshot.LinkChoice(e.id, e.label, 0, true));
                continue;
            }
            v.received.add(0, e.callsign.isEmpty() ? e.label : e.callsign + " · " + e.label);
            LinkPacket p = wanderers.get(e.id);
            if (p != null && e.zoneId != null && content.getZones().containsKey(e.zoneId)) {
                Map<String, String> vars = linkVars(p);
                vars.put("zone", content.zone(e.zoneId).getName());
                long left = (long) (e.atMillis + balance.getWanderer().getDays() * DAY - now);
                vars.put("left", ElapsedFormatter.format(Math.max(0, left), content.getTerminal().getElapsedUnits()));
                v.waiting.add(linkText("waitingRow", vars));
            }
        }
        if (v.received.size() > 10) {
            v.received = new ArrayList<>(v.received.subList(0, 10));
        }
        v.code = shownCode;
        v.codeLabel = shownLabel;
        return v;
    }

    // ---- Read models ----

    @WorkerThread
    public SessionSnapshot snapshot() {
        registerPronouns();
        TerminalText.BunkerText text = content.getTerminal().getBunker();
        Map<String, Expedition> byScout = new HashMap<>();
        for (Expedition e : active.values()) {
            byScout.put(e.getScoutId(), e);
            if (e.getPartnerId() != null) {
                byScout.put(e.getPartnerId(), e);
            }
            if (e.getThirdId() != null) {
                byScout.put(e.getThirdId(), e);
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
            b.taskId = taskId;
            b.pace = s.isAlive() ? rules.durationMultiplier(s) : 1;
            com.estatica.domain.bunker.GearPiece ride = s.isAlive() ? rules.ride(s) : null;
            if (ride != null) {
                GearDef rideDef = content.gear(ride.getGearId());
                b.rideName = rideDef.getName();
                b.ridePace = com.estatica.domain.bunker.GearRules.durationMultiplier(rideDef, ride);
                b.rideSeats = rideDef.getSeats();
                b.rideFuel = rideDef.getFuelPerHour();
            }
            b.veteran = com.estatica.domain.scouts.RetrainRules.tier(s);
            b.canRetrain = retrainRules.canRetrain(s) && s.getStatus() == ScoutStatus.IDLE;
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
            b.female = content.isFemale(s);
            b.romance = romanceLabel(s);
            b.canGift = home && hasInventory;
            b.canAssignTask = s.isAlive();
            b.slots = new ArrayList<>();
            for (GearDef.Slot slot : GearDef.Slot.values()) {
                com.estatica.domain.bunker.GearPiece worn = s.getLife().getGear(slot);
                b.slots.add(new SessionSnapshot.SlotView(slot.name(),
                        content.getTerminal().getBase().getSlotNames().getOrDefault(slot.name(), slot.name()),
                        worn != null ? worn.getId() : null,
                        worn != null ? gearLabel(worn) + "\n" + gearEffects(worn) : null));
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
                String who = teamNames(lead, team);
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
        // Rescues and personal missions show even out of reach, so they're never forgotten.
        for (Map.Entry<String, String> target : rescueTargets.entrySet()) {
            ZoneDef z = content.zone(target.getValue());
            if (z != null) {
                Map<String, String> vars = new HashMap<>();
                ScoutTemplate waiting = rescuePersona(target.getKey());
                vars.put("name", waiting != null ? waiting.getName() : "?");
                missions.add(missionView(new SessionSnapshot.MissionView(Mission.Type.RESCUE.name(), z.getId(),
                        target.getKey(), TextTemplate.format(radioText.getMissionRescue(), vars), z.getName()), z));
            }
        }
        for (Scout s : scouts.values()) {
            for (StoryDef.PersonalMission m : content.getStory().getPersonalMissions()) {
                ZoneDef z = content.zone(m.getZone());
                if (z != null && personalAvailable(m, s)) {
                    Map<String, String> vars = new HashMap<>();
                    vars.put("title", m.getTitle());
                    missions.add(missionView(new SessionSnapshot.MissionView(Mission.Type.PERSONAL.name(), z.getId(),
                            m.getId(), TextTemplate.format(content.getTerminal().getStory().getMissionPersonal(), vars),
                            z.getName(), s.getId()), z));
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
                missions.add(missionView(new SessionSnapshot.MissionView(Mission.Type.INSTALL_RELAY.name(), z.getId(),
                        null, radioText.getMissionInstall(), z.getName()), z));
            } else if (relay != null && relay.getCondition() < 70) {
                missions.add(missionView(new SessionSnapshot.MissionView(Mission.Type.MAINTAIN_RELAY.name(), z.getId(),
                        null, radioText.getMissionMaintain(), z.getName()), z));
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
        tasks.add(new SessionSnapshot.TaskView(null, text.getNoTask(), "", null));
        for (TaskDef t : content.getTasks().values()) {
            if (isRoomOpen(t.getRoom())) {
                tasks.add(new SessionSnapshot.TaskView(t.getId(), t.getName(), t.getDescription(), t.getRoom()));
            }
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
            String who = teamNames(scouts.get(e.getScoutId()), party);
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
            if (lead != null) {
                StringBuilder who = new StringBuilder(lead.getName());
                for (int i = 1; i < e.getValue().length; i++) {
                    Scout other = e.getValue()[i] != null ? scouts.get(e.getValue()[i]) : null;
                    if (other != null) {
                        who.append(" + ").append(other.getName());
                    }
                }
                doneLogs.add(new SessionSnapshot.DoneLog(LogLine.expeditionThread(e.getKey()), who.toString()));
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
        built.deepRuns = deepRunViews();
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
        SessionSnapshot.RadioView view = new SessionSnapshot.RadioView(bands, content.getBalance().getRadio().getDialMax(),
                pendingTransmissions, gameFlags.contains(FLAG_MORSE_TABLE),
                resources.getOrDefault("relay_kit", 0), relayLines, stationViews());
        view.market = marketView(clock.nowMillis());
        view.link = linkView(clock.nowMillis());
        return view;
    }

    private SessionSnapshot.MarketView marketView(long now) {
        SessionSnapshot.MarketView m = new SessionSnapshot.MarketView();
        SignalsDef.Station station = marketStation();
        m.known = marketKnown();
        if (station == null || !m.known) {
            return m;
        }
        TerminalText.BaseText text = content.getTerminal().getBase();
        m.open = marketOpen(now);
        m.chips = resources.getOrDefault("chips", 0);
        m.reputation = reputation();
        m.courierPercent = (int) Math.round(content.getBalance().getMarket().getCourierFee() * 100);
        long local = clock.localMillis(now);
        long at = m.open ? Broadcasts.startOf(station, Broadcasts.airing(station, local))
                + station.getSchedule().getMinutes() * 60_000L : Broadcasts.nextStart(station, local);
        Map<String, String> vars = new HashMap<>();
        vars.put("time", String.format(Locale.ROOT, "%02d:%02d", Math.floorMod(at / HOUR, 24), at / 60_000 % 60));
        m.status = TextTemplate.format(m.open ? text.getMarketStatusOn() : text.getMarketStatusOff(), vars);
        com.estatica.domain.bunker.MarketRules.Day today = marketDay(now);
        long day = localDay(now);
        int rep = reputation();
        List<SessionSnapshot.TradeRow> buy = new ArrayList<>();
        for (com.estatica.domain.bunker.MarketRules.Offer offer : today.offers) {
            if (value(BOUGHT + day + ":" + offer.key()) > 0) {
                continue;
            }
            buy.add(new SessionSnapshot.TradeRow(offer.key(), offerName(offer), offer.price,
                    m.chips >= offer.price ? 1 : 0, offer.kind.name().toLowerCase(Locale.ROOT), false));
        }
        List<SessionSnapshot.TradeRow> sell = new ArrayList<>();
        for (String good : content.getBalance().getMarket().getGoods().keySet()) {
            String name = content.resource(good).getName();
            String note = today.wanted.contains(good) ? text.getMarketWanted()
                    : today.scarce.contains(good) ? text.getMarketScarce() : null;
            int price = marketRules.buyPrice(good, today, rep);
            buy.add(new SessionSnapshot.TradeRow("R:" + good, name, price, price > 0 ? m.chips / price : 0,
                    today.scarce.contains(good) ? note : null, true));
            int have = resources.getOrDefault(good, 0);
            if (have > 0) {
                sell.add(new SessionSnapshot.TradeRow("R:" + good, name,
                        marketRules.sellPrice(good, today, rep, (int) value(SOLD + day + ":" + good)), have,
                        today.wanted.contains(good) ? note : null, true));
            }
        }
        for (com.estatica.domain.bunker.GearPiece piece : gear.values()) {
            if (piece.isInBunker()) {
                sell.add(new SessionSnapshot.TradeRow("P:" + piece.getId(), gearLabel(piece),
                        marketRules.gearSellPrice(piece, rep), 1, null, false));
            }
        }
        m.buy = buy;
        m.sell = sell;
        List<String> incoming = new ArrayList<>();
        List<String> held = new ArrayList<>();
        for (Map.Entry<String, Double> e : new java.util.TreeMap<>(baseValues).entrySet()) {
            if (e.getKey().startsWith(COURIER) && e.getValue() > now) {
                String body = e.getKey().substring(COURIER.length());
                body = body.substring(body.indexOf(':') + 1);
                int qty = Integer.parseInt(body.substring(body.lastIndexOf(':') + 1));
                incoming.add(goodsName(body.substring(0, body.lastIndexOf(':')), qty) + " · "
                        + ElapsedFormatter.format((long) (e.getValue() - now), content.getTerminal().getElapsedUnits()));
            } else if (e.getKey().startsWith(HELD) && e.getValue() > 0) {
                held.add(goodsName(e.getKey().substring(HELD.length()), (int) Math.round(e.getValue())));
            }
        }
        m.incoming = incoming;
        m.held = held;
        return m;
    }

    /** "3 rations", "Plans: Motorbike", "Helmet · rare". */
    private String goodsName(String what, int qty) {
        if (what.startsWith("R:")) {
            return describe(Map.of(what.substring(2), qty));
        }
        String[] parts = what.split(":");
        if ("BLUEPRINT".equals(parts[0])) {
            Map<String, String> v = new HashMap<>();
            RecipeDef r = content.getRecipes().get(parts[1]);
            v.put("item", r != null ? r.getName() : parts[1]);
            return TextTemplate.format(content.getTerminal().getBase().getBlueprintName(), v);
        }
        GearDef def = content.gear(parts[1]);
        return (def != null ? def.getName() : parts[1]) + " · " + content.getTerminal().getBase().getRarities()
                .getOrDefault(parts[2], parts[2].toLowerCase(Locale.ROOT));
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
