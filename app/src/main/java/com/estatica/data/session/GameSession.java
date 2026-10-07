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
import com.estatica.domain.content.ZoneDef;
import com.estatica.domain.dialogue.Conversation;
import com.estatica.domain.dialogue.DialogueEngine;
import com.estatica.domain.expedition.Expedition;
import com.estatica.domain.expedition.ExpeditionEngine;
import com.estatica.domain.expedition.ExpeditionResult;
import com.estatica.domain.expedition.Mission;
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
    private com.estatica.domain.state.GameState state;
    private GameRandom random;
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
        gifts = new GiftRules(content, rules);
        grief = new GriefRules(content, rules);
        state = gameStateRepository.loadOrCreate();
        random = GameRandom.fromState(state.getRngState());
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
        for (Conversation c : bunkerRepository.pendingConversations()) {
            // Content updates can remove a dialogue (Tano's, in 0.6.3): drop it instead of crashing.
            if (content.getDialogues().containsKey(c.getDialogueId())) {
                conversations.put(c.getId(), c);
            }
        }
        affinities = bunkerRepository.affinities(content.getBalance().getAffinity().getMax());

        radio = new RadioRules(content);
        field = new SignalField(content.getBalance(), state.getRngSeed());
        gameFlags.addAll(radioRepository.flags());
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
            // New games and saves from before Phase 3: starting zones and module levels.
            for (ZoneDef z : content.getZones().values()) {
                if (z.isStartKnown() && knownZones.add(z.getId())) {
                    radioRepository.addKnownZone(z.getId(), now);
                }
            }
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
                baseRepository.saveValue(KEY_RESERVE, energy.batteryCapacity(moduleLevel("generator")));
            }
        });
        baseTick = values.containsKey(KEY_TICK) ? values.get(KEY_TICK).longValue() : now;
        reserve = values.containsKey(KEY_RESERVE) ? values.get(KEY_RESERVE)
                : energy.batteryCapacity(moduleLevel("generator"));
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
            ExpeditionResult result = engine.advance(expedition, scout,
                    memoryRepository.forScout(scout.getId()), deathContext(scout), now);
            step.expeditions.add(expedition);
            step.lines.addAll(result.getLines());
            step.memories.addAll(result.getMemories());
            result.getDelivered().forEach((k, v) -> step.resourceDelta.merge(k, v, Integer::sum));
            for (String itemId : result.getDeliveredItems()) {
                ItemInstance found = ItemInstance.found(itemId, now);
                items.put(found.getId(), found);
            }
            for (String fragment : result.getPlotFragments()) {
                foundPlot(fragment, step);
            }
            if (result.isReturned()) {
                recoverLostItems(scout, expedition.getZoneId(), step);
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
        double saving = 0;
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
        dead.getLife().setGearId(null);
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
                step.lines.add(line);
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
            step.lines.addAll(dialogues.open(scout, dialogue, true, scouts.values(), random, step.now));
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
        Step step = new Step();
        advance(step);
        Scout scout = scouts.get(scoutId);
        ZoneDef zone = content.getZones().get(zoneId);
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
        GearDef gear = rules.gearOf(scout);
        if (gear != null) {
            noise *= gear.getNoiseFactor();
        }
        Mission mission = new Mission(type, target, Math.min(1.0, noise), quality.orderDelayMillis, cache, danger);
        ExpeditionEngine.Dispatch dispatch = engine.dispatch(scout, zone, random.deriveSeed(), mission, step.now);
        step.lines.addAll(dispatch.lines);
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

    private boolean isUnlocked(BandDef band) {
        if (content.getBalance().bandIndex(band.getId()) > 0
                && !PowerGrid.antennaOn(grid(clock.nowMillis()).units("antenna"))) {
            return false; // an unpowered antenna only hears the short band
        }
        return radio.isUnlocked(band, moduleLevel("antenna"), relays.values());
    }

    /** Known and within reach. */
    private boolean canReach(ZoneDef zone) {
        return knownZones.contains(zone.getId()) && isUnlocked(content.getBalance().band(zone.getBand()));
    }

    /**
     * What the dial picks up at a position. Read-only: moving the dial changes nothing, only
     * locking on does.
     */
    @WorkerThread
    public SessionSnapshot.Tuning tune(String bandId, int position) {
        BandDef band = content.getBalance().band(bandId);
        long now = clock.nowMillis();
        if (!isUnlocked(band)) {
            return new SessionSnapshot.Tuning(0, content.getTerminal().getRadio().getStaticNoise(), false);
        }
        SignalField.Reading reading = field.tune(band, position, field.window(now), caughtSignals);
        if (reading.spot == null || reading.strength < 0.1) {
            return new SessionSnapshot.Tuning(reading.strength, content.getTerminal().getRadio().getStaticNoise(), false);
        }
        List<String> teasers = content.getSignals().getTeasers().get(reading.spot.kind.name());
        GameRandom flavor = GameRandom.fromSeed(reading.spot.key().hashCode() * 31L + position);
        String teaser = teasers.get(Math.floorMod(reading.spot.key().hashCode(), teasers.size()));
        String text = SignalDegrader.degrade(teaser, Math.max(0, 1.0 - reading.strength), flavor);
        boolean lockable = reading.strength >= content.getBalance().getRadio().getLockThreshold();
        return new SessionSnapshot.Tuning(reading.strength, text, lockable);
    }

    /** Locks onto the signal at the dial position, if it's clear enough. */
    @WorkerThread
    public List<LogLine> capture(String bandId, int position) {
        Step step = new Step();
        advance(step);
        BandDef band = content.getBalance().band(bandId);
        SignalField.Spot spot = isUnlocked(band)
                ? field.lock(band, position, field.window(step.now), caughtSignals) : null;
        double cost = content.getBalance().getEnergy().getSweepCost();
        if (spot != null && reserve < cost) {
            step.system(content.getTerminal().getBase().getReserveLow(), Map.of(), null);
            return step.commit();
        }
        if (spot != null) {
            count("sweeps", step);
            reserve -= cost;
            step.radioWrites.add(() -> baseRepository.saveValue(KEY_RESERVE, reserve));
        }
        if (spot == null) {
            step.system(content.getTerminal().getRadio().getNothingThere(), Map.of(), null);
        } else {
            receive(spot, step);
        }
        return step.commit();
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
        caughtSignals.add(spot.key());
        step.radioWrites.add(() -> radioRepository.addCaughtSignal(spot.key(), step.now));
        boolean roomForSurvivor = rescueTargets.size() < content.getBalance().getPeople().getMaxPendingRescues();
        RadioRules.Outcome outcome = radio.resolve(spot, knownZones, roomForSurvivor, receivedTransmissions, random);
        TerminalText.RadioText text = content.getTerminal().getRadio();
        Map<String, String> vars = new HashMap<>();
        switch (outcome.kind) {
            case ZONE: {
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
                        random, step.now);
                transmissions.put(t.getId(), t);
                receivedTransmissions.add(def.getId());
                step.radioWrites.add(() -> radioRepository.save(t));
                step.system(text.getTransmissionFound(), vars, null);
                break;
            }
            default: {
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
                    String type = def.getReward().name();
                    String zoneId = t.getZoneId();
                    intel.computeIfAbsent(zoneId, k -> new HashMap<>()).put(type, "pending");
                    step.radioWrites.add(() -> {
                        String id = radioRepository.addIntel(zoneId, type, step.now);
                        intel.get(zoneId).put(type, id);
                    });
                    vars.put("zone", content.zone(zoneId).getName());
                    step.system(def.getReward() == SignalsDef.Transmission.Reward.CACHE
                            ? text.getIntelCache() : text.getIntelDanger(), vars, null);
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
            reserve = Math.min(energy.batteryCapacity(moduleLevel("generator")),
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
        RaidRules.Result result = raids.resolve(scouts.values(), stock, moduleLevel("antenna"), random);
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
        int bestCraft = 0;
        for (Scout s : scouts.values()) {
            if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE && "taller".equals(s.getLife().getTaskId())) {
                bestCraft = Math.max(bestCraft, rules.effectiveStat(s, StatType.CRAFT));
            }
        }
        return base * (1 + 0.05 * bestCraft);
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
            step.resourceDelta.merge(recipe.getOutput(), recipe.getAmount(), Integer::sum);
            Map<String, String> vars = new HashMap<>();
            vars.put("item", recipe.getName());
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
            reserve = Math.min(energy.batteryCapacity(moduleLevel("generator")), reserve + energy.getRechargePerBattery());
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

    /** Equips gear from stock ({@code gearId} null to take it off). The old one goes back to stock. */
    @WorkerThread
    public List<LogLine> equip(String scoutId, String gearId) {
        Step step = new Step();
        advance(step);
        Scout scout = scouts.get(scoutId);
        if (scout == null || !scout.isAlive() || scout.getStatus() != ScoutStatus.IDLE) {
            return step.commit();
        }
        String current = scout.getLife().getGearId();
        if (gearId != null && content.gear(gearId) == null) {
            return step.commit();
        }
        if (gearId != null && !pay(Map.of(gearId, 1), step)) {
            return step.commit();
        }
        if (current != null) {
            step.resourceDelta.merge(current, 1, Integer::sum);
        }
        scout.getLife().setGearId(gearId);
        Map<String, String> vars = new HashMap<>();
        vars.put("name", scout.getName());
        if (gearId != null) {
            vars.put("gear", content.gear(gearId).getName().toLowerCase(Locale.ROOT));
            step.system(content.getTerminal().getBase().getEquipped(), vars, scout);
        } else {
            step.system(content.getTerminal().getBase().getUnequipped(), vars, scout);
        }
        return step.commit();
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
                    m.getMaxLevel(), cost != null ? describe(cost) : null, cost != null && canAfford(cost)));
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
        for (GearDef gear : content.getGear().values()) {
            int count = resources.getOrDefault(gear.getId(), 0);
            if (count > 0) {
                gearStock.add(new SessionSnapshot.ItemView(gear.getId(), gear.getName() + " ×" + count,
                        gear.getDescription()));
            }
        }
        return new SessionSnapshot.BaseView(goalViews(now), !g.cut.isEmpty(), energy.watts(moduleLevel("generator")), g.available, g.used,
                events.blackoutWattsAt(now) > 0, g.night, g.filtersShort, consumers,
                (int) Math.floor(reserve), (int) energy.batteryCapacity(moduleLevel("generator")),
                resources.getOrDefault("batteries", 0) > 0, modules, moduleLevel("workshop"), jobs, recipes,
                guardNames, raids.defense(guards), gearStock, aliveScouts(), rosterCap());
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
            out.add(new SessionSnapshot.GoalView(g.getLabel(),
                    (int) Math.min(g.getTarget(), value("obj:d:" + day + ":" + g.getCounter())), g.getTarget(), false));
        }
        StoryDef.Goal w = objectives.getWeekly();
        out.add(new SessionSnapshot.GoalView(w.getLabel(),
                (int) Math.min(w.getTarget(), value("obj:w:" + week(day) + ":" + w.getCounter())), w.getTarget(), true));
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
            vars.put("hours", String.valueOf((readyAt - step.now + HOUR - 1) / HOUR));
            step.system(content.getTerminal().getBunker().getTalkCooldown(), vars, scout);
            return step.commit();
        }
        DialogueDef dialogue = dialogues.pick(scout, false, scouts.values(), random, step.now);
        if (dialogue != null) {
            step.lines.addAll(dialogues.open(scout, dialogue, false, scouts.values(), random, step.now));
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
            step.lines.addAll(answer.lines);
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
            step.lines.addAll(result.lines);
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
                + content.getBalance().getBond().getTalkCooldownHours() * HOUR;
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
            b.canGift = home && hasInventory;
            b.canAssignTask = s.isAlive();
            GearDef gear = rules.gearOf(s);
            b.gear = gear != null ? gear.getName() : null;
            b.canEquip = home;
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
            for (Scout other : scouts.values()) {
                String relation = other == s || !other.isAlive() || !s.isAlive()
                        ? null : bunker.relationship(affinities, s, other);
                if (relation != null) {
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
            List<String> labels = engine.pendingOptions(e, scouts.get(e.getScoutId()));
            if (!labels.isEmpty()) {
                orders.add(new SessionSnapshot.PendingDecision(SessionSnapshot.PendingDecision.Kind.ORDER,
                        e.getId(), scouts.get(e.getScoutId()).getName(), labels, engine.deadline(e)));
            }
        }
        orders.sort(Comparator.comparingLong(p -> p.deadlineMillis));
        List<SessionSnapshot.PendingDecision> pending = new ArrayList<>(orders);
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
                    c.getId(), scout.getName(), labels, expiresAt));
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
            zones.add(new SessionSnapshot.ZoneView(z.getId(), z.getName(), z.getDescription(),
                    z.getDanger(), z.getDurationMinutes()[0], z.getDurationMinutes()[1],
                    content.getBalance().band(z.getBand()).getName(),
                    relay != null ? (int) Math.round(relay.getCondition()) : -1,
                    intel.containsKey(z.getId())));
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
                    r.getShortName()));
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
            expeditionViews.add(new SessionSnapshot.ExpeditionView(scouts.get(e.getScoutId()).getName(),
                    content.zone(e.getZoneId()).getName(), percent, end, e.isAwaitingDecision(),
                    e.getPendingSinceMillis() != Expedition.NONE && !e.isAwaitingDecision()));
        }
        return new SessionSnapshot(scoutViews, pending, zones, resourceViews, inventory, tasks, missions,
                radioView(), baseView(now), expeditionViews,
                food.daysLeft(mouths(), kitchenSaving(), resources.getOrDefault("rations", 0), foodCarryNow(now)),
                value(KEY_HUNGRY) > 0, nextWake);
    }

    private SessionSnapshot.RadioView radioView() {
        List<SessionSnapshot.BandView> bands = new ArrayList<>();
        for (BandDef band : content.getBalance().getBands()) {
            bands.add(new SessionSnapshot.BandView(band.getId(), band.getName(), isUnlocked(band)));
        }
        List<SessionSnapshot.TransmissionView> pendingTransmissions = new ArrayList<>();
        for (Transmission t : transmissions.values()) {
            pendingTransmissions.add(new SessionSnapshot.TransmissionView(t.getId(), t.getKind().name(),
                    t.cipherText(), t.getBandId()));
        }
        List<String> relayLines = new ArrayList<>();
        for (Relay relay : relays.values()) {
            relayLines.add(content.zone(relay.getZoneId()).getName() + " " + Math.round(relay.getCondition()) + "%");
        }
        return new SessionSnapshot.RadioView(bands, content.getBalance().getRadio().getDialMax(),
                pendingTransmissions, gameFlags.contains(FLAG_MORSE_TABLE),
                resources.getOrDefault("relay_kit", 0), relayLines);
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
