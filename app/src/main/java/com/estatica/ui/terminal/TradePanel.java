package com.estatica.ui.terminal;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot;

/**
 * RADIO → TRADE: THE FAIR. While they're on the air: buy (by courier, for a cut, or waiting
 * at the fairground for someone to fetch) and sell goods or spare gear for chips. Off the
 * air, the prices still show, the buttons don't.
 */
final class TradePanel {

    private final LinearLayout root;
    private final TerminalViewModel viewModel;
    private boolean selling;
    private boolean courier = true;
    private SessionSnapshot.MarketView market;

    TradePanel(LinearLayout root, TerminalViewModel viewModel) {
        this.root = root;
        this.viewModel = viewModel;
    }

    void render(SessionSnapshot.MarketView m) {
        this.market = m;
        root.removeAllViews();
        Context context = root.getContext();
        Resources res = context.getResources();
        LayoutInflater inflater = LayoutInflater.from(context);
        if (m == null || !m.known) {
            line(inflater, res.getString(R.string.trade_unknown), R.color.amber_dim);
            return;
        }
        line(inflater, res.getString(R.string.trade_status, m.status), m.open ? R.color.amber_bright : R.color.amber_dim);
        line(inflater, res.getString(R.string.trade_wallet, m.chips, m.reputation), R.color.amber);

        LinearLayout modes = row(context);
        tab(inflater, modes, res.getString(R.string.trade_buy), !selling, v -> {
            selling = false;
            render(market);
        });
        tab(inflater, modes, res.getString(R.string.trade_sell), selling, v -> {
            selling = true;
            render(market);
        });
        root.addView(modes);

        if (!selling) {
            LinearLayout delivery = row(context);
            tab(inflater, delivery, res.getString(R.string.trade_courier, m.courierPercent), courier, v -> {
                courier = true;
                render(market);
            });
            tab(inflater, delivery, res.getString(R.string.trade_pickup), !courier, v -> {
                courier = false;
                render(market);
            });
            root.addView(delivery);
            for (SessionSnapshot.TradeRow r : m.buy) {
                buyRow(inflater, res, r);
            }
        } else {
            if (m.sell.isEmpty()) {
                line(inflater, res.getString(R.string.trade_nothing_to_sell), R.color.amber_dim);
            }
            for (SessionSnapshot.TradeRow r : m.sell) {
                sellRow(inflater, res, r);
            }
        }

        if (!m.incoming.isEmpty()) {
            line(inflater, res.getString(R.string.trade_incoming, String.join("\n", m.incoming)), R.color.signal);
        }
        if (!m.held.isEmpty()) {
            line(inflater, res.getString(R.string.trade_held, String.join("\n", m.held)), R.color.signal);
        }
    }

    /** Price per unit as shown: with the courier's cut when that's how it comes. */
    private int unitPrice(SessionSnapshot.TradeRow r) {
        return courier ? r.price + (int) Math.ceil(r.price * market.courierPercent / 100.0) : r.price;
    }

    private void buyRow(LayoutInflater inflater, Resources res, SessionSnapshot.TradeRow r) {
        LinearLayout block = block(inflater, res.getString(R.string.trade_buy_line, r.name, r.price), r.note);
        LinearLayout actions = row(root.getContext());
        if (r.bulk) {
            for (int n : new int[]{1, 5, 10}) {
                int cost = totalCost(r, n);
                boolean ok = market.open && cost <= market.chips;
                button(inflater, actions, res.getString(R.string.trade_buy_n, n, cost), ok,
                        v -> viewModel.buy(r.key, n, courier));
            }
        } else {
            int cost = totalCost(r, 1);
            button(inflater, actions, res.getString(R.string.trade_buy_one, cost), market.open && cost <= market.chips,
                    v -> viewModel.buy(r.key, 1, courier));
        }
        block.addView(actions);
        root.addView(block);
    }

    private int totalCost(SessionSnapshot.TradeRow r, int n) {
        int price = r.price * n;
        return courier ? price + (int) Math.ceil(price * market.courierPercent / 100.0) : price;
    }

    private void sellRow(LayoutInflater inflater, Resources res, SessionSnapshot.TradeRow r) {
        LinearLayout block = block(inflater, r.bulk ? res.getString(R.string.trade_sell_line, r.name, r.price, r.count)
                : res.getString(R.string.trade_sell_gear, r.name, r.price), r.note);
        LinearLayout actions = row(root.getContext());
        if (r.bulk) {
            for (int n : new int[]{1, 5}) {
                if (n <= r.count) {
                    button(inflater, actions, res.getString(R.string.trade_sell_n, n), market.open,
                            v -> viewModel.sell(r.key.substring(2), n));
                }
            }
            if (r.count > 1) {
                button(inflater, actions, res.getString(R.string.trade_sell_all, r.count), market.open,
                        v -> viewModel.sell(r.key.substring(2), r.count));
            }
        } else {
            button(inflater, actions, res.getString(R.string.trade_sell_one), market.open,
                    v -> viewModel.sellGear(r.key.substring(2)));
        }
        block.addView(actions);
        root.addView(block);
    }

    private LinearLayout block(LayoutInflater inflater, String title, String note) {
        Context context = root.getContext();
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setBackgroundResource(R.drawable.bg_slot_empty);
        int pad = Math.round(8 * context.getResources().getDisplayMetrics().density);
        block.setPadding(pad, pad / 2, pad, pad / 2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 2;
        block.setLayoutParams(lp);
        TextView name = (TextView) inflater.inflate(R.layout.item_log_line, block, false);
        name.setText(title);
        name.setTextColor(ContextCompat.getColor(context, R.color.amber_bright));
        block.addView(name);
        if (note != null) {
            TextView n = (TextView) inflater.inflate(R.layout.item_log_line, block, false);
            n.setText(note);
            n.setTextColor(ContextCompat.getColor(context, R.color.signal));
            block.addView(n);
        }
        return block;
    }

    private static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        return row;
    }

    private void line(LayoutInflater inflater, String text, int color) {
        TextView line = (TextView) inflater.inflate(R.layout.item_log_line, root, false);
        line.setText(text);
        line.setTextColor(ContextCompat.getColor(root.getContext(), color));
        root.addView(line);
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
