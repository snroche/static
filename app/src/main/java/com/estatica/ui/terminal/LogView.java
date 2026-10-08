package com.estatica.ui.terminal;

import com.estatica.domain.log.LogLine;

/** The three COMMS views: what the bunker reports, the field radio, and conversations. */
enum LogView {
    SYSTEM, EXPEDITIONS, CHATS;

    /** Whether a line shows in this view; {@code focus} narrows it to one thread (null = all). */
    boolean shows(LogLine line, String focus) {
        String thread = line.getThread();
        switch (this) {
            case EXPEDITIONS:
                return thread.startsWith(LogLine.EXPEDITION_PREFIX) && (focus == null || focus.equals(thread));
            case CHATS:
                return thread.startsWith(LogLine.CHAT_PREFIX) && (focus == null || focus.equals(thread));
            default:
                return LogLine.SYSTEM_THREAD.equals(thread);
        }
    }
}
