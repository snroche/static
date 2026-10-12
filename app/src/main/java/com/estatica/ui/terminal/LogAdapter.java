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
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;

import java.util.List;

/** Radio log. Stable ids = seq; the typing line is updated through a payload. */
final class LogAdapter extends ListAdapter<LogLine, LogAdapter.Holder> {

    private static final Object PAYLOAD_TYPING = new Object();
    private static final String CURSOR = "_";

    private long typingSeq = TerminalUiState.NO_TYPING;
    private int typedChars;
    private Highlighter highlighter = Highlighter.NONE;
    /** Lines the player tapped to see the full date. */
    private final java.util.Set<Long> showingTime = new java.util.HashSet<>();
    /** Draw a line between lines of different threads (EXPEDITIONS → ALL). */
    private boolean separateThreads;
    /** Expeditions still waiting for orders: their latest call stays lit, older ones go dim. */
    private java.util.Set<String> awaiting = java.util.Set.of();
    /** Per thread, the newest alert line. */
    private final java.util.Map<String, Long> latestAlert = new java.util.HashMap<>();

    @SuppressLint("NotifyDataSetChanged")
    void setSeparateThreads(boolean separate) {
        if (separate != separateThreads) {
            separateThreads = separate;
            notifyDataSetChanged();
        }
    }

    /** @param expeditionIds expeditions waiting for orders right now */
    @SuppressLint("NotifyDataSetChanged")
    void setAwaiting(java.util.Set<String> expeditionIds) {
        if (!expeditionIds.equals(awaiting)) {
            awaiting = java.util.Set.copyOf(expeditionIds);
            notifyDataSetChanged();
        }
    }

    @Override
    public void onCurrentListChanged(@NonNull List<LogLine> previous, @NonNull List<LogLine> current) {
        latestAlert.clear();
        for (LogLine line : current) {
            if (line.getChannel() == LogChannel.ALERT) {
                latestAlert.merge(line.getThread(), line.getSeq(), Math::max);
            }
        }
    }

