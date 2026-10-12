package com.estatica.domain.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.core.GameRandom;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.SignalsDef;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** What the dial previews is what you get when you lock on. */
public class SignalPreviewTest {

    private final GameContent content = TestContent.get();
    private final RadioRules rules = new RadioRules(content);

    @Test
    public void previewedSchemeIsTheEncryptedScheme() {
        for (SignalsDef.Transmission def : content.getSignals().getTransmissions()) {
            for (String band : new String[]{"short", "medium", "long"}) {
                for (long seed = 0; seed < 20; seed++) {
                    Transmission.Kind previewed = rules.scheme(def, band, GameRandom.fromSeed(seed));
                    Transmission t = rules.encrypt(def, band, null, "THE TEXT", GameRandom.fromSeed(seed), 0);
                    assertEquals(def.getId() + " on " + band, previewed, t.getKind());
                }
            }
        }
    }

    @Test
    public void everySchemeHasItsOwnTeaser() {
        for (Transmission.Kind kind : Transmission.Kind.values()) {
            assertFalse(kind.name(), content.getSignals().teasers("TRANSMISSION", kind.name()).isEmpty());
        }
        assertTrue(content.getSignals().teasers("TRANSMISSION", "MORSE").get(0).contains("·"));
    }

    @Test
    public void unheardRaresComeFirst() {
        SignalField.Spot spot = new SignalField(content.getBalance(), 1).spots(content.getBalance().band("short"), 0).get(0);
        Set<String> heard = new HashSet<>();
        Set<String> seen = new HashSet<>();
        int rares = 0;
        for (SignalsDef.Rare r : content.getSignals().getRare()) {
            rares += r.getBand() == null || "short".equals(r.getBand()) ? 1 : 0;
        }
        for (int i = 0; i < rares; i++) {
            SignalField.Spot rare = new SignalField.Spot(spot.bandId, 0, i, 0, SignalField.Kind.RARE);
            RadioRules.Outcome o = rules.resolve(rare, Set.of(), false, Set.of(), heard, GameRandom.fromSeed(i));
            assertTrue("repeated " + o.rare.getId(), seen.add(o.rare.getId()));
            heard.add(o.rare.getId());
        }
        // All heard: the ones that leave something behind win over flavor.
        RadioRules.Outcome again = rules.resolve(spot, Set.of(), false, Set.of(), heard, GameRandom.fromSeed(99));
        if (again.kind == SignalField.Kind.RARE) {
            assertFalse(again.rare.getResources().isEmpty());
        }
    }

    @Test
    public void frequenciesReadAsMegahertz() {
        assertEquals("3.000 MHz", content.getBalance().band("short").frequency(0));
        assertEquals("7.215 MHz", content.getBalance().band("medium").frequency(215));
        assertEquals("14.731 MHz", content.getBalance().band("long").frequency(731));
    }

    @Test
    public void stationsSitOnTheDial() {
        assertFalse(content.getSignals().getStations().isEmpty());
        for (SignalsDef.Station st : content.getSignals().getStations()) {
            assertTrue(st.getId(), st.getPosition() < content.getBalance().getRadio().getDialMax());
        }
    }
}
