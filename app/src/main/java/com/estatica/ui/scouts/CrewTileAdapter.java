package com.estatica.ui.scouts;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot.ScoutView;
import com.estatica.databinding.ItemCrewTileBinding;

/**
 * The crew as a grid of tiles. The frame says where they are (solid: in the bunker, dashed:
 * out on a mission); under the name, what they're doing. A tap opens the full card.
 */
public final class CrewTileAdapter extends ListAdapter<ScoutView, CrewTileAdapter.Holder> {

    public interface Listener {
        void onOpen(ScoutView scout);
    }

    private final Listener listener;

    public CrewTileAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemCrewTileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ScoutView s = getItem(position);
        holder.bind(s);
        holder.b.getRoot().setOnClickListener(v -> listener.onOpen(s));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemCrewTileBinding b;

        Holder(ItemCrewTileBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(ScoutView s) {
            Resources res = b.getRoot().getResources();
            boolean away = s.awayAt != null;
            b.getRoot().setBackgroundResource(away ? R.drawable.bg_tile_away : R.drawable.bg_tile_home);
            b.tileName.setText(s.name);
            b.tileDoing.setText(away ? res.getString(R.string.tile_away, s.awayAt) : s.task);
            b.tileDoing.setTextColor(ContextCompat.getColor(b.getRoot().getContext(),
                    away ? R.color.signal : R.color.amber));
            StringBuilder state = new StringBuilder(res.getString(R.string.tile_state,
                    bar(Math.round(s.stress / 20f), 5), bar(s.bondLevel, s.bondLevels)));
            if (s.hurt) {
                state.append(' ').append(res.getString(R.string.tile_hurt));
            }
            if (s.canTalk) {
                state.append(' ').append(res.getString(R.string.tile_talk));
            }
            b.tileState.setText(state);
            b.getRoot().setContentDescription(res.getString(R.string.tile_description, s.name,
                    b.tileDoing.getText(), s.mood != null ? s.mood : ""));
        }

        private static String bar(int value, int max) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < max; i++) {
                out.append(i < value ? '■' : '□');
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
            return false; // tiny list, snapshots rebuilt on every change
        }
    };
}
