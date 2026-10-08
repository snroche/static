package com.estatica.ui.terminal;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot;
import com.estatica.databinding.PanelInventoryBinding;

/** Everything the bunker has: supplies, gear, keepsakes and key items (readable). */
final class InventoryPanel {

    interface Host {
        void closeInventory();

        /** Shows a key item's text. */
        void read(String title, String text);
    }

    private enum Tab { SUPPLIES, GEAR, KEEPSAKES, KEYS }

    private final PanelInventoryBinding b;
    private final Host host;
    private Tab tab = Tab.SUPPLIES;
    private SessionSnapshot snapshot;

    InventoryPanel(PanelInventoryBinding binding, Host host) {
        this.b = binding;
        this.host = host;
        b.inventoryClose.setOnClickListener(v -> host.closeInventory());
        b.tabSupplies.setOnClickListener(v -> select(Tab.SUPPLIES));
        b.tabGear.setOnClickListener(v -> select(Tab.GEAR));
        b.tabKeepsakes.setOnClickListener(v -> select(Tab.KEEPSAKES));
        b.tabKeys.setOnClickListener(v -> select(Tab.KEYS));
    }

    boolean isShown() {
        return b.getRoot().getVisibility() == View.VISIBLE;
    }

    void show() {
        b.getRoot().setVisibility(View.VISIBLE);
        render(snapshot);
    }

    void showKeys() {
        tab = Tab.KEYS;
        show();
    }

    void hide() {
        b.getRoot().setVisibility(View.GONE);
    }

    private void select(Tab t) {
        tab = t;
        render(snapshot);
    }

    void render(SessionSnapshot snapshot) {
        this.snapshot = snapshot;
        if (snapshot == null || !isShown()) {
            return;
        }
        Resources res = b.getRoot().getResources();
        tabLabel(b.tabSupplies, Tab.SUPPLIES, res.getString(R.string.inventory_supplies));
        tabLabel(b.tabGear, Tab.GEAR, res.getString(R.string.inventory_gear));
        tabLabel(b.tabKeepsakes, Tab.KEEPSAKES, res.getString(R.string.inventory_keepsakes));
        tabLabel(b.tabKeys, Tab.KEYS, res.getString(R.string.inventory_keys, snapshot.keyItems.size()));
        b.inventoryList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(b.getRoot().getContext());
        switch (tab) {
            case SUPPLIES:
                for (SessionSnapshot.ResourceView r : snapshot.resources) {
                    String desc = snapshot.glossary.get(r.name);
                    line(inflater, res.getString(R.string.inventory_supply,
                            r.icon != null ? r.icon : "·", r.name, r.amount), desc);
                }
                break;
            case GEAR:
                for (SessionSnapshot.GearView g : snapshot.base.gear) {
                    line(inflater, g.holder != null ? res.getString(R.string.gear_line_worn_by, g.label, g.holder)
                            : g.label, null);
                }
                if (snapshot.base.gear.isEmpty()) {
                    line(inflater, res.getString(R.string.gear_empty), null);
                }
                break;
            case KEEPSAKES:
                for (SessionSnapshot.ItemView i : snapshot.inventory) {
                    line(inflater, i.name, i.description);
                }
                if (snapshot.inventory.isEmpty()) {
                    line(inflater, res.getString(R.string.inventory_no_keepsakes), null);
                }
                break;
            default:
                for (SessionSnapshot.KeyItemView k : snapshot.keyItems) {
                    TextView button = (TextView) inflater.inflate(R.layout.item_decision_option, b.inventoryList, false);
                    button.setText(res.getString(R.string.inventory_key_line, k.name));
                    button.setOnClickListener(v -> host.read(k.name, k.text != null ? k.text : k.description));
                    b.inventoryList.addView(button);
                    line(inflater, null, k.description);
                }
                if (snapshot.keyItems.isEmpty()) {
                    line(inflater, res.getString(R.string.inventory_no_keys), null);
                }
                break;
        }
    }

    private void line(LayoutInflater inflater, String title, String description) {
        if (title != null) {
            TextView t = (TextView) inflater.inflate(R.layout.item_log_line, b.inventoryList, false);
            t.setText(title);
            b.inventoryList.addView(t);
        }
        if (description != null) {
            TextView d = (TextView) inflater.inflate(R.layout.item_log_line, b.inventoryList, false);
            d.setText(description);
            d.setTextColor(ContextCompat.getColor(d.getContext(), R.color.amber_dim));
            d.setTextSize(13);
            d.setPadding(d.getPaddingLeft() + 24, 0, 0, 12);
            b.inventoryList.addView(d);
        }
    }

    private void tabLabel(TextView view, Tab which, String label) {
        boolean selected = tab == which;
        view.setText(selected ? view.getResources().getString(R.string.tab_selected, label) : label);
        view.setTextColor(ContextCompat.getColor(view.getContext(), selected ? R.color.amber_bright : R.color.amber_dim));
    }
}
