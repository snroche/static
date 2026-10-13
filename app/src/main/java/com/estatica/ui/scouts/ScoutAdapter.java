package com.estatica.ui.scouts;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot.ScoutView;
import com.estatica.data.session.SessionSnapshot.SlotView;
import com.estatica.data.session.SessionSnapshot.StatView;
import com.estatica.databinding.ItemGearSlotBinding;
import com.estatica.databinding.ItemScoutBinding;

/**
 * One person's page: name and what they're doing, then four tabs (stats, file, gear,
 * orders). The open tab is kept across snapshot rebinds and between people.
 */
public final class ScoutAdapter extends ListAdapter<ScoutView, ScoutAdapter.Holder> {

    public interface Listener {
        void onDispatch(ScoutView scout);

        void onTalk(ScoutView scout);

        void onGift(ScoutView scout);

        void onTask(ScoutView scout);

        /** A gear slot was tapped: show what fits in it. */
        void onSlot(ScoutView scout, String slot);

        /** Call them home from the field. */
        default void onRecall(ScoutView scout) {
        }

        /** Long-press on the page: explain what's on it. */
        default void onInfo(CharSequence cardText) {
        }
    }

    public enum Page { STATS, FILE, GEAR, ORDERS }

    private static final char FILLED = '■';
    private static final char EMPTY = '□';

    private final Listener listener;
    private Page page = Page.STATS;

    public ScoutAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** Opens on a given tab (e.g. ORDERS from a shortcut). */
    public void setPage(Page page) {
        this.page = page;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemScoutBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ScoutView scout = getItem(position);
        holder.bind(scout, listener, page);
        TextView[] tabs = {holder.b.tabStats, holder.b.tabFile, holder.b.tabGear, holder.b.tabOrders};
        for (int i = 0; i < tabs.length; i++) {
            Page target = Page.values()[i];
            tabs[i].setOnClickListener(v -> {
                page = target;
                int at = holder.getBindingAdapterPosition();
                if (at != RecyclerView.NO_POSITION) {
                    notifyItemChanged(at);
                }
            });
        }
        View.OnLongClickListener explain = v -> {
            listener.onInfo(holder.pageText());
            return true;
        };
        holder.b.pageStats.setOnLongClickListener(explain);
        holder.b.pageFile.setOnLongClickListener(explain);
        holder.b.summary.setOnLongClickListener(explain);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemScoutBinding b;

        Holder(ItemScoutBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        /** Everything the open tab says, for the info sheet. */
        CharSequence pageText() {
            StringBuilder out = new StringBuilder();
            appendTexts(out, b.summary);
            appendTexts(out, b.pageStats.getVisibility() == View.VISIBLE ? b.pageStats : b.pageFile);
            return out;
        }

        private static void appendTexts(StringBuilder out, View view) {
            if (view instanceof TextView) {
                TextView t = (TextView) view;
                if (t.getVisibility() == View.VISIBLE && t.getText() != null && t.getText().length() > 0) {
                    out.append(t.getText()).append('\n');
                }
            } else if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    appendTexts(out, group.getChildAt(i));
                }
            }
        }

        void bind(ScoutView s, Listener listener, Page page) {
            Resources res = b.getRoot().getResources();
            b.name.setText(res.getString(R.string.tile_name, s.name, s.female ? "♀" : "♂"));
            b.doing.setText(s.dead ? s.status
                    : s.awayAt != null ? res.getString(R.string.person_away, s.status, s.awayAt)
                    : res.getString(R.string.person_task, s.task));
            b.doing.setTextColor(ContextCompat.getColor(b.getRoot().getContext(),
                    s.awayAt != null ? R.color.signal : R.color.amber));
            optional(b.summary, s.dead || s.mood == null ? null : res.getString(R.string.scout_mood, s.mood));

            TextView[] tabs = {b.tabStats, b.tabFile, b.tabGear, b.tabOrders};
            View[] pages = {b.pageStats, b.pageFile, b.pageGear, b.pageOrders};
            for (int i = 0; i < tabs.length; i++) {
                boolean on = page.ordinal() == i;
                tabs[i].setSelected(on);
                tabs[i].setTextColor(ContextCompat.getColor(b.getRoot().getContext(),
                        on ? R.color.amber_bright : R.color.amber_dim));
                pages[i].setVisibility(on ? View.VISIBLE : View.GONE);
            }
            switch (page) {
                case STATS:
                    bindStats(s, res);
                    break;
                case FILE:
                    bindFile(s, res);
                    break;
                case GEAR:
                    bindGear(s, res, listener);
                    break;
                default:
                    bindOrders(s, res, listener);
                    break;
            }
            b.getRoot().setAlpha(s.dead ? 0.5f : 1f);
        }

