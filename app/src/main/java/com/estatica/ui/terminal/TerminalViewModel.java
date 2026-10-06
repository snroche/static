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

    void upgradeModule(String moduleId) {
        if (ready) {
            run(step -> step.animated.addAll(session.upgradeModule(moduleId)));
        }
    }

    void dispatch(String scoutId, String zoneId, Mission.Type type, String target) {
        if (ready) {
            run(step -> step.animated.addAll(session.dispatch(scoutId, zoneId, type, target)));
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
        List<String> templates = session.takeNewGame()
                ? text.getBoot()
                : session.random().pick(text.getReconnect());
        Step step = new Step();
        step.animated.addAll(session.log(systemLines(templates, vars)));
        // What happened while the app was closed is dumped at once (GDD §4.3).
        step.instant.addAll(session.catchUp());
        session.markSeen();
        step.snapshot = session.snapshot();

        int page = content.getBalance().getUi().getLogPageSize();
        int typeMillis = content.getBalance().getUi().getTypewriterCharMillis();
        boolean older = recent.size() == windowLines;
        main.post(() -> onSessionLoaded(recent, step, windowLines, page, typeMillis, older));
    }

    private void tick(Step step) {
        step.animated.addAll(session.catchUp());
    }

    private Map<String, String> baseVars() {
        Map<String, String> vars = new HashMap<>();
        vars.put("version", appVersion);
        vars.put("operator", session.operatorId().substring(0, 8).toUpperCase(Locale.ROOT));
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
        String setting = applySetting(command);
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
            if (next.animate) {
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
        state.setValue(new TerminalUiState(new ArrayList<>(window), typingSeq, charMillis));
    }

    @Override
    protected void onCleared() {
        main.removeCallbacksAndMessages(null);
    }
}
