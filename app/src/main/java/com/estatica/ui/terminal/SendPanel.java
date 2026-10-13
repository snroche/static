package com.estatica.ui.terminal;

import android.content.res.Resources;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot;
import com.estatica.data.settings.Settings;
import com.estatica.databinding.ItemCrewTileBinding;
import com.estatica.databinding.ItemPlaceBinding;
import com.estatica.domain.expedition.Mission;
import com.estatica.domain.expedition.TimelineGenerator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sending people out, in two steps the operator can switch between freely before sending:
 * WHO GOES (crew tiles: tap to pick, long-press for the leader) and WHERE (place blocks with
 * the exact trip time, danger and how picked-over it is). Rescues and personal missions come
 * first, then places by the chosen order, then the ones picked clean.
 */
final class SendPanel {

    interface Host {
        void close();

        /** Notification permission is asked on the first dispatch. */
        void beforeSend();
    }

    private enum Step { WHO, WHERE }

    /** Place order, remembered between openings. */
    static final String SORT_DANGER = "DANGER";
    static final String SORT_PICKED = "PICKED";
    static final String SORT_TIME = "TIME";

    private static final int MAX_TEAM = 2;
    /** Above this, a place is picked clean and goes to the bottom. */
    private static final int EXHAUSTED = 95;
    private static final int TILE = 0;
    private static final int PLACE = 1;
    private static final int NOTE = 2;

    private final com.estatica.databinding.PanelSendBinding b;
    private final TerminalViewModel viewModel;
    private final Host host;
    private final Settings settings;
    private final Adapter adapter = new Adapter();
    private SessionSnapshot snapshot;
    private Step step = Step.WHO;
    /** Picked scout ids, leader first. */
    private final List<String> team = new ArrayList<>();
    /** The operator already saw the "they're hurt" warning for this team and place. */
    private boolean hurtWarned;
    /** Destination key: TYPE|zoneId|target. */
    private String destination;
    /** A note for the operator after a tap that couldn't be done (out of reach…). */
    private String notice;
    /** Last trip per scout (team, destination), to send the same again in one tap. */
    private final Map<String, List<String>> lastTeam = new HashMap<>();
    private final Map<String, String> lastDestination = new HashMap<>();

