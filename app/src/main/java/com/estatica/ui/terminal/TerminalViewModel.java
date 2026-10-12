package com.estatica.ui.terminal;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.estatica.core.GameClock;
import com.estatica.core.TextTemplate;
import com.estatica.data.repo.LogRepository;
import com.estatica.data.session.GameSession;
import com.estatica.data.session.SessionSnapshot;
import com.estatica.data.settings.Settings;
import com.estatica.di.AppModule;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.TerminalText;
import com.estatica.domain.expedition.Mission;
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;
import com.estatica.domain.terminal.CommandInterpreter;
import com.estatica.domain.time.ElapsedFormatter;
import com.estatica.work.NotificationScheduler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Terminal screen. Game work runs on {@link #game} through {@link GameSession}; everything
 * else here is main-thread only.
 */
@HiltViewModel
public final class TerminalViewModel extends ViewModel {

    /** Small margin so the wake-up lands after the due time, not just before. */
    private static final long WAKE_SLACK_MILLIS = 50;

    private final GameSession session;
    private final NotificationScheduler notifications;
    private final Settings settings;
    private final MutableLiveData<Boolean> soundOn = new MutableLiveData<>();
    private final MutableLiveData<OptionsState> options = new MutableLiveData<>();
    /** Seq of the latest urgent line revealed, whatever view is open (beep + vibration). */
    private final MutableLiveData<Long> alerts = new MutableLiveData<>();
    /** What the player typed lately, newest last (for suggestions). */
    private final List<String> history = new ArrayList<>();
    /** New game: the next thing typed is the operator's name. */
    private boolean awaitingName;
    /** Base typewriter delay from balance.json; the options speed scales it. */
    private int baseCharMillis;
    private final LogRepository logRepository;
    private final GameClock clock;
    private final ExecutorService game;
    private final String appVersion;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable wake = () -> run(this::tick);

    private final MutableLiveData<TerminalUiState> state = new MutableLiveData<>();
    private final MutableLiveData<SessionSnapshot> snapshot = new MutableLiveData<>();
    private final MutableLiveData<List<SessionSnapshot.DiaryEntry>> diary = new MutableLiveData<>();
    private final MutableLiveData<SessionSnapshot.Tuning> tuning = new MutableLiveData<>();
    /** Latest dial position asked for ("band:position"); older requests are skipped. */
    private final AtomicReference<String> tuneRequest = new AtomicReference<>();
    private final AtomicBoolean tuneScheduled = new AtomicBoolean();
    private final List<LogLine> window = new ArrayList<>();
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private long typingSeq = TerminalUiState.NO_TYPING;
    /** What the COMMS screen is showing. */
    private LogView view = LogView.SYSTEM;
    private String focus;
    /** Newest seq the player has seen, per view, and per thread (expedition or chat). */
    private final long[] seenSeq = new long[LogView.values().length];
    private final Map<String, Long> seenThread = new HashMap<>();
    private long seenBaseline;
    private int windowMax;
    private int pageSize;
    private int charMillis;
    private boolean ready;
    private boolean hasOlder;
    private boolean loadingOlder;
    private boolean followingTail = true;
    private boolean foreground = true;

    /** A line waiting to be shown; {@code animate} = typewriter, else it appears at once. */
    private static final class Pending {
        final LogLine line;
        final boolean animate;

        Pending(LogLine line, boolean animate) {
            this.line = line;
            this.animate = animate;
        }
    }

    /** Lines produced by one game step, and the state after it. */
    private static final class Step {
        final List<LogLine> instant = new ArrayList<>();
        final List<LogLine> animated = new ArrayList<>();
        SessionSnapshot snapshot;
    }

    private interface GameWork {
        void run(Step step);
    }

    @Inject
    TerminalViewModel(GameSession session, NotificationScheduler notifications, Settings settings,
                      LogRepository logRepository, GameClock clock, ExecutorService game,
                      @Named(AppModule.APP_VERSION) String appVersion) {
        this.session = session;
        this.notifications = notifications;
        this.settings = settings;
        soundOn.setValue(settings.isSoundOn());
        this.logRepository = logRepository;
        this.clock = clock;
        this.game = game;
        this.appVersion = appVersion;
        game.execute(this::startSession);
    }

    LiveData<Boolean> getSoundOn() {
        return soundOn;
    }

    String appVersion() {
        return appVersion;
    }

    LiveData<Long> getAlerts() {
        return alerts;
    }

    LiveData<OptionsState> getOptions() {
        return options;
    }

    private void publishOptions(String name) {
        options.setValue(new OptionsState(name, settings.isSoundOn(), settings.isHapticsOn(),
                settings.areNotificationsOn(), settings.getTextSpeed(), settings.isCrtOn()));
    }

    private void refreshOptions() {
        game.execute(() -> {
            String name = session.operatorName();
            main.post(() -> publishOptions(name));
        });
    }

    private int scaledCharMillis() {
        int speed = Math.max(0, Math.min(OptionsState.TEXT_SPEEDS.length - 1, settings.getTextSpeed()));
        return Math.max(1, (int) Math.round(baseCharMillis * OptionsState.TEXT_SPEEDS[speed]));
    }

    // ---- Options (device settings and the operator's name) ----

    void toggleSoundOption() {
        soundOn.setValue(settings.toggleSound());
        refreshOptions();
    }

    void toggleNotificationsOption() {
        if (!settings.toggleNotifications()) {
            notifications.cancelAll();
        }
        refreshOptions();
    }

    void toggleHapticsOption() {
        settings.toggleHaptics();
        refreshOptions();
    }

    void cycleTextSpeed() {
        settings.setTextSpeed((settings.getTextSpeed() + 1) % OptionsState.TEXT_SPEEDS.length);
        charMillis = scaledCharMillis();
        refreshOptions();
    }

    void toggleCrtOption() {
        settings.toggleCrt();
        refreshOptions();
    }

    /** From the options screen. */
    void rename(String name) {
        if (!ready) {
            return;
        }
        run(step -> {
            String set = session.setOperatorName(name);
            Map<String, String> vars = baseVars();
            vars.put("operator", set != null ? set : "");
            step.animated.addAll(session.log(systemLines(
                    List.of(session.content().getTerminal().getNameChanged()), vars)));
            main.post(() -> publishOptions(set));
        });
    }

    LiveData<TerminalUiState> getState() {
        return state;
    }

    LiveData<SessionSnapshot> getSnapshot() {
        return snapshot;
    }

    LiveData<List<SessionSnapshot.DiaryEntry>> getDiary() {
        return diary;
    }

    LiveData<SessionSnapshot.Tuning> getTuning() {
        return tuning;
    }

    /** Dial moved. Cheap and read-only; while dragging only the latest position is computed. */
    void tune(String bandId, int position) {
        if (!ready) {
            return;
        }
        tuneRequest.set(bandId + ":" + position);
        if (tuneScheduled.compareAndSet(false, true)) {
            game.execute(() -> {
                tuneScheduled.set(false);
                String[] request = tuneRequest.get().split(":");
                SessionSnapshot.Tuning result = session.tune(request[0], Integer.parseInt(request[1]));
                main.post(() -> tuning.setValue(result));
            });
        }
    }

    void capture(String bandId, int position) {
        if (ready) {
            run(step -> step.animated.addAll(session.capture(bandId, position)));
        }
    }

    void decode(String transmissionId, String answer) {
        if (ready) {
            run(step -> step.animated.addAll(session.decode(transmissionId, answer)));
        }
    }

    void setPower(String consumerId, int units) {
        if (ready) {
            run(step -> step.animated.addAll(session.setPower(consumerId, units)));
        }
    }

    void recharge() {
        if (ready) {
            run(step -> step.animated.addAll(session.recharge()));
        }
    }

    void craft(String recipeId) {
        if (ready) {
            run(step -> step.animated.addAll(session.craft(recipeId)));
        }
    }

    void equip(String scoutId, String gearId) {
        if (ready) {
            run(step -> step.animated.addAll(session.equip(scoutId, gearId)));
        }
    }

    void scoutSector(String scoutId, String sectorId) {
        if (ready) {
            run(step -> step.animated.addAll(session.scoutSector(scoutId, sectorId)));
        }
    }

    /** Completions for the console input, from the latest snapshot. */
    List<String> suggest(String typed) {
        return com.estatica.data.session.ConsoleCommands.suggest(typed, snapshot.getValue(), history);
    }

    void recall(String scoutId) {
        if (ready) {
            run(step -> step.animated.addAll(session.recall(scoutId)));
        }
    }

    void unequip(String scoutId, String slot) {
        if (ready) {
            run(step -> step.animated.addAll(session.unequip(scoutId, slot)));
        }
    }

    void repairGear(String pieceId) {
        if (ready) {
            run(step -> step.animated.addAll(session.repairGear(pieceId)));
        }
    }

    void upgradeModule(String moduleId) {
        if (ready) {
            run(step -> step.animated.addAll(session.upgradeModule(moduleId)));
        }
    }

    void dispatch(String scoutId, String zoneId, Mission.Type type, String target) {
        dispatch(scoutId, zoneId, type, target, null);
    }

    void dispatch(String scoutId, String zoneId, Mission.Type type, String target, String partnerId) {
        if (ready) {
            run(step -> step.animated.addAll(session.dispatch(scoutId, zoneId, type, target, partnerId)));
        }
    }

    // ---- Game executor ----

    private void startSession() {
        session.start();
        GameContent content = session.content();
        TerminalText text = content.getTerminal();
        int windowLines = content.getBalance().getUi().getLogWindowLines();
        List<LogLine> recent = logRepository.latest(windowLines);

        Map<String, String> vars = baseVars();
        vars.put("elapsed", ElapsedFormatter.format(
                clock.nowMillis() - session.lastSeenAtMillis(), text.getElapsedUnits()));
        List<String> templates = new ArrayList<>(session.takeNewGame()
                ? text.getBoot()
                : session.random().pick(text.getReconnect()));
        String name = session.operatorName();
        if (name == null) {
            templates.addAll(text.getAskName());
        }
        Step step = new Step();
        step.animated.addAll(session.log(systemLines(templates, vars)));
        // What happened while the app was closed is dumped at once (GDD §4.3).
        step.instant.addAll(session.catchUp());
        session.markSeen();
        step.snapshot = session.snapshot();

        int page = content.getBalance().getUi().getLogPageSize();
        int typeMillis = content.getBalance().getUi().getTypewriterCharMillis();
        boolean older = recent.size() == windowLines;
        main.post(() -> {
            awaitingName = name == null;
            baseCharMillis = typeMillis;
            publishOptions(name);
            onSessionLoaded(recent, step, windowLines, page, scaledCharMillis(), older);
        });
    }

    private void tick(Step step) {
        step.animated.addAll(session.catchUp());
    }

    private Map<String, String> baseVars() {
        Map<String, String> vars = new HashMap<>();
        vars.put("version", appVersion);
        String name = session.operatorName();
        vars.put("operator", name != null ? name : session.content().getTerminal().getDefaultOperatorName());
        vars.put("operatorId", session.operatorId().substring(0, 8).toUpperCase(Locale.ROOT));
        vars.put("age", ElapsedFormatter.format(clock.nowMillis() - session.createdAtMillis(),
                session.content().getTerminal().getElapsedUnits()));
        vars.put("scouts", session.describeScouts());
        vars.put("resources", session.describeResources());
        return vars;
    }

    private List<LogLine> systemLines(List<String> templates, Map<String, String> vars) {
        long now = clock.nowMillis();
        List<LogLine> lines = new ArrayList<>(templates.size());
        for (String template : templates) {
            lines.add(LogLine.draft(now, LogChannel.SYSTEM, null, TextTemplate.format(template, vars)));
        }
        return lines;
    }

    /** Runs a game step on the game executor and applies its result on the main thread. */
    private void run(GameWork work) {
        game.execute(() -> {
            Step step = new Step();
            work.run(step);
            step.snapshot = session.snapshot();
            main.post(() -> apply(step));
        });
    }

    // ---- Main thread: actions ----

    void submitCommand(String input) {
        String command = input.trim();
        if (!ready || command.isEmpty()) {
            return;
        }
        if (awaitingName) {
            awaitingName = false;
            run(step -> {
                List<LogLine> drafts = new ArrayList<>();
                drafts.add(LogLine.draft(clock.nowMillis(), LogChannel.OPERATOR, null, command));
                String set = session.setOperatorName(command);
                Map<String, String> vars = baseVars();
                drafts.addAll(systemLines(session.content().getTerminal().getNameSet(), vars));
                step.animated.addAll(session.log(drafts));
                main.post(() -> publishOptions(set));
            });
            return;
        }
        history.remove(command);
        history.add(command);
        if (history.size() > 20) {
            history.remove(0);
        }
        String first = command.split("\\s+")[0];
        if ("clear".equals(com.estatica.data.session.ConsoleCommands.command(first))) {
            // Clears the screen, not the log: older lines are still there when you scroll up.
            window.clear();
            hasOlder = true;
            publish();
            return;
        }
        String setting = applySetting(command);
        if (setting != null) {
            refreshOptions();
        }
        if (setting == null && com.estatica.data.session.ConsoleCommands.command(first) != null) {
            run(step -> {
                step.animated.addAll(session.catchUp());
                List<LogLine> drafts = new ArrayList<>();
                drafts.add(LogLine.draft(clock.nowMillis(), LogChannel.OPERATOR, null, command));
                List<LogLine> answer = new com.estatica.data.session.ConsoleCommands(session).run(command, clock.nowMillis());
                if (answer == null) {
                    CommandInterpreter interpreter = new CommandInterpreter(session.content().getTerminal());
                    drafts.addAll(systemLines(interpreter.respond(command, baseVars()), Map.of()));
                    step.animated.addAll(session.log(drafts));
                } else {
                    step.animated.addAll(session.log(drafts));
                    // Actions (send, decode…) already saved their lines; read-outs are new drafts.
                    List<LogLine> unsaved = new ArrayList<>();
                    for (LogLine l : answer) {
                        if (l.getSeq() == LogLine.UNSAVED) {
                            unsaved.add(l);
                        } else {
                            step.animated.add(l);
                        }
                    }
                    step.animated.addAll(session.log(unsaved));
                }
            });
            return;
        }
        run(step -> {
            step.animated.addAll(session.catchUp());
            List<LogLine> drafts = new ArrayList<>();
            drafts.add(LogLine.draft(clock.nowMillis(), LogChannel.OPERATOR, null, command));
            if (setting != null) {
                TerminalText.StoryText text = session.content().getTerminal().getStory();
                String line = "sound:on".equals(setting) ? text.getSoundOn()
                        : "sound:off".equals(setting) ? text.getSoundOff()
                        : "notifications:on".equals(setting) ? text.getNotificationsOn() : text.getNotificationsOff();
                drafts.add(LogLine.draft(clock.nowMillis(), LogChannel.SYSTEM, null, line));
            } else {
                CommandInterpreter interpreter = new CommandInterpreter(session.content().getTerminal());
                drafts.addAll(systemLines(interpreter.respond(command, baseVars()), Map.of()));
            }
            step.animated.addAll(session.log(drafts));
        });
    }

    /** Device settings typed in the terminal (GDD §9). Returns what changed, or null. */
    private String applySetting(String command) {
        String normalized = command.toLowerCase(Locale.ROOT).trim();
        if (normalized.equals("sound")) {
            boolean on = settings.toggleSound();
            soundOn.setValue(on);
            return on ? "sound:on" : "sound:off";
        }
        if (normalized.equals("notifications")) {
            boolean on = settings.toggleNotifications();
            if (!on) {
                notifications.cancelAll();
            }
            return on ? "notifications:on" : "notifications:off";
        }
        return null;
    }

    void dispatch(String scoutId, String zoneId) {
        if (ready) {
            run(step -> step.animated.addAll(session.dispatch(scoutId, zoneId)));
        }
    }

    void order(String expeditionId, int option) {
        if (ready) {
            run(step -> step.animated.addAll(session.order(expeditionId, option)));
        }
    }

    void talk(String scoutId) {
        if (ready) {
            run(step -> step.animated.addAll(session.talk(scoutId)));
        }
    }

    void treat(String offerId, int choice) {
        if (ready) {
            run(step -> step.animated.addAll(session.treat(offerId, choice)));
        }
    }

    void answer(String conversationId, int choice) {
        if (ready) {
            run(step -> step.animated.addAll(session.answer(conversationId, choice)));
        }
    }

    void gift(String scoutId, String itemInstanceId) {
        if (ready) {
            run(step -> step.animated.addAll(session.gift(scoutId, itemInstanceId)));
        }
    }

    void assignTask(String scoutId, String taskId) {
        if (ready) {
            run(step -> step.animated.addAll(session.assignTask(scoutId, taskId)));
        }
    }

    /** Loads the Diary on demand (reads memories from Room). */
    void loadDiary() {
        game.execute(() -> {
            session.start();
            List<SessionSnapshot.DiaryEntry> entries = session.diary();
            main.post(() -> diary.setValue(entries));
        });
    }

    void onForeground() {
        foreground = true;
        notifications.cancelAll();
        if (ready) {
            run(this::tick);
        }
    }

    void onBackground() {
        foreground = false;
        main.removeCallbacks(wake);
        game.execute(() -> {
            session.start();
            session.markSeen();
            notifications.schedule(session.notices(), clock.nowMillis());
        });
    }

    /** Called by the UI when the typewriter finishes (or the player skips) a line. */
    void onTypingFinished(long seq) {
        if (seq == typingSeq) {
            revealNext();
        }
    }

    /** Whether the player is looking at the newest lines (enables trimming the window). */
    void setFollowingTail(boolean following) {
        followingTail = following;
    }

    /** Pages older lines from Room when the player scrolls to the top. */
    void loadOlder() {
        if (!ready || loadingOlder || !hasOlder || window.isEmpty()) {
            return;
        }
        loadingOlder = true;
        long oldestSeq = window.get(0).getSeq();
        int limit = pageSize;
        game.execute(() -> {
            List<LogLine> older = logRepository.before(oldestSeq, limit);
            main.post(() -> {
                loadingOlder = false;
                hasOlder = older.size() == limit;
                window.addAll(0, older);
                publish();
            });
        });
    }

    // ---- Main thread: state ----

    private void onSessionLoaded(List<LogLine> recent, Step step, int windowMax, int pageSize,
                                 int charMillis, boolean hasOlder) {
        this.windowMax = windowMax;
        this.pageSize = pageSize;
        this.charMillis = charMillis;
        this.hasOlder = hasOlder;
        window.addAll(recent);
        // What was there before this session counts as seen.
        long newest = recent.isEmpty() ? 0 : recent.get(recent.size() - 1).getSeq();
        java.util.Arrays.fill(seenSeq, newest);
        seenBaseline = newest;
        ready = true;
        apply(step);
    }

    private void apply(Step step) {
        for (LogLine line : step.animated) {
            pending.add(new Pending(line, line.getChannel() != LogChannel.OPERATOR));
        }
        for (LogLine line : step.instant) {
            pending.add(new Pending(line, false));
        }
        snapshot.setValue(step.snapshot);
        scheduleWake(step.snapshot.nextWakeAtMillis);
        if (typingSeq == TerminalUiState.NO_TYPING) {
            revealNext();
        } else {
            publish();
        }
    }

    private void scheduleWake(long atMillis) {
        main.removeCallbacks(wake);
        if (!foreground || atMillis < 0) {
            return;
        }
        long delay = Math.max(0, atMillis - clock.nowMillis()) + WAKE_SLACK_MILLIS;
        main.postDelayed(wake, delay);
    }

    /** Moves pending lines into the window until one needs the typewriter. */
    private void revealNext() {
        typingSeq = TerminalUiState.NO_TYPING;
        Pending next;
        while ((next = pending.poll()) != null) {
            window.add(next.line);
            // One beep per event: the system summary, not its copy in the expedition thread.
            if (next.animate && next.line.getChannel() == LogChannel.ALERT
                    && (LogLine.SYSTEM_THREAD.equals(next.line.getThread()) || LogLine.isSummary(next.line.getThread()))) {
                alerts.setValue(next.line.getSeq());
            }
            // Only what's on screen gets the typewriter; the rest just arrives.
            if (next.animate && view.shows(next.line, focus)) {
                typingSeq = next.line.getSeq();
                break;
            }
        }
        trimIfFollowing();
        publish();
    }

    private void trimIfFollowing() {
        if (!followingTail || window.size() <= windowMax) {
            return;
        }
        window.subList(0, window.size() - windowMax).clear();
        hasOlder = true;
    }

    private void publish() {
        List<LogLine> visible = new ArrayList<>();
        for (LogLine line : window) {
            if (view.shows(line, focus)) {
                visible.add(line);
            }
        }
        if (!visible.isEmpty()) {
            seenSeq[view.ordinal()] = Math.max(seenSeq[view.ordinal()], visible.get(visible.size() - 1).getSeq());
        }
        for (LogLine line : visible) {
            seenThread.merge(line.getThread(), line.getSeq(), Math::max);
        }
        Map<String, Integer> unreadByThread = new HashMap<>();
        for (LogLine line : window) {
            if (line.getSeq() > seenThread.getOrDefault(line.getThread(), seenBaseline)) {
                unreadByThread.merge(line.getThread(), 1, Integer::sum);
            }
        }
        int[] unread = new int[LogView.values().length];
        for (LogLine line : window) {
            for (LogView v : LogView.values()) {
                if (line.getSeq() > seenSeq[v.ordinal()] && v.counts(line)) {
                    unread[v.ordinal()]++;
                }
            }
        }
        state.setValue(new TerminalUiState(visible, typingSeq, charMillis, view, focus, unread, unreadByThread));
    }

    /** Switches the COMMS view (and optionally narrows it to one expedition or chat). */
    void setLogView(LogView newView, String newFocus) {
        view = newView;
        focus = newFocus;
        if (!ready) {
            return;
        }
        publish();
        int shown = 0;
        for (LogLine line : window) {
            shown += view.shows(line, focus) ? 1 : 0;
        }
        if (shown < pageSize && hasOlder) {
            loadOlder(); // a quiet view: fetch more history
        }
    }

    @Override
    protected void onCleared() {
        main.removeCallbacksAndMessages(null);
    }
}