        private void bindStats(ScoutView s, Resources res) {
            String[] moods = {"content", "steady", "tense", "breaking"};
            int[] notes = {R.string.person_stress_content, R.string.person_stress_steady,
                    R.string.person_stress_tense, R.string.person_stress_breaking};
            int note = R.string.person_stress_steady;
            for (int i = 0; i < moods.length; i++) {
                if (moods[i].equals(s.moodKey)) {
                    note = notes[i];
                }
            }
            b.stressLabel.setText(res.getString(R.string.person_stress, s.stress));
            b.stressBar.set(s.stress / 100f, 1, s.stress >= 60 ? R.color.danger : R.color.amber);
            b.stressNote.setText(note);

            boolean maxed = s.bondLevel >= s.bondLevels;
            b.bondLabel.setText(res.getString(R.string.person_bond, s.bondLevel, s.bondLevels));
            b.bondBar.set(maxed ? 1f : (s.bondLevel + s.bondProgress / 100f) / s.bondLevels, s.bondLevels,
                    R.color.amber_bright);
            b.bondNote.setText(maxed ? res.getString(R.string.person_bond_max)
                    : res.getString(R.string.person_bond_next, s.bondProgress, s.bondLevel + 1));

            b.trustLabel.setText(res.getString(R.string.person_trust, s.trust));
            b.trustBar.set(s.trust / 100f, 1, s.trust < 30 ? R.color.danger : R.color.signal);
            optional(b.injuryLine, s.hurt ? res.getString(R.string.person_hurt, s.injury) : null);
            b.stats.setText(statBlock(s));
            optional(b.traits, s.traits.isEmpty() ? null
                    : res.getString(R.string.scout_traits, String.join(", ", s.traits)));
        }

        private void bindFile(ScoutView s, Resources res) {
            optional(b.romance, s.romance);
            optional(b.likes, s.likes);
            optional(b.dislikes, s.dislikes);
            optional(b.habits, s.habits.isEmpty() ? null
                    : res.getString(R.string.person_noticed, "· " + String.join("\n· ", s.habits)));
            optional(b.relations, s.relations.isEmpty() ? null : String.join("\n", s.relations));
            optional(b.gifts, s.gifts.isEmpty() ? null
                    : res.getString(R.string.scout_gifts, String.join(", ", s.gifts)));
            boolean nothing = s.likes == null && s.dislikes == null && s.habits.isEmpty();
            optional(b.bio, nothing && s.bio == null ? res.getString(R.string.person_file_empty) : s.bio);
        }

