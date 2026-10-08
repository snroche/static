package com.estatica.ui.scouts;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot.ScoutView;
import com.estatica.data.session.SessionSnapshot.StatView;
import com.estatica.databinding.ItemScoutBinding;

import java.util.HashSet;
import java.util.Set;

/**
 * Roster: one card per scout. Collapsed, a card shows name, mood and mini bars; tapping the
 * header reveals stats, relations, habits and the rest. Actions are always visible.
 */
public final class ScoutAdapter extends ListAdapter<ScoutView, ScoutAdapter.Holder> {

    public interface Listener {
        void onDispatch(ScoutView scout);

        void onTalk(ScoutView scout);

        void onGift(ScoutView scout);

        void onTask(ScoutView scout);

        void onEquip(ScoutView scout);

        /** Call them home from the field. */
        default void onRecall(ScoutView scout) {
        }

        /** Long-press on a card: explain what's on it. */
        default void onInfo(CharSequence cardText) {
        }
    }

    private static final char FILLED = '■';
    private static final char EMPTY = '□';

    private final Listener listener;
    /** Cards the player opened; kept across snapshot rebinds. */
    private final Set<String> expanded = new HashSet<>();

    public ScoutAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemScoutBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ScoutView scout = getItem(position);
        holder.bind(scout, listener, expanded.contains(scout.id));
        View.OnLongClickListener explain = v -> {
            listener.onInfo(holder.cardText());
            return true;
        };
        holder.b.header.setOnLongClickListener(explain);
        holder.b.details.setOnLongClickListener(explain);
        holder.b.header.setOnClickListener(v -> {
            if (!expanded.remove(scout.id)) {
                expanded.add(scout.id);
            }
            int at = holder.getBindingAdapterPosition();
            if (at != RecyclerView.NO_POSITION) {
                notifyItemChanged(at);
            }
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemScoutBinding b;

        /** Everything the card says, for the info sheet. */
        CharSequence cardText() {
            StringBuilder out = new StringBuilder();
            for (TextView t : new TextView[]{b.summary, b.stats, b.mood, b.traits, b.task, b.gear, b.vitals, b.bond}) {
                if (t.getVisibility() == View.VISIBLE && t.getText() != null) {
                    out.append(t.getText()).append('\n');
                }
            }
            return out;
        }

        Holder(ItemScoutBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(ScoutView s, Listener listener, boolean open) {
            Resources res = b.getRoot().getResources();
            b.name.setText(res.getString(open ? R.string.card_name_open : R.string.card_name_closed, s.name));
            b.summary.setText(res.getString(R.string.scout_summary,
                    s.awayAt != null ? s.awayAt : s.status,
                    s.dead ? "—" : s.mood,
                    bar(Math.round(s.stress / 20f), 5),
                    bar(s.bondLevel, s.bondLevels)));
            b.header.setContentDescription(res.getString(R.string.card_expand_description, s.name));
            b.details.setVisibility(open ? View.VISIBLE : View.GONE);
            b.status.setText(s.awayAt != null
                    ? res.getString(R.string.scout_status_away, s.status, s.awayAt)
                    : s.status);
            b.stats.setText(statBlock(s));
            b.vitals.setText(res.getString(R.string.scout_vitals, s.stress, s.trust, s.injury));
            b.bond.setText(res.getString(R.string.scout_bond, bar(s.bondLevel, s.bondLevels)));
            optional(b.mood, s.dead || s.mood == null ? null : res.getString(R.string.scout_mood, s.mood));
            optional(b.habits, s.habits.isEmpty() ? null
                    : res.getString(R.string.scout_habits, String.join("; ", s.habits)));
            optional(b.task, s.dead ? null : res.getString(R.string.scout_task, s.task));
            optional(b.traits, s.traits.isEmpty() ? null
                    : res.getString(R.string.scout_traits, String.join(", ", s.traits)));
            optional(b.likes, s.likes);
            optional(b.dislikes, s.dislikes);
            optional(b.relations, s.relations.isEmpty() ? null : String.join("\n", s.relations));
            optional(b.gear, s.gear == null ? null : res.getString(R.string.scout_gear, s.gear));
            optional(b.gifts, s.gifts.isEmpty() ? null
                    : res.getString(R.string.scout_gifts, String.join(", ", s.gifts)));
            optional(b.bio, s.bio);

            action(b.dispatch, s.canDispatch, v -> listener.onDispatch(s));
            action(b.talk, s.canTalk, v -> listener.onTalk(s));
            action(b.gift, s.canGift, v -> listener.onGift(s));
            action(b.taskButton, s.canAssignTask, v -> listener.onTask(s));
            action(b.equipButton, s.canEquip, v -> listener.onEquip(s));
            action(b.recallButton, s.canRecall, v -> listener.onRecall(s));
            // The empty bunk stays on the roster, dimmed (GDD §5.5).
            b.getRoot().setAlpha(s.dead ? 0.5f : 1f);
        }

        private static void optional(TextView view, String text) {
            view.setText(text);
            view.setVisibility(text == null ? View.GONE : View.VISIBLE);
        }

        private static void action(TextView button, boolean enabled, View.OnClickListener click) {
            button.setVisibility(enabled ? View.VISIBLE : View.GONE);
            button.setOnClickListener(enabled ? click : null);
        }

        private static String bar(int value, int max) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < max; i++) {
                out.append(i < value ? FILLED : EMPTY);
            }
            return out.toString();
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