    /** An alert about an expedition that already got its orders: shown switched off. */
    private boolean answered(LogLine line) {
        String thread = line.getThread();
        if (line.getChannel() != LogChannel.ALERT || !thread.startsWith(LogLine.EXPEDITION_PREFIX)) {
            return false;
        }
        String id = thread.substring(LogLine.EXPEDITION_PREFIX.length());
        if (id.endsWith(LogLine.SUMMARY_SUFFIX)) {
            id = id.substring(0, id.length() - LogLine.SUMMARY_SUFFIX.length());
        }
        return !awaiting.contains(id) || latestAlert.getOrDefault(thread, -1L) != line.getSeq();
    }

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
                previous == null || !sameDay(previous.getTimestampMillis(), line.getTimestampMillis()),
                separateThreads && previous != null && !previous.getThread().equals(line.getThread()),
                answered(line));
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
        private final int colorRadio;
        private final int colorChat;
        private final int colorStatic;
        private final int[] voices;
        private final String walkie;
        private final String alertIcon;
        private final int colorHint;
        private final int colorDim;

        Holder(ItemLogEntryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            android.content.Context c = binding.getRoot().getContext();
            colorDefault = ContextCompat.getColor(c, R.color.amber);
            colorOperator = ContextCompat.getColor(c, R.color.operator_text);
            colorRadio = ContextCompat.getColor(c, R.color.radio_voice);
            colorChat = ContextCompat.getColor(c, R.color.chat_voice);
            colorStatic = ContextCompat.getColor(c, R.color.static_noise);
            int[] ids = {R.color.voice_1, R.color.voice_2, R.color.voice_3, R.color.voice_4, R.color.voice_5, R.color.voice_6};
            voices = new int[ids.length];
            for (int i = 0; i < ids.length; i++) {
                voices[i] = ContextCompat.getColor(c, ids[i]);
            }
            colorAlert = ContextCompat.getColor(c, R.color.amber_bright);
            colorBright = ContextCompat.getColor(c, R.color.amber_bright);
            colorBackground = ContextCompat.getColor(c, R.color.terminal_bg);
            walkie = c.getString(R.string.ic_walkie);
            alertIcon = c.getString(R.string.ic_alert);
            colorHint = ContextCompat.getColor(c, R.color.amber_hint);
            colorDim = ContextCompat.getColor(c, R.color.amber_dim);
        }

        void bind(LogLine line, long typingSeq, int typedChars, Highlighter highlighter, boolean withDate,
                  boolean newDay, boolean newThread, boolean answered) {
            android.content.Context c = binding.getRoot().getContext();
            binding.threadDivider.setVisibility(newThread && !newDay ? android.view.View.VISIBLE : android.view.View.GONE);
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
            // A small, dim time on every line: when it happened, at a glance.
            span(out, android.text.format.DateFormat.getTimeFormat(c).format(new java.util.Date(line.getTimestampMillis())) + " ",
                    new ForegroundColorSpan(colorHint), new android.text.style.RelativeSizeSpan(0.75f));
            prefix(line, out, answered);
            int body = out.length();
            out.append(shown);
            highlighter.apply(out, body);
            if (line.getChannel() == LogChannel.RADIO) {
                dimStatic(out, body);
            }
            if (withDate) {
                int start = out.length();
                out.append("  ").append(android.text.format.DateFormat.getMediumDateFormat(c)
                        .format(new java.util.Date(line.getTimestampMillis())));
                out.setSpan(new ForegroundColorSpan(colorOperator), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.text.setText(out);
            binding.text.setTextColor(answered ? colorDim : colorFor(line));
        }

        /** Who's talking, with a glyph: › system, walkie + name, inverse ⚠ for alerts, ❯ for you. */
        private void prefix(LogLine line, SpannableStringBuilder out, boolean answered) {
            switch (line.getChannel()) {
                case OPERATOR:
                    span(out, "❯ ", new ForegroundColorSpan(colorOperator));
                    break;
                case RADIO:
                    span(out, walkie + " ", new ForegroundColorSpan(colorOperator));
                    if (line.getSpeaker() != null) {
                        span(out, line.getSpeaker(), new StyleSpan(Typeface.BOLD),
                                new ForegroundColorSpan(voiceOf(line.getSpeaker())));
                        out.append(": ");
                    }
                    break;
                case ALERT:
                    if (answered) {
                        // Orders given: the same mark, switched off.
                        span(out, " " + alertIcon + " ", new ForegroundColorSpan(colorHint));
                    } else {
                        span(out, " " + alertIcon + " ", new BackgroundColorSpan(colorAlert),
                                new ForegroundColorSpan(colorBackground), new StyleSpan(Typeface.BOLD));
                    }
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
                case RADIO:
                    return line.getThread().startsWith(LogLine.CHAT_PREFIX) ? colorChat : colorRadio;
                default:
                    return colorDefault;
            }
        }

        /** Each person keeps the same name color. */
        private int voiceOf(String speaker) {
            return voices[Math.floorMod(speaker.hashCode(), voices.length)];
        }

        /** Dots and dashes where the signal dropped, in a dim burnt amber: noise, not words. */
        private void dimStatic(SpannableStringBuilder out, int from) {
            int i = from;
            while (i < out.length()) {
                char ch = out.charAt(i);
                if (ch != '·' && ch != '-') {
                    i++;
                    continue;
                }
                int end = i;
                boolean dot = false;
                while (end < out.length() && (out.charAt(end) == '·' || out.charAt(end) == '-')) {
                    dot |= out.charAt(end) == '·';
                    end++;
                }
                // A lone dash between spaces is punctuation; anything with a dot, or inside a word, is static.
                boolean inWord = (i > from && Character.isLetterOrDigit(out.charAt(i - 1)))
                        || (end < out.length() && Character.isLetterOrDigit(out.charAt(end)));
                if (dot || inWord) {
                    out.setSpan(new ForegroundColorSpan(colorStatic), i, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                i = end;
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
