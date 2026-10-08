package com.estatica.ui.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;

import org.junit.Test;

/** COMMS views: system summaries, field radio and chats never mix. */
public class LogViewTest {

    private static LogLine line(String thread) {
        return LogLine.draft(0, LogChannel.RADIO, "Mara", "text", "s1").withThread(thread);
    }

    @Test
    public void eachLineShowsInExactlyOneView() {
        LogLine system = LogLine.draft(0, LogChannel.SYSTEM, null, "Mara back from the store.");
        LogLine radio = line(LogLine.expeditionThread("e1"));
        LogLine chat = line(LogLine.chatThread("s1"));
        assertTrue(LogView.SYSTEM.shows(system, null));
        assertFalse(LogView.EXPEDITIONS.shows(system, null));
        assertTrue(LogView.EXPEDITIONS.shows(radio, null));
        assertFalse(LogView.SYSTEM.shows(radio, null));
        assertTrue(LogView.CHATS.shows(chat, null));
        assertFalse(LogView.EXPEDITIONS.shows(chat, null));
    }

    @Test
    public void focusNarrowsToOneThread() {
        LogLine one = line(LogLine.expeditionThread("e1"));
        LogLine two = line(LogLine.expeditionThread("e2"));
        assertTrue(LogView.EXPEDITIONS.shows(one, LogLine.expeditionThread("e1")));
        assertFalse(LogView.EXPEDITIONS.shows(two, LogLine.expeditionThread("e1")));
        assertTrue(LogView.CHATS.shows(line(LogLine.chatThread("a")), LogLine.chatThread("a")));
        assertFalse(LogView.CHATS.shows(line(LogLine.chatThread("b")), LogLine.chatThread("a")));
    }

    @Test
    public void threadSurvivesCopies() {
        LogLine l = line(LogLine.chatThread("s1")).withSeq(42);
        assertTrue(LogView.CHATS.shows(l, null));
    }
}
