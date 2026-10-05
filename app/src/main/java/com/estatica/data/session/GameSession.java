package com.estatica.data.session;

import androidx.annotation.WorkerThread;

import com.estatica.core.GameClock;
import com.estatica.core.GameRandom;
import com.estatica.core.TextTemplate;
import com.estatica.data.content.ContentRepository;
import com.estatica.data.db.EstaticaDatabase;
import com.estatica.data.repo.BunkerRepository;
import com.estatica.data.repo.ExpeditionRepository;
import com.estatica.data.repo.GameStateRepository;
import com.estatica.data.repo.LogRepository;
import com.estatica.data.repo.MemoryRepository;
import com.estatica.data.repo.ResourceRepository;
import com.estatica.data.repo.ScoutRepository;
import com.estatica.domain.bunker.BunkerRules;
import com.estatica.domain.bunker.ItemInstance;
import com.estatica.domain.content.Balance;
import com.estatica.domain.content.DialogueDef;
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

    @Inject
    GameSession(EstaticaDatabase db, ContentRepository contentRepository,
                GameStateRepository gameStateRepository, ScoutRepository scoutRepository,
                ExpeditionRepository expeditionRepository, MemoryRepository memoryRepository,
                ResourceRepository resourceRepository, BunkerRepository bunkerRepository,
                LogRepository logRepository, GameClock clock) {
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
        dialogues = new DialogueEngine(content, rules);
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
                for (ScoutTemplate template : content.getStarters()) {
                    Scout scout = ScoutFactory.fromTemplate(template, content.getBalance().getScouts(), now);
                    scoutRepository.insertNew(scout);
                    loaded.add(scout);
                }
            });
        }
        for (Scout scout : loaded) {
            scouts.put(scout.getId(), scout);
        }
        for (Expedition expedition : expeditionRepository.loadActive()) {
            active.put(expedition.getId(), expedition);
        }
        resources.putAll(resourceRepository.all());
        for (ItemInstance item : bunkerRepository.items()) {
            items.put(item.getId(), item);
        }
        for (Conversation c : bunkerRepository.pendingConversations()) {
            conversations.put(c.getId(), c);
        }
        affinities = bunkerRepository.affinities(content.getBalance().getAffinity().getMax());
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

    /** "Mara (en el búnker), Tano (afuera)…" for terminal texts. */
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

        void system(String template, Map<String, String> vars, Scout scout) {
            lines.add(LogLine.draft(now, LogChannel.SYSTEM, null, TextTemplate.format(template, vars),
                    scout != null ? scout.getId() : null));
        }

        List<LogLine> commit() {
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
                bunkerRepository.saveItems(items.values());
                bunkerRepository.saveAffinities(affinities);
                for (Conversation c : conversationsChanged) {
                    bunkerRepository.save(c);
                }
                gameStateRepository.saveRngState(random.getState());
            });
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
            if (result.isReturned()) {
                recoverLostItems(scout, expedition.getZoneId(), step);
            }
            if (result.isScoutDied()) {
                afterDeath(scout, expedition.getZoneId(), step);
            }
        }

        BunkerRules.Result life = bunker.advance(scouts.values(), affinities, resources, now);
        step.lines.addAll(life.lines);
        life.resourceDelta.forEach((k, v) -> step.resourceDelta.merge(k, v, Integer::sum));

        expireConversations(step);
        maybeStartConversation(step);
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

    /** GDD §5.5 "después": belongings stay in the zone, the others grieve, the task is left. */
    private void afterDeath(Scout dead, String zoneId, Step step) {
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
                    TextTemplate.format("{item} de {dead}", vars), item.getItemId(), step.now));
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

    /** At most one scout starts a conversation per step ("¿tenés un minuto?"). */
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

    /** Sends a scout out. Catches up first so the action happens on current state. */
    @WorkerThread
    public List<LogLine> dispatch(String scoutId, String zoneId) {
        Step step = new Step();
        advance(step);
        Scout scout = scouts.get(scoutId);
        if (scout != null && rules.canDispatch(scout)) {
            ExpeditionEngine.Dispatch dispatch = engine.dispatch(scout, content.zone(zoneId),
                    random.deriveSeed(), step.now);
            step.lines.addAll(dispatch.lines);
            step.newExpeditions.add(dispatch.expedition);
            active.put(dispatch.expedition.getId(), dispatch.expedition);
        }
        return step.commit();
    }

    /** The operator answers a situation. Ignored if it already timed out. */
    @WorkerThread
    public List<LogLine> order(String expeditionId, int option) {
        Step step = new Step();
        advance(step);
        Expedition expedition = active.get(expeditionId);
        if (expedition != null && engine.choose(expedition, option, step.now)) {
            advance(step);
        }
        return step.commit();
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
            DialogueEngine.Answer answer = dialogues.answer(scout, dialogue, choice, scouts.values(), step.now);
            step.lines.addAll(answer.lines);
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
            ScoutTemplate template = content.starter(s.getTemplateId());
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
            List<String> labels = engine.pendingOptions(e);
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
            Scout dead = DialogueEngine.lastDead(scout, scouts.values());
            Map<String, String> vars = new HashMap<>();
            vars.put("name", scout.getName());
            if (dead != null) {
                vars.put("dead", dead.getName());
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
            zones.add(new SessionSnapshot.ZoneView(z.getId(), z.getName(), z.getDescription(),
                    z.getDanger(), z.getDurationMinutes()[0], z.getDurationMinutes()[1]));
        }

        List<SessionSnapshot.ResourceView> resourceViews = new ArrayList<>();
        for (ResourceDef r : content.getResources().values()) {
            resourceViews.add(new SessionSnapshot.ResourceView(r.getName(), resources.getOrDefault(r.getId(), 0)));
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
        return new SessionSnapshot(scoutViews, pending, zones, resourceViews, inventory, tasks, nextWake);
    }

    /** The Diary: one entry per fallen scout, with their story and last transmission. */
    @WorkerThread
    public List<SessionSnapshot.DiaryEntry> diary() {
        TerminalText.BunkerText text = content.getTerminal().getBunker();
        SimpleDateFormat dateFormat = new SimpleDateFormat("d/M/yyyy HH:mm", Locale.forLanguageTag("es"));
        List<SessionSnapshot.DiaryEntry> out = new ArrayList<>();
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
            ScoutTemplate template = content.starter(s.getTemplateId());
            out.add(new SessionSnapshot.DiaryEntry(s.getName(), TextTemplate.format(text.getDiaryFell(), vars),
                    template != null ? template.getBio() : null, memories, lastWords));
        }
        out.sort(Comparator.comparing(e -> e.name));
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
