package com.estatica.data.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.domain.content.GameContent;

import org.junit.Test;

import java.io.StringReader;

/** Validates the real shipped content files, so broken JSON fails the build's tests. */
public class ContentParserTest {

    private final ContentParser parser = new ContentParser();

    @Test
    public void shippedContentLoadsAndCrossReferencesAreValid() {
        GameContent content = TestContent.get();
        assertTrue(content.getBalance().getContentVersion() >= 1);
        assertFalse(content.getTerminal().getBoot().isEmpty());
        assertEquals(2, content.getStarters().size());
        assertTrue("GDD §11: about 40 events", content.getEvents().size() >= 40);
        assertFalse(content.getZones().isEmpty());
    }

    @Test
    public void commandKeysAreNormalized() {
        for (String key : TestContent.get().getTerminal().getCommands().keySet()) {
            assertTrue("command keys must be normalized: " + key,
                    key.equals(key.toLowerCase()) && key.equals(key.trim()));
        }
    }

    @Test
    public void everyEventIsReachableFromSomeZone() {
        GameContent content = TestContent.get();
        for (String eventId : content.getEvents().keySet()) {
            boolean used = content.getZones().values().stream()
                    .anyMatch(z -> z.getEvents().contains(eventId)
                            || content.event(eventId).getBands().contains(z.getBand()))
                    || content.getStory().getPersonalMissions().stream()
                    .anyMatch(m -> m.getEvent().equals(eventId));
            assertTrue("event not used by any zone, band or personal mission: " + eventId, used);
        }
    }

    @Test
    public void missingSectionIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> parser.parseBalance(new StringReader("{\"contentVersion\":1}")));
    }

    @Test
    public void malformedJsonIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> parser.parseTerminal(new StringReader("{boot: [")));
    }

    @Test
    public void emptyArrayIsRejected() {
        assertThrows(IllegalStateException.class, () -> parser.parseZones(new StringReader("[]")));
    }
}