        /** Slots two to a row, each an outlined box; tap one to change what's in it. */
        private void bindGear(ScoutView s, Resources res, Listener listener) {
            b.pageGear.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(b.getRoot().getContext());
            LinearLayout row = null;
            for (int i = 0; i < s.slots.size(); i++) {
                if (i % 2 == 0) {
                    row = new LinearLayout(b.getRoot().getContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setBaselineAligned(false);
                    b.pageGear.addView(row);
                }
                SlotView slot = s.slots.get(i);
                ItemGearSlotBinding box = ItemGearSlotBinding.inflate(inflater, row, false);
                boolean empty = slot.pieceId == null;
                box.getRoot().setBackgroundResource(empty ? R.drawable.bg_slot_empty : R.drawable.bg_tile_home);
                box.slotIcon.setText(slotIcon(slot.slot));
                box.slotIcon.setTextColor(ContextCompat.getColor(b.getRoot().getContext(),
                        empty ? R.color.amber_hint : R.color.amber_bright));
                box.slotName.setText(slot.name);
                box.slotPiece.setText(empty ? res.getString(R.string.person_slot_empty) : slot.label);
                box.slotPiece.setTextColor(ContextCompat.getColor(b.getRoot().getContext(),
                        empty ? R.color.amber_dim : R.color.amber));
                box.getRoot().setContentDescription(slot.name + ": " + box.slotPiece.getText());
                if (s.canEquip) {
                    box.getRoot().setOnClickListener(v -> listener.onSlot(s, slot.slot));
                } else {
                    box.getRoot().setClickable(false);
                }
                row.addView(box.getRoot());
            }
            if (row != null && s.slots.size() % 2 == 1) {
                // Keep the last box half-width.
                View spacer = new View(b.getRoot().getContext());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                row.addView(spacer);
            }
            TextView note = (TextView) inflater.inflate(R.layout.item_log_line, b.pageGear, false);
            note.setText(s.canEquip ? R.string.person_gear_hint : R.string.person_gear_away);
            note.setTextColor(ContextCompat.getColor(b.getRoot().getContext(), R.color.amber_dim));
            b.pageGear.addView(note);
        }

        private void bindOrders(ScoutView s, Resources res, Listener listener) {
            action(b.dispatch, s.canDispatch, res.getString(R.string.person_dispatch), v -> listener.onDispatch(s));
            action(b.recallButton, s.canRecall, res.getString(R.string.person_recall), v -> listener.onRecall(s));
            // Not in the mood yet: still shown, dimmed, with when they will be.
            boolean later = !s.canTalk && s.talkReadyAtMillis > 0;
            String talk = later ? res.getString(R.string.person_talk_later,
                    android.text.format.DateFormat.getTimeFormat(b.getRoot().getContext())
                            .format(new java.util.Date(s.talkReadyAtMillis)))
                    : res.getString(R.string.person_talk);
            action(b.talk, s.canTalk || later, talk, v -> listener.onTalk(s));
            b.talk.setAlpha(later ? 0.5f : 1f);
            action(b.taskButton, s.canAssignTask, res.getString(R.string.person_set_task, s.task),
                    v -> listener.onTask(s));
            action(b.gift, s.canGift, res.getString(R.string.person_gift), v -> listener.onGift(s));
            String note = s.dead ? null
                    : s.awayAt != null ? res.getString(R.string.person_orders_away)
                    : !s.canDispatch ? res.getString(R.string.person_orders_resting) : null;
            optional(b.ordersNote, note);
        }

        private static int slotIcon(String slot) {
            switch (slot) {
                case "HEAD":
                    return R.string.ic_head;
                case "NECK":
                    return R.string.ic_neck;
                case "HANDS":
                    return R.string.ic_hands;
                case "BELT":
                    return R.string.ic_gear;
                case "BACK":
                    return R.string.ic_back;
                case "FEET":
                    return R.string.ic_feet;
                default:
                    return R.string.ic_body;
            }
        }

        private static void optional(TextView view, String text) {
            view.setText(text);
            view.setVisibility(text == null ? View.GONE : View.VISIBLE);
        }

        private static void action(TextView button, boolean enabled, String text, View.OnClickListener click) {
            button.setText(text);
            button.setVisibility(enabled ? View.VISIBLE : View.GONE);
            button.setOnClickListener(enabled ? click : null);
        }

        /** "Endurance ■■■■■■□□□ 6/9", one line per stat, names padded to align. */
        private static String statBlock(ScoutView s) {
            int width = 0;
            for (StatView stat : s.stats) {
                width = Math.max(width, stat.name.length());
            }
            StringBuilder out = new StringBuilder();
            for (StatView stat : s.stats) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(stat.name);
                for (int i = stat.name.length(); i <= width; i++) {
                    out.append(' ');
                }
                for (int i = 0; i < stat.ceiling; i++) {
                    out.append(i < stat.value ? FILLED : EMPTY);
                }
                out.append(' ').append(stat.value).append('/').append(stat.ceiling);
            }
            return out.toString();
        }
    }

    private static final DiffUtil.ItemCallback<ScoutView> DIFF = new DiffUtil.ItemCallback<ScoutView>() {
        @Override
        public boolean areItemsTheSame(@NonNull ScoutView a, @NonNull ScoutView b) {
            return a.id.equals(b.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull ScoutView a, @NonNull ScoutView b) {
            // Snapshots are rebuilt on every change and the list is tiny: always rebind.
            return false;
        }
    };
}
