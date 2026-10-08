package com.estatica.ui.terminal;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.estatica.R;
import com.estatica.databinding.ItemLogEntryBinding;
import com.estatica.domain.log.LogLine;

import java.util.List;

/** Radio log. Stable ids = seq; the typing line is updated through a payload. */
final class LogAdapter extends ListAdapter<LogLine, LogAdapter.Holder> {

    private static final Object PAYLOAD_TYPING = new Object();
    private static final String CURSOR = "_";

    private long typingSeq = TerminalUiState.NO_TYPING;
    private int typedChars;
    private Highlighter highlighter = Highlighter.NONE;
    /** Lines the player tapped to see when they happened. */
    private final java.util.Set<Long> showingTime = new java.util.HashSet<>();

    /** Long-press on a line. */
    interface LineListener {
        void onLongPress(LogLine line);
    }

    private LineListener lineListener;

    void setLineListener(LineListener listener) {
        this.lineListener = listener;
    }

    /** New vocabulary (someone joined, a place was found): redraw with it. */
    @SuppressLint("NotifyDataSetChanged")
    void setHighlighter(Highlighter highlighter) {
        this.highlighter = highlighter;
        notifyDataSetChanged();
    }

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
        ItemLogEntryBinding binding = ItemLogEntryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        Holder holder = new Holder(binding);
        binding.text.setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                long seq = getItem(position).getSeq();
                if (!showingTime.remove(seq)) {
                    showingTime.add(seq);
                }
                notifyItemChanged(position, PAYLOAD_TYPING);
            }
        });
        binding.text.setOnLongClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (lineListener != null && position != RecyclerView.NO_POSITION) {
                lineListener.onLongPress(getItem(position));
                return true;
            }
            return false;
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LogLine line = getItem(position);
        LogLine previous = position > 0 ? getItem(position - 1) : null;
        holder.bind(line, typingSeq, typedChars, highlighter, showingTime.contains(line.getSeq()),
                previous == null || !sameDay(previous.getTimestampMillis(), line.getTimestampMillis()));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        // Full and payload binds are equally cheap (one setText); the payload only avoids
        // a change animation.
        onBindViewHolder(holder, position);
    }

    private static boolean sameDay(long a, long b) {
        java.util.Calendar x = java.util.Calendar.getInstance();
        x.setTimeInMillis(a);
        java.util.Calendar y = java.util.Calendar.getInstance();
        y.setTimeInMillis(b);
        return x.get(java.util.Calendar.YEAR) == y.get(java.util.Calendar.YEAR)
                && x.get(java.util.Calendar.DAY_OF_YEAR) == y.get(java.util.Calendar.DAY_OF_YEAR);
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

        private final ItemLogEntryBinding binding;
        private final int colorDefault;
        private final int colorOperator;
        private final int colorAlert;
        private final int colorBright;
        private final int colorBackground;
        private final String walkie;
        private final String alertIcon;

        Holder(ItemLogEntryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            android.content.Context c = binding.getRoot().getContext();
            colorDefault = ContextCompat.getColor(c, R.color.amber);
            colorOperator = ContextCompat.getColor(c, R.color.amber_dim);
            colorAlert = ContextCompat.getColor(c, R.color.amber_bright);
            colorBright = ContextCompat.getColor(c, R.color.amber_bright);
            colorBackground = ContextCompat.getColor(c, R.color.terminal_bg);
            walkie = c.getString(R.string.ic_walkie);
            alertIcon = c.getString(R.string.ic_alert);
        }

        void bind(LogLine line, long typingSeq, int typedChars, Highlighter highlighter, boolean withTime,
                  boolean newDay) {
            android.content.Context c = binding.getRoot().getContext();
            binding.day.setVisibility(newDay ? android.view.View.VISIBLE : android.view.View.GONE);
            if (newDay) {
                String day = new java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.getDefault())
                        .format(new java.util.Date(line.getTimestampMillis()));
                binding.day.setText(c.getString(R.string.log_day, day));
            }
            String text = line.getText();
            String shown = line.getSeq() == typingSeq
                    ? text.substring(0, Math.min(typedChars, text.length())) + CURSOR
                    : text;
            SpannableStringBuilder out = new SpannableStringBuilder();
            prefix(line, out);
            int body = out.length();
            out.append(shown);
            highlighter.apply(out, body);
            if (withTime) {
                int start = out.length();
                out.append("  ").append(android.text.format.DateFormat.getTimeFormat(c)
                        .format(new java.util.Date(line.getTimestampMillis())));
                out.setSpan(new ForegroundColorSpan(colorOperator), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.text.setText(out);
            binding.text.setTextColor(colorFor(line));
        }

        /** Who's talking, with a glyph: › system, walkie + name, inverse ⚠ for alerts, ❯ for you. */
        private void prefix(LogLine line, SpannableStringBuilder out) {
            switch (line.getChannel()) {
                case OPERATOR:
                    span(out, "❯ ", new ForegroundColorSpan(colorOperator));
                    break;
                case RADIO:
                    span(out, walkie + " ", new ForegroundColorSpan(colorOperator));
                    if (line.getSpeaker() != null) {
                        span(out, line.getSpeaker(), new StyleSpan(Typeface.BOLD), new ForegroundColorSpan(colorBright));
                        out.append(": ");
                    }
                    break;
                case ALERT:
                    span(out, " " + alertIcon + " ", new BackgroundColorSpan(colorAlert),
                            new ForegroundColorSpan(colorBackground), new StyleSpan(Typeface.BOLD));
                    out.append(' ');
                    break;
                default:
                    span(out, "› ", new ForegroundColorSpan(colorOperator));
                    break;
            }
        }

        private static void span(SpannableStringBuilder out, String text, Object... spans) {
            int start = out.length();
            out.append(text);
            for (Object span : spans) {
                out.setSpan(span, start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
