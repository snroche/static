package com.estatica.ui.terminal;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot;
import com.estatica.domain.expedition.Mission;

import java.util.ArrayList;
import java.util.List;

/**
 * One screen to send people out: tick up to two (the first ticked leads), pick a destination,
 * send. Sending one, then another, makes two separate expeditions; a team goes together.
 */
final class SendPanel {

    interface Host {
        void close();

        /** Notification permission is asked on the first dispatch. */
        void beforeSend();
    }

    private static final int MAX_TEAM = 2;

    private final com.estatica.databinding.PanelSendBinding b;
    private final TerminalViewModel viewModel;
    private final Host host;
    private SessionSnapshot snapshot;
    /** Ticked scout ids, lead first. */
    private final List<String> team = new ArrayList<>();
    /** Destination key: TYPE|zoneId|target. */
    private String destination;
    /** Last trip per scout (team, destination), to send the same again in one tap. */
    private final java.util.Map<String, List<String>> lastTeam = new java.util.HashMap<>();
    private final java.util.Map<String, String> lastDestination = new java.util.HashMap<>();

    SendPanel(com.estatica.databinding.PanelSendBinding binding, TerminalViewModel viewModel, Host host) {
        this.b = binding;
        this.viewModel = viewModel;
        this.host = host;
        b.sendClose.setOnClickListener(v -> host.close());
        b.sendGo.setOnClickListener(v -> send());
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
        if (leadId != null && destinationKey == null && lastTeam.containsKey(leadId)) {
            // Send again: same team, same place (changeable).
            team.addAll(lastTeam.get(leadId));
            destinationKey = lastDestination.get(leadId);
        } else if (leadId != null) {
            team.add(leadId);
        }
        destination = destinationKey;
        b.getRoot().setVisibility(View.VISIBLE);
        render(snapshot);
    }

    void hide() {
        b.getRoot().setVisibility(View.GONE);
    }

    void render(SessionSnapshot snapshot) {
        this.snapshot = snapshot;
        if (snapshot == null || !isShown()) {
            return;
        }
        // People who left meanwhile drop off the team.
        team.removeIf(id -> scout(id) == null || !scout(id).canDispatch);
        Resources res = b.getRoot().getResources();
        LayoutInflater inflater = LayoutInflater.from(b.getRoot().getContext());

        b.sendTeam.removeAllViews();
        SessionSnapshot.ScoutView lead = team.isEmpty() ? null : scout(team.get(0));
        for (SessionSnapshot.ScoutView s : snapshot.scouts) {
            if (!s.canDispatch) {
                continue;
            }
            boolean on = team.contains(s.id);
            String note = s.mood;
            if (lead != null && !s.id.equals(lead.id)) {
                String relation = lead.relationKinds.get(s.id);
                if ("rivals".equals(relation)) {
                    note = res.getString(R.string.partner_rivals);
                } else if (relation != null) {
                    note = res.getString(R.string.partner_friends);
                }
            }
            String role = on && s.id.equals(team.get(0)) ? res.getString(R.string.send_lead) : "";
            row(inflater, b.sendTeam, res.getString(R.string.send_member, on ? "x" : " ", s.name, role, note),
                    on, v -> toggle(s.id));
        }
        if (b.sendTeam.getChildCount() == 0) {
            note(inflater, b.sendTeam, res.getString(R.string.send_nobody));
        }

        b.sendWhere.removeAllViews();
        boolean any = false;
        for (SessionSnapshot.MissionView m : snapshot.missions) {
            if (m.scoutId != null && (lead == null || !m.scoutId.equals(lead.id))) {
                continue; // personal missions belong to whoever leads
            }
            String key = m.type + "|" + m.zoneId + "|" + (m.target != null ? m.target : "");
            row(inflater, b.sendWhere, res.getString(R.string.send_place, mark(key), m.label, m.zoneName),
                    key.equals(destination), v -> choose(key));
            any = true;
        }
        for (SessionSnapshot.ZoneView z : snapshot.zones) {
            String key = Mission.Type.EXPLORE.name() + "|" + z.id + "|";
            StringBuilder label = new StringBuilder(res.getString(R.string.send_zone, mark(key), z.name, z.band,
                    z.minMinutes, z.maxMinutes, z.danger));
            if (z.relay >= 0) {
                label.append(" · ").append(res.getString(R.string.zone_relay, z.relay));
            }
            if (z.intel) {
                label.append(" · ").append(res.getString(R.string.zone_intel));
            }
            if (z.depleted >= 15) {
                label.append(" · ").append(res.getString(R.string.zone_depleted, z.depleted));
            }
            row(inflater, b.sendWhere, label.toString(), key.equals(destination), v -> choose(key));
            any = true;
        }
        if (!any) {
            note(inflater, b.sendWhere, res.getString(R.string.zones_none));
        }

        if (destination != null && !destinationAvailable(destination)) {
            destination = null; // gone since last time (out of reach, mission over)
        }
        boolean ready = !team.isEmpty() && destination != null;
        b.sendGo.setAlpha(ready ? 1f : 0.4f);
        b.sendGo.setEnabled(ready);
        b.sendSummary.setText(summary(res));
    }

    private String summary(Resources res) {
        if (team.isEmpty()) {
            return res.getString(R.string.send_pick_team);
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
                return true;
            }
        }
        for (SessionSnapshot.ZoneView z : snapshot.zones) {
            if ((Mission.Type.EXPLORE.name() + "|" + z.id + "|").equals(key)) {
                return true;
            }
        }
        return false;
    }

    private String mark(String key) {
        return key.equals(destination) ? "•" : " ";
    }

    private void toggle(String id) {
        if (!team.remove(id)) {
            if (team.size() >= MAX_TEAM) {
                team.remove(team.size() - 1); // swap the partner
            }
            team.add(id);
        }
        render(snapshot);
    }

    private void choose(String key) {
        destination = key.equals(destination) ? null : key;
        render(snapshot);
    }

    private void send() {
        if (team.isEmpty() || destination == null) {
            return;
        }
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

    private static void row(LayoutInflater inflater, LinearLayout parent, String text, boolean on,
                            View.OnClickListener click) {
        TextView row = (TextView) inflater.inflate(R.layout.item_decision_option, parent, false);
        row.setText(text);
        row.setTextColor(ContextCompat.getColor(parent.getContext(), on ? R.color.amber_bright : R.color.amber));
        row.setOnClickListener(click);
        parent.addView(row);
    }

    private static void note(LayoutInflater inflater, LinearLayout parent, String text) {
        TextView line = (TextView) inflater.inflate(R.layout.item_log_line, parent, false);
        line.setText(text);
        parent.addView(line);
    }
}
