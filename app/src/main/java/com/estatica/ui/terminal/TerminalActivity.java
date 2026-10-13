package com.estatica.ui.terminal;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot;
import com.estatica.databinding.ActivityTerminalBinding;
import com.estatica.domain.expedition.Mission;
import com.estatica.data.settings.Settings;
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;
import com.estatica.ui.scouts.PickerAdapter;
import com.estatica.ui.scouts.ScoutAdapter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class TerminalActivity extends ComponentActivity {

    @Inject
    Settings settings;
    private SoundEngine sound;
    private int typedSinceTick;

    /** Bottom bar destinations. */
    private enum Tab { COMMS, CREW, MAP, RADIO, BUNKER }

    private Tab tab = Tab.COMMS;

    private enum Panel { NONE, SCOUTS, SCOUT_DETAIL, GIFTS, TASKS, DIARY, RECIPES, SCOUT_SECTOR, GEAR_SLOT, RETRAIN }

    /** Crew grid, and the one person shown in full. */
    private com.estatica.ui.scouts.CrewTileAdapter crewTiles;
    private androidx.recyclerview.widget.GridLayoutManager crewGrid;
    private LinearLayoutManager panelLinear;
    private boolean filtersOpen;

    private ActivityTerminalBinding binding;
    private TerminalViewModel viewModel;
    private LogAdapter adapter;
    private LinearLayoutManager layoutManager;
    private Typewriter typewriter;
    private boolean followingTail = true;

    private ScoutAdapter scoutAdapter;
    private PickerAdapter pickerAdapter;
    private SessionSnapshot snapshot;
    private Panel panel = Panel.NONE;
    /** Scout the zone / gift / task picker is for. */
    private String selectedScoutId;
    private String selectedScoutName;
    /** Pending decision currently rendered, to avoid rebuilding its buttons needlessly. */
    private String shownDecisionId;
    private RadioPanel radioPanel;
    private DecoderPanel decoderPanel;
    private BunkerPanel bunkerPanel;
    private MapPanel mapPanel;
    private OptionsPanel optionsPanel;
    private InfoSheet infoSheet;
    private InventoryPanel inventoryPanel;
    private boolean hapticsOn = true;
    private SendPanel sendPanel;
    /** Gear slot being changed (GearDef.Slot name). */
    private String pendingSlot;
    /** Room picked on the task floor plan, or null to start at the person's current one. */
    private String taskRoom;
    /** Sector the scout picker is for. */
    private SessionSnapshot.SectorView pendingSector;

    private final OnBackPressedCallback closePanelOnBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (infoSheet.isShown()) {
                infoSheet.hide();
            } else if (inventoryPanel.isShown()) {
                inventoryPanel.hide();
            } else if (optionsPanel.isShown()) {
                optionsPanel.hide();
            } else if (decoderPanel.isShown()) {
                decoderPanel.hide();
            } else if (panel == Panel.SCOUT_SECTOR) {
                showPanel(Panel.NONE);
                mapPanel.show();
            } else if (panel == Panel.RECIPES) {
                showPanel(Panel.NONE);
                bunkerPanel.show();
            } else if (panel == Panel.GEAR_SLOT || panel == Panel.TASKS || panel == Panel.RETRAIN) {
                showPanel(Panel.SCOUT_DETAIL);
            } else if (sendPanel.isShown()) {
                sendPanel.hide();
            } else if (panel == Panel.DIARY && tab == Tab.COMMS) {
                showPanel(Panel.NONE);
            } else if (panel == Panel.SCOUT_DETAIL || panel == Panel.GIFTS || panel == Panel.DIARY) {
                showPanel(Panel.SCOUTS);
            } else {
                // A tab's own screen: back goes to COMMS.
                selectTab(Tab.COMMS);
                return;
            }
            updateBack();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTerminalBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets();

        viewModel = new ViewModelProvider(this).get(TerminalViewModel.class);
        sound = new SoundEngine(settings.isSoundOn());
        viewModel.getSoundOn().observe(this, sound::setEnabled);
        infoSheet = new InfoSheet(binding.infoSheet, this::updateBack);
        inventoryPanel = new InventoryPanel(binding.inventoryPanel, new InventoryPanel.Host() {
            @Override
            public void closeInventory() {
                inventoryPanel.hide();
                updateBack();
            }

            @Override
            public void read(String title, String text) {
                infoSheet.showText(title, text);
                updateBack();
            }
        });
        binding.hud.setOnClickListener(v -> explainHud());
        infoSheet.explainOnLongPress(binding.expeditionStrip);
        infoSheet.explainOnLongPress(binding.decisionSituation);
        infoSheet.explainOnLongPress(binding.mapPanel.mapInfo);
        optionsPanel = new OptionsPanel(binding.optionsPanel, viewModel, () -> {
            optionsPanel.hide();
            updateBack();
        }, viewModel.appVersion());
        binding.optionsButton.setOnClickListener(v -> {
            if (optionsPanel.isShown()) {
                optionsPanel.hide();
            } else {
                optionsPanel.show();
            }
            updateBack();
        });
        viewModel.getOptions().observe(this, o -> {
            optionsPanel.render(o);
            operatorName = o.operatorName;
            refreshHighlighter();
            hapticsOn = o.haptics;
            if (radioPanel != null) {
                radioPanel.setHaptics(o.haptics);
            }
            binding.scanlines.setVisibility(o.crt ? View.VISIBLE : View.GONE);
        });
        setUpLog();
        setUpInput();
        setUpPanel();
        setUpRadio();
        getOnBackPressedDispatcher().addCallback(this, closePanelOnBack);

        viewModel.getState().observe(this, this::render);
        viewModel.getAlerts().observe(this, seq -> {
            // GDD §2: short haptic when an urgent message arrives, whatever view is open.
            if (hapticsOn) {
                binding.getRoot().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            sound.beep();
        });
        binding.logTabSystem.setOnClickListener(v -> switchLog(LogView.SYSTEM, null));
        binding.logTabExpeditions.setOnClickListener(v -> switchLog(LogView.EXPEDITIONS, null));
        binding.logTabChats.setOnClickListener(v -> switchLog(LogView.CHATS, null));
        binding.logTabDiary.setOnClickListener(v -> {
            viewModel.loadDiary();
            showPanel(Panel.DIARY);
            updateBack();
        });
        viewModel.getSnapshot().observe(this, this::renderSnapshot);
        viewModel.getDiary().observe(this, entries -> {
            if (panel == Panel.DIARY) {
                renderPanel();
            }
        });
    }

    // ---- Log ----

    private void setUpLog() {
        adapter = new LogAdapter();
        adapter.setLineListener(line -> {
            infoSheet.show(line.getText());
            updateBack();
        });
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.log.setLayoutManager(layoutManager);
        binding.log.setItemAnimator(null);
        binding.log.setAdapter(adapter);
        binding.log.addOnScrollListener(scrollListener);
        binding.log.addOnItemTouchListener(tapToSkip());

        typewriter = new Typewriter(new Handler(Looper.getMainLooper()), new Typewriter.Listener() {
            @Override
            public void onTick(int visibleChars) {
                adapter.updateTyping(visibleChars);
                if (++typedSinceTick >= 3) {
                    typedSinceTick = 0;
                    sound.tick();
                }
                if (followingTail) {
                    scrollToBottom();
                }
            }

            @Override
            public void onFinished() {
                long seq = adapter.getTypingSeq();
                adapter.finishTyping();
                viewModel.onTypingFinished(seq);
            }
        });
    }

    private void render(TerminalUiState state) {
        boolean newTypingLine = state.typingSeq != TerminalUiState.NO_TYPING
                && state.typingSeq != adapter.getTypingSeq();
        if (newTypingLine) {
            typewriter.cancel();
            adapter.startTyping(state.typingSeq);
        }
        renderLogTabs(state);
        lastUnread = state.unreadByThread;
        if (logView != LogView.SYSTEM) {
            renderLogChips();
        }
        adapter.submitList(state.lines, () -> {
            if (newTypingLine) {
                LogLine line = findLine(state, state.typingSeq);
                typewriter.start(line != null ? line.getText().length() : 0,
                        state.typewriterCharMillis);
            }
            if (followingTail) {
                scrollToBottom();
            }
        });
    }

    // ---- COMMS views ----

    private void renderLogTabs(TerminalUiState state) {
        logTab(binding.logTabSystem, LogView.SYSTEM, R.string.log_system, state);
        logTab(binding.logTabExpeditions, LogView.EXPEDITIONS, R.string.log_expeditions, state);
        logTab(binding.logTabChats, LogView.CHATS, R.string.log_chats, state);
    }

    private void logTab(TextView view, LogView which, int label, TerminalUiState state) {
        String text = getString(label);
        int unread = state.unread[which.ordinal()];
        if (unread > 0 && state.view != which) {
            text = getString(R.string.log_unread, text, unread);
        }
        boolean selected = state.view == which;
        view.setText(text);
        BracketDrawable.mark(view, selected);
        view.setTextColor(ContextCompat.getColor(this, selected ? R.color.amber_bright : R.color.amber_dim));
    }

    private LogView logView = LogView.SYSTEM;
    private String logFocus;

    private void switchLog(LogView view, String focus) {
        typewriter.skip(); // the line being typed may not be in the new view
        logView = view;
        logFocus = focus;
        adapter.setSeparateThreads(view == LogView.EXPEDITIONS && focus == null);
        viewModel.setLogView(view, focus);
        renderLogChips();
        renderExpeditions(snapshot);
        followTail();
    }

    /** EXPEDITIONS: one chip per team out. CHATS: one per person. */
    private void renderLogChips() {
        binding.logChips.removeAllViews();
        if (logView == LogView.SYSTEM || snapshot == null) {
            binding.logChipsScroll.setVisibility(View.GONE);
            return;
        }
        binding.logChipsScroll.setVisibility(View.VISIBLE);
        addChip(null, getString(R.string.log_all));
        if (logView == LogView.EXPEDITIONS) {
            for (SessionSnapshot.ExpeditionView e : snapshot.expeditions) {
                addChip(e.thread, e.scoutName);
            }
            // Back home (or not): their log stays a few hours after you got the news.
            for (SessionSnapshot.DoneLog d : snapshot.doneLogs) {
                addChip(d.thread, getString(R.string.log_chip_done, d.who));
            }
        } else {
            for (SessionSnapshot.ScoutView s : snapshot.scouts) {
                if (!s.dead) {
                    addChip(LogLine.chatThread(s.id), s.name);
                }
            }
        }
    }

    private java.util.Map<String, Integer> lastUnread = java.util.Map.of();

    /** Crew filter: ALL, HOME, AWAY, HURT, STRESSED. */
    private String crewFilter = "ALL";

    /** Home first, then out there, the fallen last; then the filter. */
    private List<SessionSnapshot.ScoutView> crewList(SessionSnapshot snapshot) {
        List<SessionSnapshot.ScoutView> out = new ArrayList<>();
        for (SessionSnapshot.ScoutView s : snapshot.scouts) {
            if (s.dead) {
                continue; // the fallen live in the Diary
            }
            boolean keep;
            switch (crewFilter) {
                case "HOME":
                    keep = !s.dead && s.awayAt == null;
                    break;
                case "AWAY":
                    keep = !s.dead && s.awayAt != null;
                    break;
                case "HURT":
                    keep = !s.dead && s.hurt;
                    break;
                case "STRESSED":
                    keep = !s.dead && s.stress >= 45;
                    break;
                default:
                    keep = true;
            }
            if (keep) {
                out.add(s);
            }
        }
        out.sort(java.util.Comparator.comparingInt(s -> s.dead ? 2 : s.awayAt != null ? 1 : 0));
        return out;
    }

    private void renderCrewFilters() {
        binding.crewFilters.removeAllViews();
        String[] filters = {"ALL", "HOME", "AWAY", "HURT", "STRESSED"};
        int[] labels = {R.string.crew_filter_all, R.string.crew_filter_home, R.string.crew_filter_away,
                R.string.crew_filter_hurt, R.string.crew_filter_stressed};
        for (int i = 0; i < filters.length; i++) {
            String f = filters[i];
            TextView chip = (TextView) getLayoutInflater().inflate(R.layout.item_decision_option, binding.crewFilters, false);
            String label = getString(labels[i]);
            boolean on = f.equals(crewFilter);
            chip.setText(on ? getString(R.string.log_chip_on, label) : getString(R.string.log_chip, label));
            chip.setTextColor(ContextCompat.getColor(this, on ? R.color.amber_bright : R.color.amber_dim));
            chip.setOnClickListener(v -> {
                crewFilter = f;
                if (snapshot != null) {
                    crewTiles.submitList(crewList(snapshot));
                }
                renderCrewFilters();
                renderFilterButton();
            });
            binding.crewFilters.addView(chip);
        }
    }

    private void addChip(String thread, String label) {
        TextView chip = (TextView) getLayoutInflater().inflate(R.layout.item_decision_option, binding.logChips, false);
        boolean selected = thread == null ? logFocus == null : thread.equals(logFocus);
        int unread = thread == null ? 0 : lastUnread.getOrDefault(thread, 0);
        chip.setText(thread == null ? label
                : unread > 0 && !selected ? getString(R.string.log_chip_unread, label, unread)
                : getString(selected ? R.string.log_chip_on : R.string.log_chip, label));
        chip.setTextColor(ContextCompat.getColor(this, selected ? R.color.amber_bright : R.color.amber_dim));
        chip.setOnClickListener(v -> switchLog(logView, thread));
        binding.logChips.addView(chip);
    }

    private static LogLine findLine(TerminalUiState state, long seq) {
        for (int i = state.lines.size() - 1; i >= 0; i--) {
            if (state.lines.get(i).getSeq() == seq) {
                return state.lines.get(i);
            }
        }
        return null;
    }

    private void scrollToBottom() {
        int count = adapter.getItemCount();
        if (count > 0) {
            binding.log.scrollToPosition(count - 1);
        }
    }

    private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
            if (dy == 0) {
                return;
            }
            boolean atBottom = !rv.canScrollVertically(1);
            if (atBottom != followingTail) {
                followingTail = atBottom;
                viewModel.setFollowingTail(atBottom);
            }
            if (dy < 0 && layoutManager.findFirstVisibleItemPosition() == 0) {
                viewModel.loadOlder();
            }
        }
    };

    /** Any tap on the log completes the line being typed. Scrolling is not affected. */
    private RecyclerView.OnItemTouchListener tapToSkip() {
        GestureDetector detector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(@NonNull MotionEvent e) {
                        typewriter.skip();
                        return true;
                    }
                });
        return new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                detector.onTouchEvent(e);
                return false;
            }
        };
    }

    /** Chips above the input: tap one to fill it in. */
    private void renderSuggestions() {
        binding.suggestions.removeAllViews();
        if (!binding.input.hasFocus()) {
            binding.suggestScroll.setVisibility(View.GONE);
            return;
        }
        List<String> options = viewModel.suggest(binding.input.getText().toString());
        for (String option : options) {
            TextView chip = (TextView) getLayoutInflater().inflate(R.layout.item_decision_option,
                    binding.suggestions, false);
            chip.setText(getString(R.string.log_chip, option.trim()));
            chip.setOnClickListener(v -> {
                binding.input.setText(option);
                binding.input.setSelection(option.length());
            });
            binding.suggestions.addView(chip);
        }
        binding.suggestScroll.setVisibility(options.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setUpInput() {
        binding.input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                renderSuggestions();
            }
        });
        binding.input.setOnFocusChangeListener((v, focused) -> renderSuggestions());
        binding.input.setOnEditorActionListener((v, actionId, event) -> {
            boolean send = actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (send) {
                if (logView != LogView.SYSTEM) {
                    switchLog(LogView.SYSTEM, null); // commands answer in the system log
                }
                viewModel.submitCommand(binding.input.getText().toString());
                binding.input.setText("");
                followTail();
            }
            return send;
        });
    }

    private void followTail() {
        followingTail = true;
        viewModel.setFollowingTail(true);
        scrollToBottom();
    }

    // ---- Decisions (situation orders and conversation answers) ----

    private String vocabulary = "";
    private Highlighter highlighter = Highlighter.NONE;
    private String operatorName;

    /** Rebuilds the highlighter when the words it knows change (someone joined, a rename). */
    private void refreshHighlighter() {
        if (snapshot == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (SessionSnapshot.ScoutView s : snapshot.scouts) {
            names.add(s.name);
        }
        if (operatorName != null) {
            names.add(operatorName);
        }
        String key = names + "|" + snapshot.placeNames.size() + "|" + snapshot.resourceNames.size();
        if (key.equals(vocabulary)) {
            return;
        }
        vocabulary = key;
        highlighter = new Highlighter(names, snapshot.placeNames, snapshot.resourceNames, snapshot.dangerWords,
                snapshot.goodWords, ContextCompat.getColor(this, R.color.amber_bright),
                ContextCompat.getColor(this, R.color.danger), ContextCompat.getColor(this, R.color.good));
        adapter.setHighlighter(highlighter);
    }

    private void renderSnapshot(SessionSnapshot snapshot) {
        this.snapshot = snapshot;
        infoSheet.setGlossary(snapshot.glossary);
        java.util.Set<String> awaiting = new java.util.HashSet<>();
        for (SessionSnapshot.PendingDecision d : snapshot.pending) {
            if (d.kind == SessionSnapshot.PendingDecision.Kind.ORDER) {
                awaiting.add(d.id);
            }
        }
        adapter.setAwaiting(awaiting);
        refreshHighlighter();
        if (logView != LogView.SYSTEM) {
            renderLogChips();
        }
        crewTiles.submitList(crewList(snapshot));
        if (panel == Panel.SCOUT_DETAIL) {
            showScoutDetail();
        }
        renderCrewFilters();
        radioPanel.render(snapshot);
        bunkerPanel.render(snapshot);
        mapPanel.render(snapshot);
        inventoryPanel.render(snapshot);
        sendPanel.render(snapshot);
        if (panel != Panel.NONE && panel != Panel.SCOUTS) {
            renderPanel();
        }
        renderDecision(snapshot);
        renderHud(snapshot);
        renderExpeditions(snapshot);
        renderBadges(snapshot);
    }

    // ---- Status bar, expedition strip and button badges ----

    /** "⚡120/180W ◈40/100 ☾ · SCR 12 CMP 3 RAT 8 …" — static text, no ticking, colored by state. */
    private void renderHud(SessionSnapshot snapshot) {
        SessionSnapshot.BaseView base = snapshot.base;
        android.text.SpannableStringBuilder hud = new android.text.SpannableStringBuilder();
        if (base.blackout) {
            colored(hud, getString(R.string.hud_blackout), R.color.danger);
        } else {
            colored(hud, getString(R.string.hud_power, base.used, base.available),
                    base.used * 10 >= base.available * 9 ? R.color.amber_bright : R.color.good);
        }
        hud.append("  ");
        int reserveShare = base.reserveCapacity == 0 ? 0 : base.reserve * 100 / base.reserveCapacity;
        colored(hud, getString(R.string.hud_reserve, base.reserve, base.reserveCapacity),
                reserveShare < 20 ? R.color.danger : reserveShare < 50 ? R.color.amber : R.color.good);
        hud.append("  ").append(getString(base.night ? R.string.hud_night : R.string.hud_day)).append(' ');
        colored(hud, getString(weatherIcon(snapshot.weather)) + ' ' + snapshot.weatherName, weatherColor(snapshot.weather));
        boolean first = true;
        for (SessionSnapshot.ResourceView r : snapshot.resources) {
            if (r.shortName == null) {
                continue;
            }
            hud.append(first ? "\n" : "  ");
            first = false;
            if (r.icon != null) {
                int start = hud.length();
                hud.append(r.icon);
                hud.setSpan(new android.text.style.RelativeSizeSpan(1.35f), start, hud.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                colored(hud, " " + r.amount, r.amount == 0 ? R.color.danger : R.color.amber);
            } else {
                hud.append(getString(R.string.hud_resource, r.shortName, r.amount));
            }
        }
        hud.append(first ? "\n" : "  ");
        if (snapshot.hungry) {
            colored(hud, getString(R.string.hud_hungry), R.color.danger);
        } else if (!Double.isInfinite(snapshot.foodDaysLeft)) {
            colored(hud, getString(R.string.hud_food, String.format(java.util.Locale.ENGLISH, "%.1f", snapshot.foodDaysLeft)),
                    snapshot.foodDaysLeft < 1 ? R.color.danger : snapshot.foodDaysLeft < 3 ? R.color.amber_bright : R.color.good);
        }
        binding.hud.setText(hud);
        binding.hud.setContentDescription(getString(R.string.hud_description, hud));
    }

    private void colored(android.text.SpannableStringBuilder out, String text, int color) {
        int start = out.length();
        out.append(text);
        out.setSpan(new android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, color)), start, out.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Tap on the status bar: what each number and icon means. */
    private void explainHud() {
        if (snapshot == null) {
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append(getString(R.string.ic_power)).append(' ').append(getString(R.string.hud_info_power)).append("\n\n");
        text.append(getString(R.string.ic_battery)).append(' ').append(getString(R.string.hud_info_reserve)).append("\n\n");
        text.append(getString(R.string.ic_rations)).append(' ').append(getString(R.string.hud_info_food)).append("\n\n");
        text.append(getString(weatherIcon(snapshot.weather))).append(' ').append(getString(R.string.hud_info_weather));
        for (SessionSnapshot.ResourceView r : snapshot.resources) {
            text.append("\n\n").append(getString(R.string.hud_info_resource, r.icon != null ? r.icon : "·",
                    r.name.toUpperCase(java.util.Locale.ROOT), r.amount));
            String use = snapshot.glossary.get(r.name);
            if (use != null) {
                text.append('\n').append(use);
            }
        }
        infoSheet.showText(getString(R.string.hud_info_title), text.toString());
        updateBack();
    }

    private static int weatherColor(String weather) {
        switch (weather) {
            case "CLOUDY":
                return R.color.weather_cloudy;
            case "RAIN":
                return R.color.weather_rain;
            case "FOG":
                return R.color.weather_fog;
            case "STORM":
                return R.color.weather_storm;
            default:
                return R.color.weather_clear;
        }
    }

    /**
     * One line per scout out: progress bar and estimated return, or the waiting state. Only in
     * COMMS > EXPEDITIONS, and only the focused team when a chip is picked.
     */
    private void renderExpeditions(SessionSnapshot snapshot) {
        if (snapshot == null || tab != Tab.COMMS || logView != LogView.EXPEDITIONS) {
            binding.expeditionStrip.setVisibility(View.GONE);
            return;
        }
        java.text.DateFormat time = DateFormat.getTimeFormat(this);
        StringBuilder strip = new StringBuilder();
        for (SessionSnapshot.ExpeditionView e : snapshot.expeditions) {
            if (logFocus != null && !logFocus.equals(e.thread)) {
                continue;
            }
            if (strip.length() > 0) {
                strip.append('\n');
            }
            if (e.waitingForOrders) {
                strip.append(getString(R.string.expedition_waiting, e.scoutName, e.zoneName));
            } else if (e.orderOnItsWay) {
                strip.append(getString(R.string.expedition_order_sent, e.scoutName, e.zoneName));
            } else {
                strip.append(getString(R.string.expedition_line, e.scoutName, e.zoneName,
                        progressBar(e.percent), e.percent, time.format(new Date(e.returnAtMillis))));
            }
        }
        if (strip.length() == 0) {
            strip.append(getString(R.string.expedition_none));
        }
        binding.expeditionStrip.setText(strip);
        binding.expeditionStrip.setVisibility(View.VISIBLE);
    }

    private static String progressBar(int percent) {
        int cells = 8;
        int filled = Math.min(cells, percent * cells / 100);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < cells; i++) {
            bar.append(i < filled ? '█' : '·');
        }
        return bar.append(']').toString();
    }

    private void renderBadges(SessionSnapshot snapshot) {
        int waiting = snapshot.radio.transmissions.size();
        SessionSnapshot.BaseView base = snapshot.base;
        boolean alert = base.blackout || base.powerCut || base.filtersShort || snapshot.hungry
                || snapshot.foodDaysLeft < 1;
        int alive = 0;
        int home = 0;
        for (SessionSnapshot.ScoutView scout : snapshot.scouts) {
            if (!scout.dead) {
                alive++;
                if (scout.awayAt == null) {
                    home++;
                }
            }
        }
        int unexplored = 0;
        for (SessionSnapshot.SectorView s : snapshot.map.sectors) {
            unexplored += !s.scouted && s.reachable && !s.beingScouted ? 1 : 0;
        }
        int orders = snapshot.pending.size();
        tabLabel(binding.tabComms, Tab.COMMS, orders > 0
                ? getString(R.string.tab_comms_badge, orders) : getString(R.string.tab_comms));
        tabLabel(binding.tabCrew, Tab.CREW, getString(R.string.tab_crew_count, home, alive));
        tabLabel(binding.tabMap, Tab.MAP, unexplored > 0 && knowsNothing(snapshot)
                ? getString(R.string.tab_map_badge, unexplored) : getString(R.string.tab_map));
        tabLabel(binding.tabRadio, Tab.RADIO, waiting > 0
                ? getString(R.string.tab_radio_badge, waiting) : getString(R.string.tab_radio));
        tabLabel(binding.tabBunker, Tab.BUNKER, alert
                ? getString(R.string.tab_bunker_alert) : getString(R.string.tab_bunker));
    }

    /** Early game: nothing found yet, so the map is where to go. */
    private static boolean knowsNothing(SessionSnapshot snapshot) {
        return snapshot.map.places.isEmpty();
    }

    private void tabLabel(TextView view, Tab which, String label) {
        boolean selected = tab == which;
        // One tall bracket around both lines (icon and name), not characters beside the text.
        view.setText(label);
        BracketDrawable.mark(view, selected);
        view.setTextColor(ContextCompat.getColor(this, selected ? R.color.amber_bright : R.color.amber_dim));
        view.setSelected(selected);
    }

    /** Switches the screen above the bottom bar. */
    private void selectTab(Tab target) {
        tab = target;
        optionsPanel.hide();
        inventoryPanel.hide();
        sendPanel.hide();
        showPanel(Panel.NONE);
        radioPanel.hide();
        decoderPanel.hide();
        bunkerPanel.hide();
        mapPanel.hide();
        switch (target) {
            case CREW:
                showPanel(Panel.SCOUTS);
                break;
            case MAP:
                mapPanel.show();
                break;
            case RADIO:
                radioPanel.show();
                break;
            case BUNKER:
                bunkerPanel.show();
                break;
            default:
                followTail();
                break;
        }
        if (snapshot != null) {
            renderBadges(snapshot);
        }
        renderExpeditions(snapshot);
        updateBack();
    }

    /** Which pending decision the card shows (they can page through them). */
    private int decisionIndex;

    private void renderDecision(SessionSnapshot snapshot) {
        if (snapshot.pending.isEmpty()) {
            binding.decisionPanel.setVisibility(View.GONE);
            shownDecisionId = null;
            return;
        }
        // Keep showing the same decision while it's still pending.
        int index = -1;
        for (int i = 0; i < snapshot.pending.size(); i++) {
            if (snapshot.pending.get(i).id.equals(shownDecisionId)) {
                index = i;
            }
        }
        decisionIndex = index >= 0 ? index : Math.min(decisionIndex, snapshot.pending.size() - 1);
        SessionSnapshot.PendingDecision decision = snapshot.pending.get(decisionIndex);
        binding.decisionPanel.setVisibility(View.VISIBLE);
        int total = snapshot.pending.size();
        binding.decisionCount.setText(getString(R.string.decision_count, decisionIndex + 1, total));
        binding.decisionCount.setVisibility(total > 1 ? View.VISIBLE : View.GONE);
        binding.decisionPrev.setVisibility(total > 1 ? View.VISIBLE : View.GONE);
        binding.decisionNext.setVisibility(total > 1 ? View.VISIBLE : View.GONE);
        binding.decisionPrev.setOnClickListener(v -> pageDecision(-1));
        binding.decisionNext.setOnClickListener(v -> pageDecision(1));
        if (decision.id.equals(shownDecisionId)) {
            return;
        }
        shownDecisionId = decision.id;
        boolean order = decision.kind == SessionSnapshot.PendingDecision.Kind.ORDER;
        boolean treat = decision.kind == SessionSnapshot.PendingDecision.Kind.TREAT;
        binding.decisionHeader.setText(order
                ? getString(R.string.decision_header, decision.scoutName, decision.place)
                : treat ? getString(R.string.decision_treat_header, decision.scoutName)
                : getString(R.string.decision_talk_header, decision.scoutName));
        binding.decisionHeader.setOnClickListener(v -> switchLog(
                order ? LogView.EXPEDITIONS : LogView.CHATS, decision.thread));
        String deadline = decision.deadlineMillis < 0 ? null
                : DateFormat.getTimeFormat(this).format(new Date(decision.deadlineMillis));
        binding.decisionDeadline.setText(order ? getString(R.string.decision_deadline, deadline)
                : treat ? getString(R.string.decision_treat_open)
                : deadline != null ? getString(R.string.decision_talk_deadline, deadline)
                : getString(R.string.decision_talk_open));
        binding.decisionSituation.setText(highlighter.apply(String.join("\n", decision.situation)));
        binding.decisionSituation.setVisibility(decision.situation.isEmpty() ? View.GONE : View.VISIBLE);
        binding.decisionOptions.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        int shown = 0;
        int dim = ContextCompat.getColor(this, R.color.amber_dim);
        for (int i = 0; i < decision.options.size(); i++) {
            if (decision.options.get(i) == null) {
                continue; // Not for this team: indexes still match the event's options.
            }
            TextView button = (TextView) inflater.inflate(
                    R.layout.item_decision_option, binding.decisionOptions, false);
            android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(
                    getString(R.string.decision_option, ++shown, decision.options.get(i)));
            String hint = i < decision.hints.size() ? decision.hints.get(i) : null;
            if (hint != null) {
                int from = text.length();
                text.append("\n    ").append(hint);
                text.setSpan(new android.text.style.ForegroundColorSpan(dim), from, text.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new android.text.style.RelativeSizeSpan(0.85f), from, text.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            button.setText(text);
            int option = i;
            button.setOnClickListener(v -> {
                binding.decisionOptions.removeAllViews();
                binding.decisionPanel.setVisibility(View.GONE);
                shownDecisionId = null;
                if (order) {
                    viewModel.order(decision.id, option);
                } else if (decision.kind == SessionSnapshot.PendingDecision.Kind.TREAT) {
                    viewModel.treat(decision.id, option);
                } else {
                    viewModel.answer(decision.id, option);
                }
                followTail();
            });
            binding.decisionOptions.addView(button);
        }
    }

    private void pageDecision(int delta) {
        if (snapshot == null || snapshot.pending.isEmpty()) {
            return;
        }
        int total = snapshot.pending.size();
        decisionIndex = ((decisionIndex + delta) % total + total) % total;
        shownDecisionId = null; // redraw at the new index
        renderDecision(snapshot);
    }

    // ---- Scouts panel and its pickers ----

    private void setUpPanel() {
        scoutAdapter = new ScoutAdapter(new ScoutAdapter.Listener() {
            @Override
            public void onDispatch(SessionSnapshot.ScoutView scout) {
                select(scout);
                sendPanel.open(scout.id);
                updateBack();
            }

            @Override
            public void onTalk(SessionSnapshot.ScoutView scout) {
                viewModel.talk(scout.id);
                showPanel(Panel.NONE);
                followTail();
            }

            @Override
            public void onGift(SessionSnapshot.ScoutView scout) {
                select(scout);
                showPanel(Panel.GIFTS);
            }

            @Override
            public void onTask(SessionSnapshot.ScoutView scout) {
                select(scout);
                taskRoom = null;
                showPanel(Panel.TASKS);
            }

            @Override
            public void onRetrain(SessionSnapshot.ScoutView scout) {
                select(scout);
                showPanel(Panel.RETRAIN);
            }

            @Override
            public void onSlot(SessionSnapshot.ScoutView scout, String slot) {
                select(scout);
                pendingSlot = slot;
                showPanel(Panel.GEAR_SLOT);
            }

            @Override
            public void onRecall(SessionSnapshot.ScoutView scout) {
                viewModel.recall(scout.id);
                selectTab(Tab.COMMS);
            }

            @Override
            public void onInfo(CharSequence cardText) {
                infoSheet.show(cardText);
                updateBack();
            }
        });
        pickerAdapter = new PickerAdapter(this::onPick);
        crewTiles = new com.estatica.ui.scouts.CrewTileAdapter(scout -> {
            select(scout);
            showPanel(Panel.SCOUT_DETAIL);
            updateBack();
        });
        binding.taskPlan.setListener(room -> {
            taskRoom = room.id;
            renderPanel();
        });
        crewGrid = new androidx.recyclerview.widget.GridLayoutManager(this, 2);
        panelLinear = new LinearLayoutManager(this);
        binding.panelList.setLayoutManager(panelLinear);
        binding.panelList.setItemAnimator(null);
        binding.tabComms.setOnClickListener(v -> selectTab(Tab.COMMS));
        binding.tabCrew.setOnClickListener(v -> selectTab(Tab.CREW));
        binding.tabMap.setOnClickListener(v -> selectTab(Tab.MAP));
        binding.tabRadio.setOnClickListener(v -> selectTab(Tab.RADIO));
        binding.tabBunker.setOnClickListener(v -> selectTab(Tab.BUNKER));
        binding.panelClose.setOnClickListener(v -> closePanelOnBack.handleOnBackPressed());
        sendPanel = new SendPanel(binding.sendPanel, viewModel, settings, new SendPanel.Host() {
            @Override
            public void close() {
                sendPanel.hide();
                selectTab(Tab.COMMS);
            }

            @Override
            public void beforeSend() {
                requestNotificationPermission();
            }
        });
        binding.panelExtra.setOnClickListener(v -> {
            filtersOpen = !filtersOpen;
            binding.crewFiltersScroll.setVisibility(filtersOpen ? View.VISIBLE : View.GONE);
            renderFilterButton();
        });
        mapPanel = new MapPanel(binding.mapPanel, new MapPanel.Host() {
            @Override
            public void closeMap() {
                selectTab(Tab.COMMS);
            }

            @Override
            public void pickScout(SessionSnapshot.SectorView sector) {
                pendingSector = sector;
                mapPanel.hide();
                showPanel(Panel.SCOUT_SECTOR);
            }

            @Override
            public void sendTo(SessionSnapshot.PlaceView place) {
                sendPanel.open(null, Mission.Type.EXPLORE.name() + "|" + place.zoneId + "|");
                updateBack();
            }
        });
    }

    // ---- Radio and decoder ----

    private void setUpRadio() {
        radioPanel = new RadioPanel(binding.radioPanel, viewModel, new RadioPanel.Host() {
            @Override
            public void openDecoder(SessionSnapshot.TransmissionView transmission, boolean morseTable) {
                decoderPanel.show(transmission, morseTable);
                updateBack();
            }

            @Override
            public void closeRadio() {
                selectTab(Tab.COMMS);
            }
        }, sound);
        decoderPanel = new DecoderPanel(binding.decodePanel, viewModel, () -> {
            decoderPanel.hide();
            updateBack();
        });
        bunkerPanel = new BunkerPanel(binding.bunkerPanel, viewModel, new BunkerPanel.Host() {
            @Override
            public void closeBunker() {
                selectTab(Tab.COMMS);
            }

            @Override
            public void openInventory() {
                inventoryPanel.show();
                updateBack();
            }

            @Override
            public void explain(CharSequence text) {
                infoSheet.show(text);
                updateBack();
            }

            @Override
            public void pickRecipe() {
                // The picker overlay sits below the bunker panel: swap them while choosing.
                bunkerPanel.hide();
                showPanel(Panel.RECIPES);
            }
        });
        viewModel.getTuning().observe(this, radioPanel::onTuning);
    }

    private void updateBack() {
        closePanelOnBack.setEnabled(tab != Tab.COMMS || panel != Panel.NONE || decoderPanel.isShown()
                || optionsPanel.isShown() || sendPanel.isShown() || infoSheet.isShown()
                || inventoryPanel.isShown());
    }

    private void select(SessionSnapshot.ScoutView scout) {
        selectedScoutId = scout.id;
        selectedScoutName = scout.name;
    }

    private void onPick(PickerAdapter.Row row) {
        switch (panel) {
            case GIFTS:
                viewModel.gift(selectedScoutId, row.key);
                break;
            case RETRAIN:
                viewModel.retrain(selectedScoutId, row.key);
                showPanel(Panel.SCOUT_DETAIL);
                updateBack();
                return;
            case TASKS:
                viewModel.assignTask(selectedScoutId, row.key);
                showPanel(Panel.SCOUT_DETAIL); // back to their page, task updated
                updateBack();
                return;
            case GEAR_SLOT:
                if (KEY_TAKE_OFF.equals(row.key)) {
                    viewModel.unequip(selectedScoutId, pendingSlot);
                } else {
                    viewModel.equip(selectedScoutId, row.key);
                }
                showPanel(Panel.SCOUT_DETAIL); // back to the slots to keep dressing them
                updateBack();
                return;
            case SCOUT_SECTOR:
                requestNotificationPermission();
                viewModel.scoutSector(row.key, pendingSector.id);
                break;
            case RECIPES:
                viewModel.craft(row.key);
                showPanel(Panel.NONE);
                bunkerPanel.show();
                updateBack();
                return;
            default:
                return;
        }
        // Done choosing: back to the log to see what happens.
        selectTab(Tab.COMMS);
    }

    private static final String KEY_TAKE_OFF = "off";

    private static int weatherIcon(String weather) {
        switch (weather) {
            case "CLOUDY":
                return R.string.ic_cloudy;
            case "RAIN":
                return R.string.ic_rain;
            case "FOG":
                return R.string.ic_fog;
            case "STORM":
                return R.string.ic_lightning;
            default:
                return R.string.ic_day;
        }
    }

    private SessionSnapshot.ScoutView scoutById(String id) {
        if (snapshot != null) {
            for (SessionSnapshot.ScoutView s : snapshot.scouts) {
                if (s.id.equals(id)) {
                    return s;
                }
            }
        }
        return null;
    }

    private void showPanel(Panel target) {
        panel = target;
        closePanelOnBack.setEnabled(tab != Tab.COMMS || target != Panel.NONE
                || (decoderPanel != null && decoderPanel.isShown()));
        if (target == Panel.NONE) {
            binding.panel.setVisibility(View.GONE);
            binding.panelList.setAdapter(null);
            return;
        }
        binding.panel.setVisibility(View.VISIBLE);
        binding.panelExtra.setVisibility(target == Panel.SCOUTS ? View.VISIBLE : View.GONE);
        binding.crewFiltersScroll.setVisibility(target == Panel.SCOUTS && filtersOpen ? View.VISIBLE : View.GONE);
        // The recipe picker opens over the bunker panel and returns to it.
        boolean top = target == Panel.SCOUTS || (target == Panel.DIARY && tab == Tab.COMMS);
        binding.panelClose.setText(top ? R.string.panel_close : R.string.panel_back);
        if (target == Panel.SCOUTS) {
            binding.panelTitle.setText(R.string.panel_scouts_title);
            renderFilterButton();
            binding.panelList.setLayoutManager(crewGrid);
            binding.panelList.setAdapter(crewTiles);
            return;
        }
        binding.panelList.setLayoutManager(panelLinear);
        binding.taskPlan.setVisibility(target == Panel.TASKS ? View.VISIBLE : View.GONE);
        if (target == Panel.SCOUT_DETAIL) {
            showScoutDetail();
            return;
        }
        binding.panelList.setAdapter(pickerAdapter);
        renderPanel();
    }

    /** One person in full: their card, always open, with every action. */
    private void showScoutDetail() {
        SessionSnapshot.ScoutView who = scoutById(selectedScoutId);
        if (who == null || who.dead) {
            showPanel(Panel.SCOUTS);
            return;
        }
        binding.panelTitle.setText(R.string.panel_scouts_title);
        if (binding.panelList.getAdapter() != scoutAdapter) {
            binding.panelList.setAdapter(scoutAdapter);
        }
        scoutAdapter.submitList(List.of(who));
    }

    /** "[ FILTER ▾ ]", or the active filter's name. */
    private void renderFilterButton() {
        String label = getString(R.string.crew_filter_button);
        if (!"ALL".equals(crewFilter)) {
            int[] labels = {R.string.crew_filter_home, R.string.crew_filter_away, R.string.crew_filter_hurt,
                    R.string.crew_filter_stressed};
            String[] keys = {"HOME", "AWAY", "HURT", "STRESSED"};
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(crewFilter)) {
                    label = getString(R.string.crew_filter_button_on, getString(labels[i]));
                }
            }
        }
        binding.panelExtra.setText(label);
    }

    /** Fills the picker for the current panel from the latest snapshot / diary. */
    private void renderPanel() {
        if (snapshot == null) {
            return;
        }
        List<PickerAdapter.Row> rows = new ArrayList<>();
        switch (panel) {
            case SCOUT_SECTOR:
                binding.panelTitle.setText(getString(R.string.panel_scout_sector_title, pendingSector.label));
                for (SessionSnapshot.ScoutView s : snapshot.scouts) {
                    if (s.canDispatch) {
                        rows.add(new PickerAdapter.Row(s.id, s.name, s.mood, null, true));
                    }
                }
                if (rows.isEmpty()) {
                    rows.add(new PickerAdapter.Row("-", getString(R.string.scout_sector_nobody), null, null, false));
                }
                break;
            case GIFTS:
                binding.panelTitle.setText(getString(R.string.panel_gift_title, selectedScoutName));
                for (SessionSnapshot.ItemView item : snapshot.inventory) {
                    rows.add(new PickerAdapter.Row(item.id, item.name, null, item.description, true));
                }
                if (rows.isEmpty()) {
                    rows.add(new PickerAdapter.Row(null, getString(R.string.inventory_empty), null, null, false));
                }
                break;
            case TASKS:
                binding.panelTitle.setText(getString(R.string.panel_task_title, selectedScoutName));
                taskRows(rows);
                break;
            case RETRAIN: {
                binding.panelTitle.setText(getString(R.string.retrain_title, selectedScoutName));
                SessionSnapshot.ScoutView who = scoutById(selectedScoutId);
                rows.add(new PickerAdapter.Row("-", getString(R.string.retrain_help), null, null, false));
                String[] keys = {"endurance", "stealth", "wits", "nerve", "craft"};
                if (who != null) {
                    for (int i = 0; i < who.stats.size() && i < keys.length; i++) {
                        SessionSnapshot.StatView stat = who.stats.get(i);
                        rows.add(new PickerAdapter.Row(keys[i], getString(R.string.retrain_focus, stat.name),
                                getString(R.string.retrain_meta, stat.value, (stat.value + 1) / 2, stat.ceiling,
                                        stat.ceiling + 2), null, true));
                    }
                }
                break;
            }
            case GEAR_SLOT: {
                SessionSnapshot.ScoutView who = scoutById(selectedScoutId);
                SessionSnapshot.SlotView current = null;
                if (who != null) {
                    for (SessionSnapshot.SlotView slot : who.slots) {
                        if (slot.slot.equals(pendingSlot)) {
                            current = slot;
                        }
                    }
                }
                binding.panelTitle.setText(getString(R.string.gear_slot_pick_title,
                        current != null ? current.name : pendingSlot, selectedScoutName));
                if (current != null && current.pieceId != null) {
                    rows.add(new PickerAdapter.Row(KEY_TAKE_OFF, getString(R.string.gear_take_off), current.label,
                            null, true));
                }
                for (SessionSnapshot.ItemView piece : snapshot.base.gearStock) {
                    if (pendingSlot.equals(piece.slot)) {
                        rows.add(new PickerAdapter.Row(piece.id, piece.name, null, piece.description, true));
                    }
                }
                if (rows.isEmpty() || (rows.size() == 1 && KEY_TAKE_OFF.equals(rows.get(0).key))) {
                    rows.add(new PickerAdapter.Row("-", getString(R.string.gear_empty), null, null, false));
                }
                break;
            }
            case RECIPES:
                binding.panelTitle.setText(R.string.panel_recipes_title);
                for (SessionSnapshot.RecipeView r : snapshot.base.recipes) {
                    boolean available = r.needsLevel == 0;
                    rows.add(new PickerAdapter.Row(r.id, r.name,
                            getString(R.string.recipe_meta, r.cost, r.minutes),
                            available ? null : getString(R.string.recipe_locked, r.needsLevel),
                            available && r.affordable));
                }
                break;
            case DIARY:
                binding.panelTitle.setText(R.string.panel_diary_title);
                List<SessionSnapshot.DiaryEntry> entries = viewModel.getDiary().getValue();
                if (entries != null) {
                    int i = 0;
                    for (SessionSnapshot.DiaryEntry e : entries) {
                        String title = e.section ? "── " + e.name + " ──" : e.name;
                        rows.add(new PickerAdapter.Row("diary" + i++, title, e.header, diaryText(e), false));
                    }
                }
                break;
            default:
                return;
        }
        pickerAdapter.submitList(rows);
    }

    /**
     * Tasks by room: the floor plan above the list picks the room; its tasks come first, then
     * the ones done anywhere. The current task is marked.
     */
    private void taskRows(List<PickerAdapter.Row> rows) {
        SessionSnapshot.ScoutView who = scoutById(selectedScoutId);
        String current = who != null ? who.taskId : null;
        if (taskRoom == null) {
            taskRoom = "bunks";
            for (SessionSnapshot.TaskView task : snapshot.tasks) {
                if (task.id != null && task.id.equals(current) && task.room != null) {
                    taskRoom = task.room;
                }
            }
        }
        binding.taskPlan.setRooms(snapshot.base.rooms, taskRoom);
        SessionSnapshot.RoomView room = null;
        for (SessionSnapshot.RoomView r : snapshot.base.rooms) {
            if (r.id.equals(taskRoom)) {
                room = r;
            }
        }
        if (room != null) {
            rows.add(new PickerAdapter.Row("room:" + room.id, "── " + room.name + " ──", null,
                    room.open ? null : getString(R.string.task_room_sealed), false));
        }
        boolean any = false;
        for (SessionSnapshot.TaskView task : snapshot.tasks) {
            if (task.room != null && task.room.equals(taskRoom)) {
                rows.add(taskRow(task, current));
                any = true;
            }
        }
        if (!any && room != null && room.open) {
            rows.add(new PickerAdapter.Row("none:" + room.id, getString(R.string.task_room_none), null, null, false));
        }
        rows.add(new PickerAdapter.Row("anywhere", getString(R.string.task_anywhere), null, null, false));
        for (SessionSnapshot.TaskView task : snapshot.tasks) {
            if (task.room == null) {
                rows.add(taskRow(task, current));
            }
        }
    }

    private PickerAdapter.Row taskRow(SessionSnapshot.TaskView task, String current) {
        boolean on = java.util.Objects.equals(task.id, current);
        return new PickerAdapter.Row(task.id, on ? getString(R.string.task_current, task.name) : task.name,
                null, task.description, true);
    }

    private static String diaryText(SessionSnapshot.DiaryEntry e) {
        StringBuilder out = new StringBuilder();
        if (e.bio != null) {
            out.append(e.bio);
        }
        for (String memory : e.memories) {
            out.append(out.length() > 0 ? "\n" : "").append("· ").append(memory);
        }
        if (e.lastWords != null) {
            out.append(out.length() > 0 ? "\n\n" : "").append(e.lastWords);
        }
        return out.toString();
    }

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // Either way the game works; notifications are a convenience (GDD §9).
            });

    /** Asked at the first dispatch, when it's clear why the game wants to notify. */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // ---- Lifecycle ----

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.onForeground();
        if (radioPanel.isShown()) {
            radioPanel.show(); // the static comes back with the screen
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Don't keep animating off-screen: finish the active line now.
        typewriter.skip();
        sound.stopRadio();
        viewModel.onBackground();
    }

    @Override
    protected void onDestroy() {
        sound.release();
        typewriter.cancel();
        super.onDestroy();
    }
}
