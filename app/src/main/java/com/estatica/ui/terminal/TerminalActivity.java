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

    private enum Panel { NONE, SCOUTS, ZONES, GIFTS, TASKS, DIARY, GEAR, RECIPES, SCOUT_SECTOR, PARTNER }

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
    private boolean hapticsOn = true;
    /** Destination chosen, waiting for "who goes along?": TYPE|zoneId|target. */
    private String pendingDispatch;
    /** Sector the scout picker is for. */
    private SessionSnapshot.SectorView pendingSector;

    private final OnBackPressedCallback closePanelOnBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (optionsPanel.isShown()) {
                optionsPanel.hide();
            } else if (decoderPanel.isShown()) {
                decoderPanel.hide();
            } else if (panel == Panel.SCOUT_SECTOR) {
                showPanel(Panel.NONE);
                mapPanel.show();
            } else if (panel == Panel.RECIPES) {
                showPanel(Panel.NONE);
                bunkerPanel.show();
            } else if (panel == Panel.PARTNER) {
                showPanel(Panel.ZONES);
            } else if (panel == Panel.ZONES || panel == Panel.GIFTS || panel == Panel.TASKS
                    || panel == Panel.DIARY || panel == Panel.GEAR) {
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
        view.setText(selected ? getString(R.string.tab_selected, text) : text);
        view.setTextColor(ContextCompat.getColor(this, selected ? R.color.amber_bright : R.color.amber_dim));
    }

    private LogView logView = LogView.SYSTEM;
    private String logFocus;

    private void switchLog(LogView view, String focus) {
        typewriter.skip(); // the line being typed may not be in the new view
        logView = view;
        logFocus = focus;
        viewModel.setLogView(view, focus);
        renderLogChips();
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
        } else {
            for (SessionSnapshot.ScoutView s : snapshot.scouts) {
                if (!s.dead) {
                    addChip(LogLine.chatThread(s.id), s.name);
                }
            }
        }
    }

    private void addChip(String thread, String label) {
        TextView chip = (TextView) getLayoutInflater().inflate(R.layout.item_decision_option, binding.logChips, false);
        boolean selected = thread == null ? logFocus == null : thread.equals(logFocus);
        chip.setText(thread == null ? label
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

    private void setUpInput() {
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

    private void renderSnapshot(SessionSnapshot snapshot) {
        this.snapshot = snapshot;
        if (logView != LogView.SYSTEM) {
            renderLogChips();
        }
        scoutAdapter.submitList(snapshot.scouts);
        radioPanel.render(snapshot);
        bunkerPanel.render(snapshot);
        mapPanel.render(snapshot);
        if (panel != Panel.NONE && panel != Panel.SCOUTS) {
            renderPanel();
        }
        renderDecision(snapshot);
        renderHud(snapshot);
        renderExpeditions(snapshot);
        renderBadges(snapshot);
    }

    // ---- Status bar, expedition strip and button badges ----

    /** "⚡120/180W ◈40/100 ☾ · SCR 12 CMP 3 RAT 8 …" — static text, no ticking. */
    private void renderHud(SessionSnapshot snapshot) {
        SessionSnapshot.BaseView base = snapshot.base;
        StringBuilder hud = new StringBuilder();
        if (base.blackout) {
            hud.append(getString(R.string.hud_blackout));
        } else {
            hud.append(getString(R.string.hud_power, base.used, base.available));
        }
        hud.append("  ").append(getString(R.string.hud_reserve, base.reserve, base.reserveCapacity));
        if (base.night) {
            hud.append("  ").append(getString(R.string.hud_night));
        }
        StringBuilder supplies = new StringBuilder();
        for (SessionSnapshot.ResourceView r : snapshot.resources) {
            if (r.shortName == null) {
                continue;
            }
            if (supplies.length() > 0) {
                supplies.append("  ");
            }
            supplies.append(getString(R.string.hud_resource, r.shortName, r.amount));
        }
        if (snapshot.hungry) {
            supplies.append("  ").append(getString(R.string.hud_hungry));
        } else if (!Double.isInfinite(snapshot.foodDaysLeft)) {
            supplies.append("  ").append(getString(R.string.hud_food,
                    String.format(java.util.Locale.ENGLISH, "%.1f", snapshot.foodDaysLeft)));
        }
        if (supplies.length() > 0) {
            hud.append('\n').append(supplies);
        }
        binding.hud.setText(hud);
        binding.hud.setContentDescription(getString(R.string.hud_description, hud));
    }

    /** One line per scout out: progress bar and estimated return, or the waiting state. */
    private void renderExpeditions(SessionSnapshot snapshot) {
        if (snapshot.expeditions.isEmpty()) {
            binding.expeditionStrip.setVisibility(View.GONE);
            return;
        }
        java.text.DateFormat time = DateFormat.getTimeFormat(this);
        StringBuilder strip = new StringBuilder();
        for (SessionSnapshot.ExpeditionView e : snapshot.expeditions) {
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
        view.setText(selected ? getString(R.string.tab_selected, label) : label);
        view.setTextColor(ContextCompat.getColor(this, selected ? R.color.amber_bright : R.color.amber_dim));
        view.setSelected(selected);
    }

    /** Switches the screen above the bottom bar. */
    private void selectTab(Tab target) {
        tab = target;
        optionsPanel.hide();
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
        updateBack();
    }

    private void renderDecision(SessionSnapshot snapshot) {
        if (snapshot.pending.isEmpty()) {
            binding.decisionPanel.setVisibility(View.GONE);
            shownDecisionId = null;
            return;
        }
        SessionSnapshot.PendingDecision decision = snapshot.pending.get(0);
        binding.decisionPanel.setVisibility(View.VISIBLE);
        int more = snapshot.pending.size() - 1;
        binding.decisionMore.setVisibility(more > 0 ? View.VISIBLE : View.GONE);
        binding.decisionMore.setText(getString(R.string.decision_more, more));
        if (decision.id.equals(shownDecisionId)) {
            return;
        }
        shownDecisionId = decision.id;
        binding.decisionHeader.setText(decisionHeader(decision));
        binding.decisionOptions.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        int shown = 0;
        for (int i = 0; i < decision.options.size(); i++) {
            if (decision.options.get(i) == null) {
                continue; // Not for this scout: indexes still match the event's options.
            }
            TextView button = (TextView) inflater.inflate(
                    R.layout.item_decision_option, binding.decisionOptions, false);
            button.setText(getString(R.string.decision_option, ++shown, decision.options.get(i)));
            int option = i;
            button.setOnClickListener(v -> {
                binding.decisionOptions.removeAllViews();
                binding.decisionPanel.setVisibility(View.GONE);
                shownDecisionId = null;
                if (decision.kind == SessionSnapshot.PendingDecision.Kind.ORDER) {
                    viewModel.order(decision.id, option);
                } else {
                    viewModel.answer(decision.id, option);
                }
                followTail();
            });
            binding.decisionOptions.addView(button);
        }
    }

    private String decisionHeader(SessionSnapshot.PendingDecision decision) {
        String deadline = decision.deadlineMillis < 0 ? null
                : DateFormat.getTimeFormat(this).format(new Date(decision.deadlineMillis));
        if (decision.kind == SessionSnapshot.PendingDecision.Kind.ORDER) {
            return getString(R.string.decision_header, decision.scoutName, deadline);
        }
        return deadline == null
                ? getString(R.string.decision_talk_header, decision.scoutName)
                : getString(R.string.decision_talk_header_until, decision.scoutName, deadline);
    }

    // ---- Scouts panel and its pickers ----

    private void setUpPanel() {
        scoutAdapter = new ScoutAdapter(new ScoutAdapter.Listener() {
            @Override
            public void onDispatch(SessionSnapshot.ScoutView scout) {
                select(scout);
                showPanel(Panel.ZONES);
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
                showPanel(Panel.TASKS);
            }

            @Override
            public void onEquip(SessionSnapshot.ScoutView scout) {
                select(scout);
                showPanel(Panel.GEAR);
            }
        });
        pickerAdapter = new PickerAdapter(this::onPick);
        binding.panelList.setLayoutManager(new LinearLayoutManager(this));
        binding.panelList.setItemAnimator(null);
        binding.tabComms.setOnClickListener(v -> selectTab(Tab.COMMS));
        binding.tabCrew.setOnClickListener(v -> selectTab(Tab.CREW));
        binding.tabMap.setOnClickListener(v -> selectTab(Tab.MAP));
        binding.tabRadio.setOnClickListener(v -> selectTab(Tab.RADIO));
        binding.tabBunker.setOnClickListener(v -> selectTab(Tab.BUNKER));
        binding.panelClose.setOnClickListener(v -> closePanelOnBack.handleOnBackPressed());
        binding.panelExtra.setOnClickListener(v -> {
            viewModel.loadDiary();
            showPanel(Panel.DIARY);
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
                || optionsPanel.isShown());
    }

    private void select(SessionSnapshot.ScoutView scout) {
        selectedScoutId = scout.id;
        selectedScoutName = scout.name;
    }

    private void onPick(PickerAdapter.Row row) {
        switch (panel) {
            case ZONES: {
                requestNotificationPermission();
                if (hasFreeCompanion()) {
                    pendingDispatch = row.key;
                    showPanel(Panel.PARTNER);
                    return;
                }
                sendOut(row.key, null);
                break;
            }
            case PARTNER:
                sendOut(pendingDispatch, row.key.isEmpty() ? null : row.key);
                break;
            case GIFTS:
                viewModel.gift(selectedScoutId, row.key);
                break;
            case TASKS:
                viewModel.assignTask(selectedScoutId, row.key);
                break;
            case GEAR:
                viewModel.equip(selectedScoutId, row.key);
                break;
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

    /** key: TYPE|zoneId|target. */
    private void sendOut(String key, String partnerId) {
        String[] parts = key.split("\\|", -1);
        viewModel.dispatch(selectedScoutId, parts[1], Mission.Type.valueOf(parts[0]),
                parts[2].isEmpty() ? null : parts[2], partnerId);
    }

    private boolean hasFreeCompanion() {
        for (SessionSnapshot.ScoutView s : snapshot.scouts) {
            if (s.canDispatch && !s.id.equals(selectedScoutId)) {
                return true;
            }
        }
        return false;
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
        // The recipe picker opens over the bunker panel and returns to it.
        binding.panelClose.setText(target == Panel.SCOUTS ? R.string.panel_close : R.string.panel_back);
        if (target == Panel.SCOUTS) {
            binding.panelTitle.setText(R.string.panel_scouts_title);
            binding.panelList.setAdapter(scoutAdapter);
            return;
        }
        binding.panelList.setAdapter(pickerAdapter);
        renderPanel();
    }

    /** Fills the picker for the current panel from the latest snapshot / diary. */
    private void renderPanel() {
        if (snapshot == null) {
            return;
        }
        List<PickerAdapter.Row> rows = new ArrayList<>();
        switch (panel) {
            case ZONES:
                binding.panelTitle.setText(getString(R.string.panel_zones_title, selectedScoutName));
                for (SessionSnapshot.MissionView m : snapshot.missions) {
                    if (m.scoutId != null && !m.scoutId.equals(selectedScoutId)) {
                        continue;
                    }
                    rows.add(new PickerAdapter.Row(m.type + "|" + m.zoneId + "|" + (m.target != null ? m.target : ""),
                            m.label, m.zoneName, null, true));
                }
                for (SessionSnapshot.ZoneView z : snapshot.zones) {
                    StringBuilder extra = new StringBuilder(z.description);
                    if (z.relay >= 0) {
                        extra.append("\n").append(getString(R.string.zone_relay, z.relay));
                    }
                    if (z.intel) {
                        extra.append("\n").append(getString(R.string.zone_intel));
                    }
                    rows.add(new PickerAdapter.Row(Mission.Type.EXPLORE.name() + "|" + z.id + "|", z.name,
                            getString(R.string.zone_band_meta, z.band, dangerBar(z.danger), z.minMinutes, z.maxMinutes),
                            extra.toString(), true));
                }
                if (rows.isEmpty()) {
                    rows.add(new PickerAdapter.Row("-", getString(R.string.zones_none), null, null, false));
                }
                break;
            case PARTNER: {
                binding.panelTitle.setText(getString(R.string.panel_partner_title, selectedScoutName));
                rows.add(new PickerAdapter.Row("", getString(R.string.partner_alone), null, null, true));
                SessionSnapshot.ScoutView lead = null;
                for (SessionSnapshot.ScoutView s : snapshot.scouts) {
                    if (s.id.equals(selectedScoutId)) {
                        lead = s;
                    }
                }
                for (SessionSnapshot.ScoutView s : snapshot.scouts) {
                    if (!s.canDispatch || s.id.equals(selectedScoutId)) {
                        continue;
                    }
                    String relation = lead != null ? lead.relationKinds.get(s.id) : null;
                    String meta = relation == null ? s.mood
                            : "rivals".equals(relation) ? getString(R.string.partner_rivals)
                            : getString(R.string.partner_friends);
                    rows.add(new PickerAdapter.Row(s.id, s.name, meta, null, true));
                }
                break;
            }
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
                for (SessionSnapshot.TaskView task : snapshot.tasks) {
                    rows.add(new PickerAdapter.Row(task.id, task.name, null, task.description, true));
                }
                break;
            case GEAR:
                binding.panelTitle.setText(getString(R.string.panel_gear_title, selectedScoutName));
                rows.add(new PickerAdapter.Row(null, getString(R.string.gear_none), null, null, true));
                for (SessionSnapshot.ItemView gear : snapshot.base.gearStock) {
                    rows.add(new PickerAdapter.Row(gear.id, gear.name, null, gear.description, true));
                }
                if (snapshot.base.gearStock.isEmpty()) {
                    rows.add(new PickerAdapter.Row("-", getString(R.string.gear_empty), null, null, false));
                }
                break;
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

    private static String dangerBar(int danger) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            out.append(i < danger ? '▮' : '▯');
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
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Don't keep animating off-screen: finish the active line now.
        typewriter.skip();
        viewModel.onBackground();
    }

    @Override
    protected void onDestroy() {
        sound.release();
        typewriter.cancel();
        super.onDestroy();
    }
}
