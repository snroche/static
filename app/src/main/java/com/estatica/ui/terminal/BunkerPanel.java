package com.estatica.ui.terminal;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot;
import com.estatica.databinding.PanelBunkerBinding;

/** The bunker panel: power steppers, reserve, workshop jobs, module upgrades, defense. */
final class BunkerPanel {

    interface Host {
        void closeBunker();

        void pickRecipe();

        /** Long-press on a line: explain it. */
        default void explain(CharSequence text) {
        }
    }

    private final PanelBunkerBinding b;
    private final TerminalViewModel viewModel;
    private final Host host;
    private SessionSnapshot snapshot;

    private void explainable(TextView view) {
        view.setOnLongClickListener(v -> {
            host.explain(view.getText());
            return true;
        });
    }

    BunkerPanel(PanelBunkerBinding binding, TerminalViewModel viewModel, Host host) {
        this.b = binding;
        this.viewModel = viewModel;
        this.host = host;
        b.bunkerClose.setOnClickListener(v -> host.closeBunker());
        b.recharge.setOnClickListener(v -> viewModel.recharge());
        b.craft.setOnClickListener(v -> host.pickRecipe());
    }

    boolean isShown() {
        return b.getRoot().getVisibility() == View.VISIBLE;
    }

    void show() {
        b.getRoot().setVisibility(View.VISIBLE);
        if (snapshot != null) {
            render(snapshot);
        }
    }

    void hide() {
        b.getRoot().setVisibility(View.GONE);
    }

