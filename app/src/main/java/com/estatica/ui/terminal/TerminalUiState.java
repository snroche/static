package com.estatica.ui.terminal;

import com.estatica.domain.log.LogLine;

import java.util.Collections;
import java.util.List;

/** Immutable snapshot rendered by {@link TerminalActivity}. */
final class TerminalUiState {

    static final long NO_TYPING = -1L;

    final List<LogLine> lines;
    /** Seq of the line currently being typewritten, or {@link #NO_TYPING}. */
    final long typingSeq;
    final int typewriterCharMillis;
    final LogView view;
    /** Thread the view is narrowed to, or null. */
    final String focus;
    /** Lines not seen yet, per view (index = LogView ordinal). */
    final int[] unread;
    /** Lines not seen yet, per thread (for the chips). */
    final java.util.Map<String, Integer> unreadByThread;

    TerminalUiState(List<LogLine> lines, long typingSeq, int typewriterCharMillis, LogView view, String focus,
                    int[] unread, java.util.Map<String, Integer> unreadByThread) {
        this.unreadByThread = unreadByThread;
        this.lines = Collections.unmodifiableList(lines);
        this.typingSeq = typingSeq;
        this.typewriterCharMillis = typewriterCharMillis;
        this.view = view;
        this.focus = focus;
        this.unread = unread;
    }
}