    SendPanel(com.estatica.databinding.PanelSendBinding binding, TerminalViewModel viewModel, Settings settings,
              Host host) {
        this.b = binding;
        this.viewModel = viewModel;
        this.settings = settings;
        this.host = host;
        b.sendClose.setOnClickListener(v -> host.close());
        b.sendGo.setOnClickListener(v -> send());
        b.sendStepWho.setOnClickListener(v -> showStep(Step.WHO));
        b.sendStepWhere.setOnClickListener(v -> showStep(Step.WHERE));
        b.sortDanger.setOnClickListener(v -> sortBy(SORT_DANGER));
        b.sortPicked.setOnClickListener(v -> sortBy(SORT_PICKED));
        b.sortTime.setOnClickListener(v -> sortBy(SORT_TIME));
        GridLayoutManager grid = new GridLayoutManager(b.getRoot().getContext(), 2);
        grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getItemViewType(position) == TILE ? 1 : 2;
            }
        });
        b.sendList.setLayoutManager(grid);
        b.sendList.setItemAnimator(null);
        b.sendList.setAdapter(adapter);
    }

    boolean isShown() {
        return b.getRoot().getVisibility() == View.VISIBLE;
    }

    /** Opens with {@code leadId} already on the team (or empty if null). */
    void open(String leadId) {
        open(leadId, null);
    }

    /** @param destinationKey TYPE|zoneId|target already chosen (from the map), or null */
    void open(String leadId, String destinationKey) {
        team.clear();
        hurtWarned = false;
        notice = null;
        if (leadId != null && destinationKey == null && lastTeam.containsKey(leadId)) {
            // Send again: same team, same place (changeable).
            team.addAll(lastTeam.get(leadId));
            destinationKey = lastDestination.get(leadId);
        } else if (leadId != null) {
            team.add(leadId);
        }
        destination = destinationKey;
        // Start where there's still a choice to make.
        step = team.isEmpty() ? Step.WHO : Step.WHERE;
        b.getRoot().setVisibility(View.VISIBLE);
        render(snapshot);
        b.sendList.scrollToPosition(0);
    }

    void hide() {
        b.getRoot().setVisibility(View.GONE);
    }

    private void showStep(Step target) {
        step = target;
        notice = null;
        render(snapshot);
        b.sendList.scrollToPosition(0);
    }

    private void sortBy(String sort) {
        settings.setSendSort(sort);
        render(snapshot);
        b.sendList.scrollToPosition(0);
    }

    void render(SessionSnapshot snapshot) {
        this.snapshot = snapshot;
        if (snapshot == null || !isShown()) {
            return;
        }
        // People who left meanwhile drop off the team.
        team.removeIf(id -> scout(id) == null || !scout(id).canDispatch);
        if (destination != null && !destinationAvailable(destination)) {
            destination = null; // gone since last time (mission over)
        }
        Resources res = b.getRoot().getResources();
        b.sendStepWho.setText(res.getString(R.string.send_step_who, team.size(), MAX_TEAM));
        b.sendStepWhere.setText(destination == null ? res.getString(R.string.send_step_where)
                : res.getString(R.string.send_step_where_picked, placeName(destination)));
        tab(b.sendStepWho, step == Step.WHO);
        tab(b.sendStepWhere, step == Step.WHERE);
        b.sendSortBar.setVisibility(step == Step.WHERE ? View.VISIBLE : View.GONE);
        String sort = settings.getSendSort();
        tab(b.sortDanger, SORT_DANGER.equals(sort));
        tab(b.sortPicked, SORT_PICKED.equals(sort));
        tab(b.sortTime, SORT_TIME.equals(sort));

        List<Entry> entries = step == Step.WHO ? whoEntries(res) : whereEntries(res, sort);
        adapter.submitList(entries);

        boolean ready = !team.isEmpty() && destination != null;
        b.sendGo.setAlpha(ready ? 1f : 0.4f);
        b.sendGo.setEnabled(ready);
        String hurt = hurtWarning(res);
        b.sendGo.setText(hurt != null && hurtWarned ? R.string.send_go_anyway : R.string.send_go);
        SpannableStringBuilder text = new SpannableStringBuilder(summary(res));
        String alert = notice != null ? notice : hurt;
        if (alert != null) {
            text.append('\n');
            int from = text.length();
            text.append(alert);
            text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(b.getRoot().getContext(), R.color.danger)),
                    from, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        b.sendSummary.setText(text);
    }

    private void tab(TextView view, boolean on) {
        view.setSelected(on);
        view.setTextColor(ContextCompat.getColor(view.getContext(), on ? R.color.amber_bright : R.color.amber_dim));
    }

    // ---- WHO GOES ----

    private List<Entry> whoEntries(Resources res) {
        List<Entry> out = new ArrayList<>();
        out.add(Entry.note("who-help", res.getString(R.string.send_who_help, MAX_TEAM)));
        List<SessionSnapshot.ScoutView> busy = new ArrayList<>();
        for (SessionSnapshot.ScoutView s : snapshot.scouts) {
            if (s.dead) {
                continue;
            }
            if (s.canDispatch) {
                out.add(Entry.scout(s));
            } else {
                busy.add(s);
            }
        }
        if (out.size() == 1) {
            out.add(Entry.note("nobody", res.getString(R.string.send_nobody)));
        }
        for (SessionSnapshot.ScoutView s : busy) {
            out.add(Entry.scout(s)); // shown dimmed: not here to send
        }
        return out;
    }

    private void bindScout(ItemCrewTileBinding t, SessionSnapshot.ScoutView s) {
        Resources res = t.getRoot().getResources();
        int index = team.indexOf(s.id);
        boolean picked = index >= 0;
        boolean leader = index == 0;
        t.getRoot().setBackgroundResource(picked ? R.drawable.bg_tile_picked
                : s.awayAt != null ? R.drawable.bg_tile_away : R.drawable.bg_tile_home);
        String mark = leader ? " " + res.getString(R.string.ic_crown) : picked ? " ✓" : "";
        t.tileName.setText(res.getString(R.string.tile_name, s.name, s.female ? "♀" : "♂") + mark);
        String doing;
        int color = R.color.amber;
        if (!s.canDispatch) {
            doing = s.awayAt != null ? res.getString(R.string.tile_away, s.awayAt) : s.status;
            color = s.awayAt != null ? R.color.signal : R.color.amber_dim;
        } else if (leader) {
            doing = res.getString(R.string.send_role_leader);
            color = R.color.amber_bright;
        } else if (picked || !team.isEmpty()) {
            SessionSnapshot.ScoutView lead = scout(team.get(0));
            String relation = lead != null ? lead.relationKinds.get(s.id) : null;
            doing = "rivals".equals(relation) ? res.getString(R.string.partner_rivals)
                    : relation != null ? res.getString(R.string.partner_friends)
                    : picked ? res.getString(R.string.send_role_partner) : s.mood;
            color = "rivals".equals(relation) ? R.color.danger : R.color.amber;
        } else {
            doing = s.hurt ? res.getString(R.string.send_tile_hurt, s.injury) : s.mood;
            color = s.hurt ? R.color.danger : R.color.amber;
        }
        t.tileDoing.setText(doing);
        t.tileDoing.setTextColor(ContextCompat.getColor(t.getRoot().getContext(), color));
        t.tileState.setText(res.getString(R.string.send_tile_state, bar(Math.round(s.stress / 20f), 5),
                bar(s.bondLevel, s.bondLevels), pace(res, s.pace)));
        t.getRoot().setAlpha(s.canDispatch ? 1f : 0.45f);
        t.getRoot().setContentDescription(res.getString(R.string.send_tile_description, s.name, doing));
        if (s.canDispatch) {
            t.getRoot().setOnClickListener(v -> toggle(s.id));
            t.getRoot().setOnLongClickListener(v -> {
                makeLeader(s.id);
                return true;
            });
        } else {
            t.getRoot().setOnClickListener(null);
            t.getRoot().setOnLongClickListener(null);
            t.getRoot().setClickable(false);
        }
    }

    private static String pace(Resources res, double pace) {
        if (pace < 0.95) {
            return " " + res.getString(R.string.send_pace_fast);
        }
        if (pace > 1.05) {
            return " " + res.getString(R.string.send_pace_slow);
        }
        return "";
    }

    private void toggle(String id) {
        hurtWarned = false;
        notice = null;
        if (!team.remove(id)) {
            if (team.size() >= MAX_TEAM) {
                team.remove(team.size() - 1); // swap the last one picked
            }
            team.add(id);
        }
        render(snapshot);
    }

    private void makeLeader(String id) {
        hurtWarned = false;
        notice = null;
        team.remove(id);
        if (team.size() >= MAX_TEAM) {
            team.remove(team.size() - 1);
        }
        team.add(0, id);
        render(snapshot);
    }

    // ---- WHERE ----

    private List<Entry> whereEntries(Resources res, String sort) {
        List<Entry> out = new ArrayList<>();
        List<Entry> missions = new ArrayList<>();
        for (SessionSnapshot.MissionView m : snapshot.missions) {
            missions.add(Entry.mission(m));
        }
        // Rescues and personal missions first, then relay work.
        missions.sort(Comparator.comparingInt(e -> special(e.mission) ? 0 : 1));
        if (!missions.isEmpty()) {
            out.add(Entry.note("h-missions", res.getString(R.string.send_section_missions)));
            out.addAll(missions);
        }
        List<Entry> places = new ArrayList<>();
        List<Entry> clean = new ArrayList<>();
        for (SessionSnapshot.ZoneView z : snapshot.zones) {
            (z.depleted > EXHAUSTED ? clean : places).add(Entry.zone(z));
        }
        double pace = teamPace();
        Comparator<Entry> order;
        if (SORT_PICKED.equals(sort)) {
            order = Comparator.comparingInt((Entry e) -> e.zone.depleted).thenComparingInt(e -> e.zone.danger);
        } else if (SORT_TIME.equals(sort)) {
            order = Comparator.comparingLong((Entry e) -> trip(e.zone.minMinutes, e.zone.maxMinutes, pace))
                    .thenComparingInt(e -> e.zone.danger);
        } else {
            order = Comparator.comparingInt((Entry e) -> e.zone.danger)
                    .thenComparingLong(e -> trip(e.zone.minMinutes, e.zone.maxMinutes, pace));
        }
        places.sort(order);
        clean.sort(order);
        out.add(Entry.note("h-places", res.getString(R.string.send_section_places)));
        out.addAll(places);
        if (places.isEmpty()) {
            out.add(Entry.note("no-places", res.getString(R.string.zones_none)));
        }
        if (!clean.isEmpty()) {
            out.add(Entry.note("h-clean", res.getString(R.string.send_section_clean)));
            out.addAll(clean);
        }
        return out;
    }

    private static boolean special(SessionSnapshot.MissionView m) {
        return Mission.Type.RESCUE.name().equals(m.type) || Mission.Type.PERSONAL.name().equals(m.type);
    }

    private void bindZone(ItemPlaceBinding p, SessionSnapshot.ZoneView z) {
        Resources res = p.getRoot().getResources();
        String key = Mission.Type.EXPLORE.name() + "|" + z.id + "|";
        boolean on = key.equals(destination);
        p.getRoot().setBackgroundResource(on ? R.drawable.bg_tile_picked : R.drawable.bg_tile_home);
        p.placeName.setText(z.name);
        p.placeTime.setText(time(res, trip(z.minMinutes, z.maxMinutes, teamPace())));
        p.placeStats.setText(res.getString(R.string.send_place_stats, bar(z.danger, Math.max(4, z.danger)),
                z.depleted));
        List<String> notes = new ArrayList<>();
        if (z.relay >= 0) {
            notes.add(res.getString(R.string.zone_relay, z.relay));
        }
        if (z.intel) {
            notes.add(res.getString(R.string.zone_intel));
        }
        if (z.depleted >= 15 && z.refillIn != null) {
            notes.add(res.getString(R.string.send_place_refill, z.refillIn));
        }
        optional(p.placeNote, notes.isEmpty() ? null : String.join(" · ", notes));
        p.getRoot().setAlpha(z.depleted > EXHAUSTED && !on ? 0.6f : 1f);
        p.getRoot().setOnClickListener(v -> choose(key, null));
    }

    private void bindMission(ItemPlaceBinding p, SessionSnapshot.MissionView m) {
        Resources res = p.getRoot().getResources();
        String key = m.type + "|" + m.zoneId + "|" + (m.target != null ? m.target : "");
        boolean on = key.equals(destination);
        p.getRoot().setBackgroundResource(on ? R.drawable.bg_tile_picked
                : special(m) ? R.drawable.bg_tile_special : R.drawable.bg_tile_home);
        p.placeName.setText(m.label);
        p.placeName.setTextColor(ContextCompat.getColor(p.getRoot().getContext(),
                special(m) ? R.color.signal : R.color.amber_bright));
        p.placeTime.setText(m.reachable ? time(res, trip(m.minMinutes, m.maxMinutes, teamPace())) : "");
        p.placeStats.setText(res.getString(R.string.send_mission_stats, m.zoneName,
                bar(m.danger, Math.max(4, m.danger))));
        String blocker = missionBlocker(res, m);
        optional(p.placeNote, blocker);
        p.placeNote.setTextColor(ContextCompat.getColor(p.getRoot().getContext(),
                blocker != null && !m.reachable ? R.color.danger : R.color.amber_dim));
        p.getRoot().setAlpha(m.reachable ? 1f : 0.6f);
        p.getRoot().setOnClickListener(v -> choose(key, m));
    }

    /** Why this mission can't go as things stand, or null. */
    private String missionBlocker(Resources res, SessionSnapshot.MissionView m) {
        if (!m.reachable) {
            return res.getString(R.string.send_out_of_reach);
        }
        if (m.scoutId != null) {
            SessionSnapshot.ScoutView owner = scout(m.scoutId);
            String name = owner != null ? owner.name : "?";
            if (team.isEmpty() || !m.scoutId.equals(team.get(0))) {
                return owner != null && owner.canDispatch ? res.getString(R.string.send_only_leads, name)
                        : res.getString(R.string.send_only_leads_away, name);
            }
        }
        return null;
    }

    private void choose(String key, SessionSnapshot.MissionView mission) {
        hurtWarned = false;
        notice = null;
        if (mission != null && !mission.reachable) {
            notice = b.getRoot().getResources().getString(R.string.send_out_of_reach);
            render(snapshot);
            return;
        }
        if (mission != null && mission.scoutId != null && (team.isEmpty() || !mission.scoutId.equals(team.get(0)))) {
            // A personal mission: its owner leads, if they're here to.
            SessionSnapshot.ScoutView owner = scout(mission.scoutId);
            if (owner == null || !owner.canDispatch) {
                notice = b.getRoot().getResources().getString(R.string.send_only_leads_away,
                        owner != null ? owner.name : "?");
                render(snapshot);
                return;
            }
            makeLeader(owner.id);
        }
        destination = key.equals(destination) ? null : key;
        if (destination != null && team.isEmpty()) {
            step = Step.WHO; // place picked first: now who goes
        }
        render(snapshot);
    }

    /** The team moves at the slowest one's pace; nobody picked yet counts as normal. */
    private double teamPace() {
        double pace = 0;
        for (String id : team) {
            SessionSnapshot.ScoutView s = scout(id);
            if (s != null) {
                pace = Math.max(pace, s.pace);
            }
        }
        return pace > 0 ? pace : 1;
    }

    private static long trip(int min, int max, double pace) {
        return TimelineGenerator.tripMillis(min, max, pace);
    }

    /** "45 s", "12 min", "1 h 05 min", "6 h". */
    private static String time(Resources res, long millis) {
        long seconds = Math.round(millis / 1000.0);
        if (seconds < 60) {
            return res.getString(R.string.send_time_s, seconds);
        }
        long minutes = Math.round(seconds / 60.0);
        if (minutes < 60) {
            return res.getString(R.string.send_time_m, minutes);
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        return rest == 0 ? res.getString(R.string.send_time_h, hours)
                : res.getString(R.string.send_time_hm, hours, rest);
    }

    // ---- Sending ----

    /** "Mara is hurt (light injury)…", or null if everyone going is fine. */
    private String hurtWarning(Resources res) {
        List<String> hurt = new ArrayList<>();
        for (String id : team) {
            SessionSnapshot.ScoutView s = scout(id);
            if (s != null && s.hurt) {
                hurt.add(res.getString(R.string.send_hurt_who, s.name, s.injury));
            }
        }
        return hurt.isEmpty() ? null : res.getString(R.string.send_hurt_warning, String.join(", ", hurt));
    }

    private String summary(Resources res) {
        if (team.isEmpty()) {
            return destination == null ? res.getString(R.string.send_pick_team)
                    : res.getString(R.string.send_pick_team_for, placeName(destination));
        }
        StringBuilder who = new StringBuilder();
        for (String id : team) {
            if (who.length() > 0) {
                who.append(" + ");
            }
            who.append(scout(id).name);
        }
        if (destination == null) {
            return res.getString(R.string.send_pick_where, who);
        }
        return res.getString(R.string.send_ready, who, placeName(destination));
    }

    private String placeName(String key) {
        String[] parts = key.split("\\|", -1);
        for (SessionSnapshot.MissionView m : snapshot.missions) {
            if ((m.type + "|" + m.zoneId + "|" + (m.target != null ? m.target : "")).equals(key)) {
                return m.label;
            }
        }
        for (SessionSnapshot.ZoneView z : snapshot.zones) {
            if (z.id.equals(parts[1])) {
                return z.name;
            }
        }
        return parts[1];
    }

    private boolean destinationAvailable(String key) {
        for (SessionSnapshot.MissionView m : snapshot.missions) {
            if ((m.type + "|" + m.zoneId + "|" + (m.target != null ? m.target : "")).equals(key)) {
                return m.reachable;
            }
        }
        for (SessionSnapshot.ZoneView z : snapshot.zones) {
            if ((Mission.Type.EXPLORE.name() + "|" + z.id + "|").equals(key)) {
                return true;
            }
        }
        return false;
    }

    private void send() {
        if (team.isEmpty() || destination == null) {
            return;
        }
        if (!hurtWarned && hurtWarning(b.getRoot().getResources()) != null) {
            // First tap only warns: the second one sends them anyway.
            hurtWarned = true;
            render(snapshot);
            return;
        }
        hurtWarned = false;
        host.beforeSend();
        for (String id : team) {
            lastTeam.put(id, new ArrayList<>(team));
            lastDestination.put(id, destination);
        }
        String[] parts = destination.split("\\|", -1);
        viewModel.dispatch(team.get(0), parts[1], Mission.Type.valueOf(parts[0]),
                parts[2].isEmpty() ? null : parts[2], team.size() > 1 ? team.get(1) : null);
        host.close();
    }

    private SessionSnapshot.ScoutView scout(String id) {
        for (SessionSnapshot.ScoutView s : snapshot.scouts) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    private static String bar(int value, int max) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < max; i++) {
            out.append(i < value ? '■' : '□');
        }
        return out.toString();
    }

    private static void optional(TextView view, String text) {
        view.setText(text);
        view.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    // ---- List ----

    /** One row of the list: a crew tile, a place or mission block, or a note/heading. */
    private static final class Entry {
        final int type;
        final String key;
        SessionSnapshot.ScoutView scout;
        SessionSnapshot.ZoneView zone;
        SessionSnapshot.MissionView mission;
        String text;

        private Entry(int type, String key) {
            this.type = type;
            this.key = key;
        }

        static Entry scout(SessionSnapshot.ScoutView s) {
            Entry e = new Entry(TILE, "s:" + s.id);
            e.scout = s;
            return e;
        }

        static Entry zone(SessionSnapshot.ZoneView z) {
            Entry e = new Entry(PLACE, "z:" + z.id);
            e.zone = z;
            return e;
        }

        static Entry mission(SessionSnapshot.MissionView m) {
            Entry e = new Entry(PLACE, "m:" + m.type + m.zoneId + m.target);
            e.mission = m;
            return e;
        }

        static Entry note(String key, String text) {
            Entry e = new Entry(NOTE, "n:" + key);
            e.text = text;
            return e;
        }
    }

    private final class Adapter extends ListAdapter<Entry, RecyclerView.ViewHolder> {

        Adapter() {
            super(new DiffUtil.ItemCallback<Entry>() {
                @Override
                public boolean areItemsTheSame(@NonNull Entry a, @NonNull Entry b) {
                    return a.key.equals(b.key);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Entry a, @NonNull Entry b) {
                    return false; // small list, picks change the look: always rebind
                }
            });
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TILE) {
                ItemCrewTileBinding t = ItemCrewTileBinding.inflate(inflater, parent, false);
                return new Holder<>(t.getRoot(), t);
            }
            if (viewType == PLACE) {
                ItemPlaceBinding p = ItemPlaceBinding.inflate(inflater, parent, false);
                return new Holder<>(p.getRoot(), p);
            }
            TextView note = (TextView) inflater.inflate(R.layout.item_log_line, parent, false);
            note.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.amber_dim));
            note.setPadding(note.getPaddingLeft() + 8, 12, note.getPaddingRight(), 4);
            return new Holder<>(note, note);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Entry e = getItem(position);
            Object binding = ((Holder<?>) holder).binding;
            if (e.type == TILE) {
                bindScout((ItemCrewTileBinding) binding, e.scout);
            } else if (e.type == PLACE) {
                if (e.zone != null) {
                    bindZone((ItemPlaceBinding) binding, e.zone);
                } else {
                    bindMission((ItemPlaceBinding) binding, e.mission);
                }
            } else {
                ((TextView) binding).setText(e.text);
            }
        }
    }

    private static final class Holder<T> extends RecyclerView.ViewHolder {
        final T binding;

        Holder(View root, T binding) {
            super(root);
            this.binding = binding;
        }
    }
}