    void render(SessionSnapshot snapshot) {
        this.snapshot = snapshot;
        if (!isShown()) {
            return;
        }
        Resources res = b.getRoot().getResources();
        LayoutInflater inflater = LayoutInflater.from(b.getRoot().getContext());
        SessionSnapshot.BaseView base = snapshot.base;

        StringBuilder goals = new StringBuilder();
        for (SessionSnapshot.GoalView g : base.goals) {
            if (goals.length() > 0) {
                goals.append('\n');
            }
            String check = g.progress >= g.target ? "[x]" : "[ ]";
            goals.append(res.getString(R.string.goal_line, check, g.label, g.progress, g.target));
        }
        b.goals.setText(goals);

        StringBuilder energy = new StringBuilder(res.getString(R.string.energy_line, base.used, base.available, base.watts));
        if (base.blackout) {
            energy.append('\n').append(res.getString(R.string.energy_blackout));
        }
        if (base.night) {
            energy.append('\n').append(res.getString(R.string.energy_night));
        }
        if (base.filtersShort) {
            energy.append('\n').append(res.getString(R.string.energy_bad_air));
        }
        b.energy.setText(energy);

        b.consumers.removeAllViews();
        for (SessionSnapshot.ConsumerView c : base.consumers) {
            LinearLayout row = new LinearLayout(b.getRoot().getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView label = (TextView) inflater.inflate(R.layout.item_log_line, row, false);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            label.setText(res.getString(R.string.consumer_line, c.name, bar(c.effective, c.requested, c.max)));
            label.setContentDescription(res.getString(R.string.consumer_description, c.name, c.effective, c.max, c.description));
            row.addView(label);
            row.addView(stepper(inflater, row, res.getString(R.string.step_down), c.requested > 0,
                    v -> viewModel.setPower(c.id, c.requested - 1),
                    res.getString(R.string.power_less, c.name)));
            row.addView(stepper(inflater, row, res.getString(R.string.step_up), c.requested < c.max,
                    v -> viewModel.setPower(c.id, c.requested + 1),
                    res.getString(R.string.power_more, c.name)));
            b.consumers.addView(row);
        }

        b.reserve.setText(res.getString(R.string.reserve_line, base.reserve, base.reserveCapacity));
        b.recharge.setAlpha(base.canRecharge && base.reserve < base.reserveCapacity ? 1f : 0.5f);

        b.jobs.removeAllViews();
        for (SessionSnapshot.JobView job : base.jobs) {
            TextView line = (TextView) inflater.inflate(R.layout.item_log_line, b.jobs, false);
            line.setText(job.minutesLeft < 0
                    ? res.getString(R.string.job_paused, job.name, job.percent)
                    : res.getString(R.string.job_line, job.name, job.percent, job.minutesLeft));
            b.jobs.addView(line);
        }
        if (base.jobs.isEmpty()) {
            TextView none = (TextView) inflater.inflate(R.layout.item_log_line, b.jobs, false);
            none.setText(R.string.workshop_idle);
            b.jobs.addView(none);
        }
        b.craft.setText(res.getString(R.string.craft_slots, base.jobs.size(), base.slots));
        b.craft.setAlpha(base.jobs.size() < base.slots ? 1f : 0.5f);

        b.modules.removeAllViews();
        for (SessionSnapshot.ModuleView m : base.modules) {
            TextView line = (TextView) inflater.inflate(R.layout.item_log_line, b.modules, false);
            line.setText(res.getString(R.string.module_line, m.name, m.level, m.maxLevel));
            explainable(line);
            b.modules.addView(line);
            if (m.now != null) {
                TextView now = (TextView) inflater.inflate(R.layout.item_log_line, b.modules, false);
                now.setText(m.next != null ? res.getString(R.string.module_now_next, m.now, m.next)
                        : res.getString(R.string.module_now_max, m.now));
                now.setAlpha(0.8f);
                explainable(now);
                b.modules.addView(now);
            }
            if (m.upgradeCost != null) {
                TextView upgrade = (TextView) inflater.inflate(R.layout.item_decision_option, b.modules, false);
                upgrade.setText(res.getString(R.string.module_upgrade, m.upgradeCost));
                upgrade.setAlpha(m.affordable ? 1f : 0.5f);
                upgrade.setOnClickListener(v -> viewModel.upgradeModule(m.id));
                b.modules.addView(upgrade);
            }
        }

        b.gear.removeAllViews();
        for (SessionSnapshot.GearView piece : base.gear) {
            TextView line = (TextView) inflater.inflate(R.layout.item_log_line, b.gear, false);
            line.setText(piece.holder != null
                    ? res.getString(R.string.gear_line_worn_by, piece.label, piece.holder)
                    : piece.label);
            b.gear.addView(line);
            if (piece.repairCost != null) {
                TextView repair = (TextView) inflater.inflate(R.layout.item_decision_option, b.gear, false);
                repair.setText(res.getString(R.string.gear_repair, piece.repairCost));
                repair.setAlpha(piece.affordable ? 1f : 0.5f);
                repair.setOnClickListener(v -> viewModel.repairGear(piece.id));
                b.gear.addView(repair);
            }
        }
        if (base.gear.isEmpty()) {
            TextView none = (TextView) inflater.inflate(R.layout.item_log_line, b.gear, false);
            none.setText(R.string.gear_empty);
            b.gear.addView(none);
        }

        String guards = base.guards.isEmpty() ? res.getString(R.string.nobody_on_guard) : String.join(", ", base.guards);
        b.defense.setText(res.getString(R.string.defense_line, guards, base.defense, base.scouts, base.bunks));
    }

    private static TextView stepper(LayoutInflater inflater, ViewGroup parent, String text, boolean enabled,
                                    View.OnClickListener click, String description) {
        TextView button = (TextView) inflater.inflate(R.layout.item_decision_option, parent, false);
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        button.setText(text);
        button.setContentDescription(description);
        button.setAlpha(enabled ? 1f : 0.3f);
        button.setOnClickListener(enabled ? click : null);
        return button;
    }

    /** ■ powered, ▣ asked for but cut, □ unused capacity. */
    private static String bar(int effective, int requested, int max) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < max; i++) {
            out.append(i < effective ? '■' : i < requested ? '▣' : '□');
        }
        return out.toString();
    }
}
