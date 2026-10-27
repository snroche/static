package com.estatica.ui.terminal;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.estatica.R;
import com.estatica.data.link.NearbyLink;
import com.estatica.data.session.SessionSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RADIO → LINK: other bunkers (GDD §12). SEND makes codes (a message, a drop, a wanderer, last
 * words), RECEIVE takes them (typed, pasted or scanned), NEARBY links two phones over Bluetooth
 * for trades and joint runs. A code just made opens in a popup ({@link CodeDialog}).
 *
 * <p>Rebuilt on every render like the trade panel, except the text fields, which are kept and
 * re-attached so typing survives; while one has focus, renders wait until it's let go.
 */
final class LinkPanel {

    interface Host {
        /** A code was just made (or picked to show again): TEXT · QR · NEARBY popup. */
        void showCode(String code, String label, String callsign);

        void scanQr();

        /** Asks for Bluetooth permissions (and discoverability when hosting), then starts. */
        void nearby(boolean host);

        /** A code was taken (sound: a chirp when it came through, a click when it didn't). */
        void received(boolean ok);
    }

    /** Resource icons for the goods rows. */
    private static final Map<String, Integer> ICONS = Map.of(
            "scrap", R.string.ic_scrap, "components", R.string.ic_components, "parts", R.string.ic_parts,
            "rations", R.string.ic_rations, "medicine", R.string.ic_medicine, "batteries", R.string.ic_batteries,
            "fuel", R.string.ic_fuel, "relay_kit", R.string.ic_relay, "chips", R.string.ic_chips);

    private enum Mode { SEND, RECEIVE, NEARBY }

    private enum Kind { MESSAGE, DROP, WANDERER, LAST_WORDS }

    private final LinearLayout root;
    private final TerminalViewModel viewModel;
    private final Host host;
    private final EditText message;
    private final EditText note;
    private final EditText code;
    private Mode mode = Mode.SEND;
    private Kind kind = Kind.MESSAGE;
    private String scheme = "PLAIN";
    private final Map<String, Integer> drop = new LinkedHashMap<>();
    private final Set<String> dropGear = new LinkedHashSet<>();
    private String jointZone;
    private SessionSnapshot.LinkView link;
    private LinkController.View near = new LinkController.View();
    private boolean deferred;
    /** The code last handed to the popup, so each one opens it once. */
    private String popped;
    /** Where the building blocks add views: the panel, or a popup's body while filling it. */
    private LinearLayout into;
    private Popup tradePopup;
    /** What the last incoming popup showed (the session's list), so each opens once. */
    private List<String> shownIncoming;

    LinkPanel(LinearLayout root, TerminalViewModel viewModel, Host host) {
        this.root = root;
        this.viewModel = viewModel;
        this.host = host;
        this.into = root;
        LayoutInflater inflater = LayoutInflater.from(root.getContext());
        message = input(inflater, R.string.link_message_hint, 4);
        note = input(inflater, R.string.link_note_hint, 2);
        code = input(inflater, R.string.link_code_hint, 6);
    }

    private EditText input(LayoutInflater inflater, int hint, int lines) {
        EditText e = (EditText) inflater.inflate(R.layout.item_link_input, root, false);
        e.setHint(hint);
        e.setMaxLines(lines);
        e.setOnFocusChangeListener((v, focused) -> {
            if (!focused && deferred) {
                render();
            }
        });
        return e;
    }

    void render(SessionSnapshot.LinkView link) {
        this.link = link;
        if (link.code == null) {
            popped = null;
        } else if (!link.code.equals(popped)) {
            popped = link.code;
            host.showCode(link.code, link.codeLabel, link.callsign);
        }
        if (link.incoming != null && link.incoming != shownIncoming) {
            shownIncoming = link.incoming;
            showIncoming(link.incoming, link.incomingOk);
        }
        render();
    }

