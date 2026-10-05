package com.estatica.ui.scouts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.estatica.databinding.ItemPickerBinding;

/** A list of choices (zones, gifts, tasks) or read-only entries (Diary). */
public final class PickerAdapter extends ListAdapter<PickerAdapter.Row, PickerAdapter.Holder> {

    public static final class Row {
        /** Passed back on click; may be null (e.g. "no task"). */
        public final String key;
        public final String title;
        public final String meta;
        public final String description;
        public final boolean clickable;

        public Row(String key, String title, String meta, String description, boolean clickable) {
            this.key = key;
            this.title = title;
            this.meta = meta;
            this.description = description;
            this.clickable = clickable;
        }
    }

    public interface Listener {
        void onPick(Row row);
    }

    private final Listener listener;

    public PickerAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPickerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = getItem(position);
        ItemPickerBinding b = holder.binding;
        b.title.setText(row.title);
        bindOptional(b.meta, row.meta);
        bindOptional(b.description, row.description);
        b.getRoot().setClickable(row.clickable);
        b.getRoot().setFocusable(row.clickable);
        b.getRoot().setOnClickListener(row.clickable ? v -> listener.onPick(row) : null);
    }

    private static void bindOptional(android.widget.TextView view, String text) {
        view.setText(text);
        view.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemPickerBinding binding;

        Holder(ItemPickerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            return java.util.Objects.equals(a.key, b.key) && a.title.equals(b.title);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            // Rows are rebuilt with every snapshot; a handful of them: always rebind.
            return false;
        }
    };
}
