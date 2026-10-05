package com.estatica.ui.terminal;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.estatica.R;
import com.estatica.databinding.ItemLogLineBinding;
import com.estatica.domain.log.LogLine;

import java.util.List;

/** Radio log. Stable ids = seq; the typing line is updated through a payload. */
final class LogAdapter extends ListAdapter<LogLine, LogAdapter.Holder> {

    private static final Object PAYLOAD_TYPING = new Object();
    private static final String CURSOR = "_";

    private long typingSeq = TerminalUiState.NO_TYPING;
    private int typedChars;

    LogAdapter() {
        super(DIFF);
        setHasStableIds(true);
    }

    void startTyping(long seq) {
        typingSeq = seq;
        typedChars = 0;
    }

    void updateTyping(int chars) {
        typedChars = chars;
        notifyTypingLine();
    }

    void finishTyping() {
        int position = positionOf(typingSeq);
        typingSeq = TerminalUiState.NO_TYPING;
        if (position >= 0) {
            notifyItemChanged(position, PAYLOAD_TYPING);
        }
    }

    long getTypingSeq() {
        return typingSeq;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getSeq();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLogLineBinding binding = ItemLogLineBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position), typingSeq, typedChars);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        // Full and payload binds are equally cheap (one setText); the payload only avoids
        // a change animation.
        onBindViewHolder(holder, position);
    }

    private void notifyTypingLine() {
        int position = positionOf(typingSeq);
        if (position >= 0) {
            notifyItemChanged(position, PAYLOAD_TYPING);
        }
    }

    /** The typing line is almost always the last one, so search backwards. */
    int positionOf(long seq) {
        if (seq == TerminalUiState.NO_TYPING) {
            return RecyclerView.NO_POSITION;
        }
        List<LogLine> items = getCurrentList();
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).getSeq() == seq) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    static final class Holder extends RecyclerView.ViewHolder {

        private final ItemLogLineBinding binding;
        private final int colorDefault;
        private final int colorOperator;
        private final int colorAlert;

        Holder(ItemLogLineBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            colorDefault = ContextCompat.getColor(binding.getRoot().getContext(), R.color.amber);
            colorOperator = ContextCompat.getColor(binding.getRoot().getContext(), R.color.amber_dim);
            colorAlert = ContextCompat.getColor(binding.getRoot().getContext(), R.color.amber_bright);
        }

        // Prefixes are symbols or speaker names, not translatable sentences.
        @SuppressLint("SetTextI18n")
        void bind(LogLine line, long typingSeq, int typedChars) {
            String text = line.getText();
            String shown = line.getSeq() == typingSeq
                    ? text.substring(0, Math.min(typedChars, text.length())) + CURSOR
                    : text;
            binding.text.setText(prefix(line) + shown);
            binding.text.setTextColor(colorFor(line));
        }

        private static String prefix(LogLine line) {
            switch (line.getChannel()) {
                case OPERATOR:
                    return "> ";
                case RADIO:
                    return line.getSpeaker() != null ? line.getSpeaker() + ": " : "";
                case ALERT:
                    return "!! ";
                default:
                    return "";
            }
        }

        private int colorFor(LogLine line) {
            switch (line.getChannel()) {
                case OPERATOR:
                    return colorOperator;
                case ALERT:
                    return colorAlert;
                default:
                    return colorDefault;
            }
        }
    }

    private static final DiffUtil.ItemCallback<LogLine> DIFF = new DiffUtil.ItemCallback<LogLine>() {
        @Override
        public boolean areItemsTheSame(@NonNull LogLine a, @NonNull LogLine b) {
            return a.getSeq() == b.getSeq();
        }

        @Override
        public boolean areContentsTheSame(@NonNull LogLine a, @NonNull LogLine b) {
            return a.equals(b);
        }
    };
}
