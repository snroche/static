package com.estatica.domain.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.core.GameRandom;
import com.estatica.domain.content.BandDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.SignalsDef;
import com.estatica.domain.content.ZoneDef;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RadioTest {

    private static final long HOUR = 3_600_000L;

    private GameContent content;
    private SignalField field;
    private RadioRules rules;
    private BandDef shortBand;
    private BandDef medium;

    @Before
    public void setUp() {
        content = TestContent.get();
        field = new SignalField(content.getBalance(), 1234);
        rules = new RadioRules(content);
        shortBand = content.getBalance().band("short");
        medium = content.getBalance().band("medium");
    }

    @Test
    public void signalsAreFixedWithinAWindowAndMoveBetweenWindows() {
        long w = field.window(100 * HOUR);
        List<SignalField.Spot> a = field.spots(shortBand, w);
        List<SignalField.Spot> b = field.spots(shortBand, w);
        assertEquals(a.get(0).position, b.get(0).position);
        assertEquals(field.window(100 * HOUR), field.window(100 * HOUR + HOUR));
        boolean moved = false;
        for (int i = 1; i < 10; i++) {
            moved |= field.spots(shortBand, w + i).get(0).position != a.get(0).position;
        }
        assertTrue(moved);
        assertNotEquals(new SignalField(content.getBalance(), 99).spots(shortBand, w).get(0).position,
                a.get(0).position);
    }

    @Test
    public void tuningPeaksOnTheSignalAndLocksOnlyWhenClose() {
        long w = 7;
        SignalField.Spot spot = field.spots(medium, w).get(0);
        Set<String> caught = new HashSet<>();
        SignalField.Reading exact = field.tune(medium, spot.position, w, caught);
        assertEquals(1.0, exact.strength, 1e-9);
        assertNotNull(field.lock(medium, spot.position, w, caught));

        int far = (spot.position + medium.getTuneRadius() * 3) % content.getBalance().getRadio().getDialMax();
        SignalField.Reading off = field.tune(medium, far, w, Set.of());
        assertTrue(off.strength < exact.strength);

        for (SignalField.Spot s : field.spots(medium, w)) {
            caught.add(s.key());
        }
        assertNull("caught signals are gone", field.lock(medium, spot.position, w, caught));
    }

    @Test
    public void reachNeedsTheAntennaOrARelayOneBandBefore() {
        assertTrue(rules.isUnlocked(shortBand, 1, List.of()));
        assertFalse(rules.isUnlocked(medium, 1, List.of()));
        assertTrue(rules.isUnlocked(medium, 2, List.of()));

        BandDef longBand = content.getBalance().band("long");
        Relay mediumRelay = Relay.installed("hospital", 0);
        assertFalse(rules.isUnlocked(longBand, 2, List.of(mediumRelay)));
        assertTrue(rules.isUnlocked(longBand, 3, List.of(mediumRelay)));
        mediumRelay.decay(30 * 24 * HOUR, 10, 30);
        assertFalse("a broken relay doesn't extend reach", rules.isUnlocked(longBand, 3, List.of(mediumRelay)));
    }

    @Test
    public void distanceAddsNoiseAndDelayThatRelaysAndTheRadioReduce() {
        ZoneDef near = content.zone("almacen");
        ZoneDef far = content.zone("hospital");
        Map<String, Relay> none = new HashMap<>();
        assertEquals(0, rules.quality(near, none, 1).noise, 1e-9);
        RadioRules.Quality raw = rules.quality(far, none, 1);
        assertTrue(raw.noise > 0 && raw.orderDelayMillis > 0);

        Map<String, Relay> relays = new HashMap<>();
        relays.put("hospital", Relay.installed("hospital", 0));
        RadioRules.Quality relayed = rules.quality(far, relays, 1);
        assertTrue(relayed.noise < raw.noise && relayed.orderDelayMillis < raw.orderDelayMillis);
        assertTrue(rules.quality(far, none, 3).noise < raw.noise);
    }

    @Test
    public void relaysWearDownAndGetRepaired() {
        Relay relay = Relay.installed("hospital", 0);
        assertFalse(relay.decay(3 * 24 * HOUR, 10, 30));
        assertEquals(70, relay.getCondition(), 1e-9);
        assertTrue("reports the moment it stops working", relay.decay(8 * 24 * HOUR, 10, 30));
        assertFalse(relay.decay(9 * 24 * HOUR, 10, 30));
        relay.repair(9 * 24 * HOUR);
        assertTrue(relay.isWorking(30));
    }

    private SignalField.Spot spotOf(SignalField.Kind kind, String band) {
        for (long w = 0; w < 500; w++) {
            for (SignalField.Spot s : field.spots(content.getBalance().band(band), w)) {
                if (s.kind == kind) {
                    return s;
                }
            }
        }
        throw new AssertionError("no " + kind + " in " + band);
    }

    @Test
    public void caughtSignalsResolveIntoSomethingAndFallBackToRareFinds() {
        GameRandom rng = GameRandom.fromSeed(1);
        Set<String> known = new HashSet<>(Set.of("almacen", "estacion", "farmacia", "escuela"));

        RadioRules.Outcome zone = rules.resolve(spotOf(SignalField.Kind.ZONE, "short"), known, true, Set.of(), rng);
        assertEquals(SignalField.Kind.ZONE, zone.kind);
        assertEquals("lavadero", zone.zoneId);
        known.add("lavadero");
        RadioRules.Outcome nothingLeft = rules.resolve(spotOf(SignalField.Kind.ZONE, "short"), known, true, Set.of(), rng);
        assertEquals(SignalField.Kind.RARE, nothingLeft.kind);

        RadioRules.Outcome survivor = rules.resolve(spotOf(SignalField.Kind.SURVIVOR, "medium"), known, true, Set.of(), rng);
        assertEquals(SignalField.Kind.SURVIVOR, survivor.kind);
        assertEquals("medium", content.zone(survivor.zoneId).getBand());
        RadioRules.Outcome enoughWaiting = rules.resolve(spotOf(SignalField.Kind.SURVIVOR, "medium"), known,
                false, Set.of(), rng);
        assertEquals(SignalField.Kind.RARE, enoughWaiting.kind);
    }

    @Test
    public void theFirstTransmissionIsTheMorseManual() {
        GameRandom rng = GameRandom.fromSeed(2);
        Set<String> known = Set.of("almacen", "estacion");
        RadioRules.Outcome first = rules.resolve(spotOf(SignalField.Kind.TRANSMISSION, "short"), known, true, Set.of(), rng);
        assertEquals(SignalsDef.Transmission.Reward.MORSE_TABLE, first.transmission.getReward());
        Transmission manual = rules.encrypt(first.transmission, "short", null, first.transmission.getText(), rng, 0);
        assertEquals("the manual is always Caesar", Transmission.Kind.CAESAR, manual.getKind());

        RadioRules.Outcome next = rules.resolve(spotOf(SignalField.Kind.TRANSMISSION, "short"), known, true,
                Set.of("manual_morse"), rng);
        assertNotEquals("manual_morse", next.transmission.getId());
        if (next.transmission.needsZone()) {
            assertTrue(known.contains(next.zoneId));
        }
    }
}
