package com.estatica.ui.terminal;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot;
import com.estatica.databinding.PanelBunkerBinding;

/** The bunker panel: power steppers, reserve, workshop jobs, module upgrades, defense. */
final class BunkerPanel {

    interface Host {
        void closeBunker();

        void pickRecipe();

        /** Open the inventory screen. */
        default void openInventory() {
        }

        /** Long-press on a line: explain it. */
        default void explain(CharSequence text) {
        }
    }

    private enum Section { POWER, WORKSHOP, UPGRADES, GOALS }

    /** Piece whose SALVAGE was tapped once (the second tap does it). */
    private String salvageArmed;

    private Section section = Section.UPGRADES;
    /** Room picked on the floor plan. */
    private String room = "radio_room";
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
        b.fuelGenerator.setOnClickListener(v -> viewModel.toggleFuelGenerator());
        b.craft.setOnClickListener(v -> host.pickRecipe());
        b.inventoryButton.setOnClickListener(v -> host.openInventory());
        b.tabPower.setOnClickListener(v -> showSection(Section.POWER));
        b.tabWorkshop.setOnClickListener(v -> showSection(Section.WORKSHOP));
        b.tabUpgrades.setOnClickListener(v -> showSection(Section.UPGRADES));
        b.tabGoals.setOnClickListener(v -> showSection(Section.GOALS));
        b.bunkerPlan.setListener(r -> {
            room = r.id;
            if (snapshot != null) {
                render(snapshot);
            }
        });
        b.roomRestore.setOnClickListener(v -> viewModel.startRestore(room));
    }

    private void showSection(Section target) {
        section = target;
        if (snapshot != null) {
            render(snapshot);
        }
    }

    /** "[•POWER !]": the open section is lit; one that needs you says so, one with news gets "↑". */
    private void sectionButton(TextView view, Section which, int label, boolean alert, boolean news) {
        Resources res = view.getResources();
        String text = res.getString(label) + (alert ? " !" : news ? " ↑" : "");
        boolean on = section == which;
        view.setText(text);
        view.setTextColor(androidx.core.content.ContextCompat.getColor(view.getContext(),
                alert ? R.color.danger : on ? R.color.amber_bright : R.color.amber_dim));
        BracketDrawable.mark(view, on);
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

        boolean powerAlert = base.blackout || base.filtersShort || base.powerCut;
        boolean anyAffordable = false;
        for (SessionSnapshot.ModuleView m : base.modules) {
            anyAffordable |= m.upgradeCost != null && m.affordable;
        }
        sectionButton(b.tabPower, Section.POWER, R.string.energy_title, powerAlert, false);
        sectionButton(b.tabWorkshop, Section.WORKSHOP, R.string.workshop_title, false, base.jobs.size() < base.slots);
        sectionButton(b.tabUpgrades, Section.UPGRADES, R.string.rooms_title, false, anyAffordable);
        sectionButton(b.tabGoals, Section.GOALS, R.string.goals_title, false, false);
        b.sectionPower.setVisibility(section == Section.POWER ? View.VISIBLE : View.GONE);
        b.sectionWorkshop.setVisibility(section == Section.WORKSHOP ? View.VISIBLE : View.GONE);
        b.sectionUpgrades.setVisibility(section == Section.UPGRADES ? View.VISIBLE : View.GONE);
        b.goals.setVisibility(section == Section.GOALS ? View.VISIBLE : View.GONE);

        StringBuilder goals = new StringBuilder(res.getString(R.string.goals_help));
        boolean weeklyShown = false;
        goals.append("\n\n").append(res.getString(R.string.goals_daily));
        for (SessionSnapshot.GoalView g : base.goals) {
            if (g.weekly && !weeklyShown) {
                weeklyShown = true;
                goals.append("\n\n").append(res.getString(R.string.goals_weekly));
            }
            String check = g.progress >= g.target ? "[x]" : "[ ]";
            goals.append('\n').append(res.getString(R.string.goal_line, check, g.label, g.progress, g.target, g.reward));
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
        b.fuelGenerator.setText(res.getString(R.string.fuel_generator, base.fuelGenerator ? "x" : " ", base.fuel));

        b.bench.setText(base.crafter != null
                ? res.getString(R.string.bench_line, base.crafter, base.crafterCraft, base.blueprintsKnown, base.blueprintsTotal)
                : res.getString(R.string.bench_empty, base.blueprintsKnown, base.blueprintsTotal));
        b.bench.setTextColor(ContextCompat.getColor(b.getRoot().getContext(),
                base.crafter != null ? R.color.amber : R.color.danger));
        b.jobs.removeAllViews();
        int at = 0;
        for (SessionSnapshot.JobView job : base.jobs) {
            if (at++ == base.slots) {
                TextView queued = (TextView) inflater.inflate(R.layout.item_log_line, b.jobs, false);
                queued.setText(R.string.job_queue);
                queued.setAlpha(0.7f);
                b.jobs.addView(queued);
            }
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
        b.craft.setText(res.getString(R.string.craft_slots, base.jobs.size(), base.capacity));
        b.craft.setAlpha(base.jobs.size() < base.capacity && base.crafter != null ? 1f : 0.5f);

        SessionSnapshot.RoomView picked = null;
        for (SessionSnapshot.RoomView r : base.rooms) {
            if (r.id.equals(room)) {
                picked = r;
            }
        }
        b.bunkerPlan.setRooms(base.rooms, room);
        renderRoom(res, picked);

        b.modules.removeAllViews();
        for (SessionSnapshot.ModuleView m : base.modules) {
            if (picked == null || !picked.open || !picked.moduleIds.contains(m.id)) {
                continue; // upgrades live in their room
            }
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
            if (piece.upgradeCost != null) {
                TextView upgrade = (TextView) inflater.inflate(R.layout.item_decision_option, b.gear, false);
                upgrade.setText(res.getString(R.string.gear_upgrade, piece.upgradeCost, piece.upgradeCraft));
                upgrade.setAlpha(piece.upgradeOk ? 1f : 0.5f);
                upgrade.setOnClickListener(v -> viewModel.upgradeGear(piece.id));
                b.gear.addView(upgrade);
            }
            if (piece.inStock && piece.salvage != null) {
                TextView salvage = (TextView) inflater.inflate(R.layout.item_decision_option, b.gear, false);
                salvage.setText(res.getString(piece.id.equals(salvageArmed) ? R.string.gear_salvage_confirm
                        : R.string.gear_salvage, piece.salvage));
                salvage.setOnLongClickListener(null);
                salvage.setOnClickListener(v -> {
                    // Two taps: the first only asks.
                    if (!piece.id.equals(salvageArmed)) {
                        salvageArmed = piece.id;
                        salvage.setText(res.getString(R.string.gear_salvage_confirm, piece.salvage));
                        return;
                    }
                    salvageArmed = null;
                    viewModel.salvage(piece.id);
                });
                b.gear.addView(salvage);
            }
        }
        if (base.gear.isEmpty()) {
            TextView none = (TextView) inflater.inflate(R.layout.item_log_line, b.gear, false);
            none.setText(R.string.gear_empty);
            b.gear.addView(none);
        }

        boolean entrance = picked != null && "entrance".equals(picked.id);
        b.defenseTitle.setVisibility(entrance ? View.VISIBLE : View.GONE);
        b.defense.setVisibility(entrance ? View.VISIBLE : View.GONE);
        String guards = base.guards.isEmpty() ? res.getString(R.string.nobody_on_guard) : String.join(", ", base.guards);
        b.defense.setText(res.getString(R.string.defense_line, guards, base.defense, base.scouts, base.bunks));
    }

    /** The picked room's sheet: what it is, who's in it, what it unlocks, or how to open it. */
    private void renderRoom(Resources res, SessionSnapshot.RoomView r) {
        if (r == null) {
            b.roomTitle.setText("");
            b.roomInfo.setText(R.string.rooms_hint);
            b.roomRestore.setVisibility(View.GONE);
            return;
        }
        b.roomTitle.setText(r.open ? r.name : res.getString(R.string.room_sealed_title, r.name));
        StringBuilder info = new StringBuilder(r.text != null ? r.text : "");
        if (r.open) {
            if (!r.working.isEmpty()) {
                info.append('\n').append(res.getString(R.string.room_working, String.join(", ", r.working)));
            }
            if (!r.tasks.isEmpty()) {
                info.append('\n').append(res.getString(R.string.room_tasks, String.join(", ", r.tasks)));
            }
        } else if (r.restoring) {
            info.append('\n').append(res.getString(R.string.room_restoring, Math.round(r.progress * 100), r.hours,
                    r.restorers));
        } else {
            if (!r.tasks.isEmpty()) {
                info.append('\n').append(res.getString(R.string.room_unlocks, String.join(", ", r.tasks)));
            }
            if (!r.needs.isEmpty()) {
                info.append('\n').append(res.getString(R.string.room_needs, String.join(", ", r.needs)));
            }
            info.append('\n').append(res.getString(R.string.room_cost, r.cost, r.hours));
        }
        b.roomInfo.setText(info);
        boolean showRestore = !r.open && !r.restoring;
        b.roomRestore.setVisibility(showRestore ? View.VISIBLE : View.GONE);
        b.roomRestore.setText(R.string.room_restore);
        b.roomRestore.setAlpha(r.canRestore ? 1f : 0.4f);
        b.roomRestore.setEnabled(r.canRestore);
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
