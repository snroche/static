package com.estatica.ui.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;

import org.junit.Test;

/** COMMS views: system, field radio and chats never mix; ALL views show the overview only. */
public class LogViewTest {

    private static LogLine line(String thread) {
        return LogLine.draft(0, LogChannel.RADIO, "Mara", "text", "s1").withThread(thread);
    }

    @Test
    public void eachLineShowsInExactlyOneView() {
        LogLine system = LogLine.draft(0, LogChannel.SYSTEM, null, "The workshop produced 1 component.");
        LogLine summary = line(LogLine.summaryThread("e1"));
        LogLine radio = line(LogLine.expeditionThread("e1"));
        LogLine bunker = line(LogView.BUNKER_CHAT);
        assertTrue(LogView.SYSTEM.shows(system, null));
        assertFalse(LogView.EXPEDITIONS.shows(system, null));
        assertTrue(LogView.EXPEDITIONS.shows(summary, null));
        assertFalse(LogView.SYSTEM.shows(summary, null));
        assertFalse(LogView.SYSTEM.shows(radio, null));
        assertTrue(LogView.CHATS.shows(bunker, null));
        assertFalse(LogView.EXPEDITIONS.shows(bunker, null));
    }

    @Test
    public void allShowsTheOverviewNotEveryThread() {
        // EXPEDITIONS → ALL: departures, returns and calls for orders, not the whole radio log.
        assertFalse(LogView.EXPEDITIONS.shows(line(LogLine.expeditionThread("e1")), null));
        // CHATS → ALL: what happens between the crew, not one-on-one chats.
        assertFalse(LogView.CHATS.shows(line(LogLine.chatThread("s1")), null));
        // Personal chats still count towards the CHATS badge.
        assertTrue(LogView.CHATS.counts(line(LogLine.chatThread("s1"))));
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
        assertTrue(LogView.CHATS.shows(l, LogLine.chatThread("s1")));
    }
}
