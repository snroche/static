package com.estatica.ui.terminal;

import com.estatica.domain.log.LogLine;

/** The three COMMS views: what the bunker reports, the field radio, and conversations. */
enum LogView {
    SYSTEM, EXPEDITIONS, CHATS;

    /**
     * Whether a line shows in this view; {@code focus} narrows it to one thread. Without a
     * focus ("ALL"), EXPEDITIONS shows only departures, returns and calls for orders, and
     * CHATS only what happens between the crew (personal chats have their own chip).
     */
    boolean shows(LogLine line, String focus) {
        String thread = line.getThread();
        switch (this) {
            case EXPEDITIONS:
                return focus == null ? LogLine.isSummary(thread) : focus.equals(thread);
            case CHATS:
                return focus == null ? BUNKER_CHAT.equals(thread) : focus.equals(thread);
            default:
                return LogLine.SYSTEM_THREAD.equals(thread);
        }
    }

    /** Whether a line counts towards this view's unread badge (anything under it). */
    boolean counts(LogLine line) {
        String thread = line.getThread();
        switch (this) {
            case EXPEDITIONS:
                return LogLine.isSummary(thread);
            case CHATS:
                return thread.startsWith(LogLine.CHAT_PREFIX);
            default:
                return LogLine.SYSTEM_THREAD.equals(thread);
        }
    }

    /** Moments the whole bunker shares (GameSession.BUNKER_CHAT). */
    static final String BUNKER_CHAT = LogLine.chatThread("bunker");
}
