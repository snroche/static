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

    TerminalUiState(List<LogLine> lines, long typingSeq, int typewriterCharMillis) {
        this.lines = Collections.unmodifiableList(lines);
        this.typingSeq = typingSeq;
        this.typewriterCharMillis = typewriterCharMillis;
    }
}