    /** What a code just taken brought, in its own popup (the log has it too). */
    private void showIncoming(List<String> lines, boolean ok) {
        Context context = root.getContext();
        Popup popup = Popup.open(context, context.getString(ok ? R.string.link_incoming_title
                : R.string.link_incoming_failed), viewModel::linkHideIncoming);
        LayoutInflater inflater = LayoutInflater.from(context);
        into = popup.body();
        for (String text : lines) {
            line(inflater, text, ok ? R.color.amber_bright : R.color.danger);
        }
        LinearLayout actions = row(context);
        button(inflater, actions, context.getString(R.string.link_ok), true, v -> popup.dismiss());
        into.addView(actions);
        into = root;
        host.received(ok);
    }

    /** Straight to NEARBY (from the code popup, to set up a link). */
    void openNearby() {
        doneTyping();
        mode = Mode.NEARBY;
        render();
    }

    void renderNearby(LinkController.View near) {
        this.near = near;
        render();
    }

    private boolean editing() {
        return message.hasFocus() || note.hasFocus() || code.hasFocus();
    }

    /** Lets go of the keyboard before acting on what was typed. */
    private void doneTyping() {
        View focused = editing() ? (message.hasFocus() ? message : note.hasFocus() ? note : code) : null;
        if (focused != null) {
            InputMethodManager imm = (InputMethodManager) root.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            }
            focused.clearFocus();
        }
    }

    private void render() {
        if (link == null || root.getVisibility() != View.VISIBLE) {
            return;
        }
        if (editing()) {
            deferred = true;
            return;
        }
        deferred = false;
        if (tradePopup != null) {
            if (near.state == NearbyLink.State.CONNECTED) {
                fillTrade();
            } else {
                tradePopup.dismiss();
            }
        }
        detach(message);
        detach(note);
        detach(code);
        root.removeAllViews();
        Context context = root.getContext();
        Resources res = context.getResources();
        LayoutInflater inflater = LayoutInflater.from(context);
        line(inflater, res.getString(R.string.link_callsign, link.callsign), R.color.amber_bright);

        LinearLayout modes = row(context);
        tab(inflater, modes, res.getString(R.string.link_send), mode == Mode.SEND, v -> setMode(Mode.SEND));
        tab(inflater, modes, res.getString(R.string.link_receive), mode == Mode.RECEIVE, v -> setMode(Mode.RECEIVE));
        tab(inflater, modes, near.state == NearbyLink.State.CONNECTED ? res.getString(R.string.link_nearby_on)
                : res.getString(R.string.link_nearby), mode == Mode.NEARBY, v -> setMode(Mode.NEARBY));
        root.addView(modes);

        switch (mode) {
            case SEND:
                send(inflater, res);
                break;
            case RECEIVE:
                receive(inflater, res);
                break;
            default:
                nearby(inflater, res);
                break;
        }
    }

    private static void detach(View v) {
        if (v.getParent() instanceof LinearLayout) {
            ((LinearLayout) v.getParent()).removeView(v);
        }
    }

    private void setMode(Mode m) {
        doneTyping();
        mode = m;
        render();
    }

    // ---- SEND ----

    private void send(LayoutInflater inflater, Resources res) {
        LinearLayout kinds = row(root.getContext());
        tab(inflater, kinds, res.getString(R.string.link_kind_message), kind == Kind.MESSAGE, v -> setKind(Kind.MESSAGE));
        tab(inflater, kinds, res.getString(R.string.link_kind_drop), kind == Kind.DROP, v -> setKind(Kind.DROP));
        root.addView(kinds);
        LinearLayout people = row(root.getContext());
        tab(inflater, people, res.getString(R.string.link_kind_wanderer), kind == Kind.WANDERER, v -> setKind(Kind.WANDERER));
        tab(inflater, people, res.getString(R.string.link_kind_last_words), kind == Kind.LAST_WORDS,
                v -> setKind(Kind.LAST_WORDS));
        root.addView(people);

        switch (kind) {
            case MESSAGE:
                line(inflater, res.getString(R.string.link_message_help, link.messageMaxChars), R.color.amber_dim);
                root.addView(message);
                LinearLayout schemes = row(root.getContext());
                int n = 0;
                for (SessionSnapshot.LinkChoice s : link.schemes) {
                    if (n++ % 3 == 0 && n > 1) {
                        root.addView(schemes);
                        schemes = row(root.getContext());
                    }
                    choice(inflater, schemes, s.name, s.id.equals(scheme), s.enabled, v -> {
                        doneTyping();
                        scheme = s.id;
                        render();
                    });
                }
                root.addView(schemes);
                LinearLayout go = row(root.getContext());
                button(inflater, go, res.getString(R.string.link_make_code), true, v -> {
                    doneTyping();
                    String text = message.getText().toString();
                    if (!text.isBlank()) {
                        viewModel.linkMessage(text, scheme);
                        message.setText("");
                    }
                });
                root.addView(go);
                break;
            case DROP:
                dropEditor(inflater, res);
                break;
            case WANDERER:
                line(inflater, res.getString(R.string.link_wanderer_help), R.color.amber_dim);
                for (SessionSnapshot.LinkChoice c : link.crew) {
                    option(inflater, c.name, v -> viewModel.linkWanderer(c.id));
                }
                break;
            default:
                line(inflater, res.getString(R.string.link_last_words_help), R.color.amber_dim);
                if (link.fallen.isEmpty()) {
                    line(inflater, res.getString(R.string.link_no_fallen), R.color.amber_dim);
                }
                for (SessionSnapshot.LinkChoice c : link.fallen) {
                    option(inflater, c.name, v -> viewModel.linkLastWords(c.id));
                }
                break;
        }

        if (!link.sent.isEmpty()) {
            line(inflater, res.getString(R.string.link_sent_title), R.color.amber);
            for (SessionSnapshot.LinkChoice c : link.sent) {
                option(inflater, c.name, v -> viewModel.linkShow(c.id));
            }
        }
    }

    private void setKind(Kind k) {
        doneTyping();
        kind = k;
        render();
    }

    private void dropEditor(LayoutInflater inflater, Resources res) {
        int units = 0;
        for (int v : drop.values()) {
            units += v;
        }
        dropGear.removeIf(id -> {
            for (SessionSnapshot.LinkChoice g : link.gear) {
                if (g.id.equals(id)) {
                    return false;
                }
            }
            return true;
        });
        line(inflater, res.getString(R.string.link_drop_help, units, link.dropMaxUnits, dropGear.size(), link.dropMaxGear),
                R.color.amber_dim);
        final int total = units;
        for (SessionSnapshot.LinkChoice g : link.goods) {
            int chosen = Math.min(drop.getOrDefault(g.id, 0), g.count);
            if (g.count == 0 && chosen == 0) {
                continue;
            }
            goodRow(inflater, g, chosen, chosen < g.count && total < link.dropMaxUnits, delta -> {
                int room = link.dropMaxUnits - total;
                int next = Math.max(0, Math.min(g.count, chosen + Math.min(delta, room)));
                if (next == 0) {
                    drop.remove(g.id);
                } else {
                    drop.put(g.id, next);
                }
                doneTyping();
                render();
            });
        }
        for (SessionSnapshot.LinkChoice g : link.gear) {
            boolean on = dropGear.contains(g.id);
            boolean ok = on || dropGear.size() < link.dropMaxGear;
            choiceRow(inflater, (on ? "[■] " : "[ ] ") + g.name, ok, v -> {
                if (!dropGear.remove(g.id)) {
                    dropGear.add(g.id);
                }
                doneTyping();
                render();
            });
        }
        root.addView(note);
        LinearLayout go = row(root.getContext());
        boolean any = units > 0 || !dropGear.isEmpty();
        button(inflater, go, res.getString(R.string.link_pack), any, v -> {
            doneTyping();
            viewModel.linkDrop(new LinkedHashMap<>(drop), new ArrayList<>(dropGear), note.getText().toString());
            drop.clear();
            dropGear.clear();
            note.setText("");
        });
        root.addView(go);
    }

    // ---- RECEIVE ----

    private void receive(LayoutInflater inflater, Resources res) {
        line(inflater, res.getString(R.string.link_receive_help, link.takenToday, link.dailyLimit), R.color.amber_dim);
        root.addView(code);
        LinearLayout actions = row(root.getContext());
        button(inflater, actions, res.getString(R.string.link_take), true, v -> {
            doneTyping();
            String text = code.getText().toString();
            if (!text.isBlank()) {
                viewModel.linkReceive(text);
                code.setText("");
            }
        });
        button(inflater, actions, res.getString(R.string.link_paste), true, v -> paste());
        button(inflater, actions, res.getString(R.string.link_scan), true, v -> {
            doneTyping();
            host.scanQr();
        });
        root.addView(actions);
        if (!link.waiting.isEmpty()) {
            line(inflater, res.getString(R.string.link_waiting_title), R.color.amber);
            for (String w : link.waiting) {
                line(inflater, w, R.color.signal);
            }
        }
        if (!link.received.isEmpty()) {
            line(inflater, res.getString(R.string.link_received_title), R.color.amber);
            for (String r : link.received) {
                line(inflater, "· " + r, R.color.amber_dim);
            }
        }
    }

    private void paste() {
        ClipboardManager clipboard = (ClipboardManager) root.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
            CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(root.getContext());
            code.setText(text);
        }
    }

    // ---- NEARBY ----

    private void nearby(LayoutInflater inflater, Resources res) {
        LinkController controller = viewModel.link();
        if (!controller.available()) {
            line(inflater, res.getString(R.string.link_no_bluetooth), R.color.amber_dim);
            return;
        }
        if (near.failed) {
            line(inflater, res.getString(R.string.link_failed), R.color.danger);
        }
        LinearLayout actions = row(root.getContext());
        switch (near.state) {
            case OFF:
                line(inflater, res.getString(R.string.link_nearby_help), R.color.amber_dim);
                button(inflater, actions, res.getString(R.string.link_host), true, v -> host.nearby(true));
                button(inflater, actions, res.getString(R.string.link_find), true, v -> host.nearby(false));
                root.addView(actions);
                return;
            case HOSTING:
                line(inflater, res.getString(R.string.link_hosting, link.callsign), R.color.signal);
                break;
            case SEARCHING:
                line(inflater, res.getString(R.string.link_searching), R.color.signal);
                for (String[] d : near.devices) {
                    option(inflater, d[1], v -> controller.connect(d[0]));
                }
                break;
            case CONNECTING:
                line(inflater, res.getString(R.string.link_connecting), R.color.signal);
                break;
            default:
                line(inflater, res.getString(R.string.link_connected, near.peer != null ? near.peer : "…"),
                        R.color.amber_bright);
                break;
        }
        button(inflater, actions, res.getString(near.state == NearbyLink.State.CONNECTED ? R.string.link_hang_up
                : R.string.link_cancel), true, v -> controller.close());
        root.addView(actions);
        if (near.state == NearbyLink.State.CONNECTED) {
            line(inflater, res.getString(R.string.link_trade_title), R.color.amber);
            line(inflater, res.getString(R.string.link_their_offer,
                    near.theirOffer != null ? near.theirOffer : res.getString(R.string.link_waiting_offer)), R.color.signal);
            if (near.theirReady && !near.myReady) {
                line(inflater, res.getString(R.string.link_they_agreed), R.color.amber_bright);
            }
            LinearLayout open = row(root.getContext());
            button(inflater, open, res.getString(R.string.link_open_trade), true, v -> openTrade());
            root.addView(open);
            joint(inflater, res, controller);
        }
    }

    private void openTrade() {
        Context context = root.getContext();
        tradePopup = Popup.open(context, context.getString(R.string.link_trade_with,
                near.peer != null ? near.peer : "…"), () -> tradePopup = null);
        fillTrade();
    }

    /** The trade popup's body, rebuilt on every change (offers come and go while it's open). */
    private void fillTrade() {
        Context context = root.getContext();
        tradePopup.body().removeAllViews();
        into = tradePopup.body();
        trade(LayoutInflater.from(context), context.getResources(), viewModel.link());
        into = root;
    }

    private void trade(LayoutInflater inflater, Resources res, LinkController controller) {
        line(inflater, res.getString(R.string.link_trade_help), R.color.amber_dim);
        line(inflater, res.getString(R.string.link_you_give), R.color.amber);
        for (SessionSnapshot.LinkChoice g : link.goods) {
            int chosen = near.goods.getOrDefault(g.id, 0);
            if (g.count == 0 && chosen == 0) {
                continue;
            }
            goodRow(inflater, g, chosen, chosen < g.count, delta -> controller.changeGood(g.id, delta, g.count));
        }
        for (SessionSnapshot.LinkChoice g : link.gear) {
            boolean on = near.gear.contains(g.id);
            choiceRow(inflater, (on ? "[■] " : "[ ] ") + g.name, true, v -> controller.toggleGear(g.id));
        }
        if (near.offerInvalid) {
            line(inflater, res.getString(R.string.link_offer_invalid), R.color.danger);
        }
        line(inflater, res.getString(R.string.link_their_offer,
                near.theirOffer != null ? near.theirOffer : res.getString(R.string.link_waiting_offer)), R.color.signal);
        String status = near.myReady && !near.theirReady ? res.getString(R.string.link_you_agreed)
                : near.theirReady && !near.myReady ? res.getString(R.string.link_they_agreed) : null;
        if (status != null) {
            line(inflater, status, R.color.amber_bright);
        }
        LinearLayout go = row(root.getContext());
        button(inflater, go, res.getString(R.string.link_agree), !near.myReady && !near.offerInvalid
                && near.theirOffer != null, v -> controller.agree());
        into.addView(go);
    }

    /** One good: icon, name and how many of how many, then − and + (hold for five). */
    private void goodRow(LayoutInflater inflater, SessionSnapshot.LinkChoice g, int chosen, boolean canAdd,
                         java.util.function.IntConsumer change) {
        Context context = root.getContext();
        LinearLayout row = row(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = (TextView) inflater.inflate(R.layout.item_log_line, row, false);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Integer icon = ICONS.get(g.id);
        name.setText(context.getString(R.string.link_good_row, icon != null ? context.getString(icon) : "·",
                g.name, chosen, g.count));
        name.setTextColor(ContextCompat.getColor(context, chosen > 0 ? R.color.amber_bright : R.color.amber));
        row.addView(name);
        stepper(inflater, row, "−", chosen > 0, -1, change);
        stepper(inflater, row, "+", canAdd, 1, change);
        into.addView(row);
    }

    private void stepper(LayoutInflater inflater, LinearLayout parent, String label, boolean enabled, int sign,
                         java.util.function.IntConsumer change) {
        TextView b = (TextView) inflater.inflate(R.layout.item_tier_tab, parent, false);
        int width = Math.round(52 * parent.getResources().getDisplayMetrics().density);
        b.setLayoutParams(new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT));
        b.setText(label);
        b.setContentDescription(parent.getResources().getString(sign > 0 ? R.string.link_more : R.string.link_less));
        b.setAlpha(enabled ? 1f : 0.4f);
        b.setClickable(enabled);
        b.setOnClickListener(enabled ? v -> change.accept(sign) : null);
        b.setOnLongClickListener(enabled ? v -> {
            change.accept(5 * sign);
            return true;
        } : null);
        parent.addView(b);
    }

    private void joint(LayoutInflater inflater, Resources res, LinkController controller) {
        line(inflater, res.getString(R.string.link_joint_title), R.color.amber);
        if (near.jointSent || near.theirJoint) {
            line(inflater, res.getString(near.jointSent ? R.string.link_joint_waiting : R.string.link_joint_theirs),
                    R.color.signal);
        }
        if (near.jointSent) {
            return;
        }
        if (link.places.isEmpty()) {
            line(inflater, res.getString(R.string.link_joint_no_places), R.color.amber_dim);
            return;
        }
        line(inflater, res.getString(R.string.link_joint_help), R.color.amber_dim);
        if (jointZone == null) {
            for (SessionSnapshot.LinkChoice p : link.places) {
                option(inflater, p.name, v -> {
                    jointZone = p.id;
                    render();
                });
            }
            return;
        }
        String zoneName = jointZone;
        for (SessionSnapshot.LinkChoice p : link.places) {
            if (p.id.equals(jointZone)) {
                zoneName = p.name;
            }
        }
        line(inflater, res.getString(R.string.link_joint_place, zoneName), R.color.amber_bright);
        line(inflater, res.getString(R.string.link_joint_who), R.color.amber_dim);
        for (SessionSnapshot.LinkChoice c : link.crew) {
            option(inflater, c.name, v -> {
                controller.joint(jointZone, c.id);
                jointZone = null;
            });
        }
        LinearLayout back = row(root.getContext());
        button(inflater, back, res.getString(R.string.link_cancel), true, v -> {
            jointZone = null;
            render();
        });
        root.addView(back);
    }

    // ---- Building blocks (as in TradePanel) ----

    private static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        return row;
    }

    private void line(LayoutInflater inflater, String text, int color) {
        TextView line = (TextView) inflater.inflate(R.layout.item_log_line, into, false);
        line.setText(text);
        line.setTextColor(ContextCompat.getColor(root.getContext(), color));
        into.addView(line);
    }

    /** A full-width row to tap (people, places, devices, codes sent). */
    private void option(LayoutInflater inflater, String text, View.OnClickListener click) {
        choiceRow(inflater, text, true, click);
    }

    private void choiceRow(LayoutInflater inflater, String text, boolean enabled, View.OnClickListener click) {
        TextView row = (TextView) inflater.inflate(R.layout.item_decision_option, into, false);
        row.setText(text);
        row.setAlpha(enabled ? 1f : 0.4f);
        row.setOnClickListener(enabled ? click : null);
        row.setClickable(enabled);
        into.addView(row);
    }

    private static void tab(LayoutInflater inflater, LinearLayout parent, String text, boolean on,
                            View.OnClickListener click) {
        TextView tab = (TextView) inflater.inflate(R.layout.item_tier_tab, parent, false);
        tab.setText(text);
        tab.setTextColor(ContextCompat.getColor(parent.getContext(), on ? R.color.amber_bright : R.color.amber_dim));
        BracketDrawable.mark(tab, on);
        tab.setOnClickListener(click);
        parent.addView(tab);
    }

    /** A tab that may be locked (schemes whose key item isn't found yet). */
    private static void choice(LayoutInflater inflater, LinearLayout parent, String text, boolean on, boolean enabled,
                               View.OnClickListener click) {
        tab(inflater, parent, text, on, enabled ? click : null);
        View last = parent.getChildAt(parent.getChildCount() - 1);
        last.setAlpha(enabled ? 1f : 0.4f);
        last.setClickable(enabled);
    }

    private static void button(LayoutInflater inflater, LinearLayout parent, String text, boolean enabled,
                               View.OnClickListener click) {
        TextView button = (TextView) inflater.inflate(R.layout.item_tier_tab, parent, false);
        button.setText(text);
        button.setAlpha(enabled ? 1f : 0.4f);
        button.setOnClickListener(enabled ? click : null);
        button.setClickable(enabled);
        parent.addView(button);
    }
}
