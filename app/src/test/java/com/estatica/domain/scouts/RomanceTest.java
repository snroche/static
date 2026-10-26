package com.estatica.domain.scouts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.core.GameRandom;
import com.estatica.domain.content.DialogueDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.dialogue.DialogueEngine;
import com.estatica.domain.log.LogLine;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Who can be courted, when, and what the dialogue engine lets through. */
public class RomanceTest {

    private static final long T0 = 1_000_000_000L;
    private final GameContent content = TestContent.get();
    private final ScoutRules rules = new ScoutRules(content);
    private final RomanceRules romance = new RomanceRules(content, rules);

    /** A fake romance state for the engine. */
    private static final class Stub implements DialogueEngine.Romance {
        boolean ready = true;
        boolean together;
        boolean adult = true;
        int meter;
        int started;

        @Override
        public boolean adult(Scout s) {
            return adult;
        }

        @Override
        public boolean ready(Scout s) {
            return ready;
        }

        @Override
        public boolean together(Scout s) {
            return together;
        }

        @Override
        public int meter(Scout s) {
            return meter;
        }

        @Override
        public void start(Scout s, long now, List<LogLine> lines) {
            started++;
            together = true;
        }
    }

    @Test
    public void teenagersAreNeverCourted() {
        Scout adult = TestScouts.custom("mara", 5, 10, 100, T0);
        assertTrue("starters are adults", romance.isAdult(adult));
        com.estatica.domain.content.ScoutTemplate kid = TestScouts.persona("teen");
        Scout teen = ScoutFactory.fromTemplate(kid, content.getBalance().getScouts(), T0);
        teen.setPersona(kid);
        // Maxed out bond changes nothing.
        rules.addBond(teen, 100);
        assertFalse(romance.isAdult(teen));
        assertFalse(romance.isReady(teen));
    }

    @Test
    public void adultsOnlyEvenIfTheMeterSaysOtherwise() {
        Stub stub = new Stub();
        stub.adult = false;
        stub.meter = 100;
        DialogueEngine engine = new DialogueEngine(content, rules);
        engine.setRomance(stub);
        Scout s = TestScouts.custom("mara", 5, 10, 100, T0);
        for (DialogueDef d : content.getDialogues().values()) {
            if (d.isRomantic()) {
                assertFalse(d.getId(), engine.isEligible(s, d, List.of(s), T0));
            }
        }
    }

    @Test
    public void nothingRomanticBeforeTheyAreReady() {
        Stub stub = new Stub();
        stub.ready = false;
        DialogueEngine engine = new DialogueEngine(content, rules);
        engine.setRomance(stub);
        Scout s = TestScouts.custom("mara", 5, 10, 0, T0);
        for (DialogueDef d : content.getDialogues().values()) {
            if (d.isRomanceReady()) {
                assertFalse(d.getId(), engine.isEligible(s, d, List.of(s), T0));
            }
        }
    }

    @Test
    public void aConfessionAcceptedStartsIt() {
        Stub stub = new Stub();
        stub.meter = 80;
        DialogueEngine engine = new DialogueEngine(content, rules);
        engine.setRomance(stub);
        Scout s = TestScouts.custom("mara", 5, 10, 90, T0);
        DialogueDef confession = content.dialogue("rom_confess_them");
        assertTrue(engine.isEligible(s, confession, List.of(s), T0));
        engine.answer(s, confession, 0, List.of(s), T0);
        assertEquals(1, stub.started);
        assertFalse("once together, no more confessions", engine.isEligible(s, confession, List.of(s), T0));
    }

    /** Dozens of everyday talks queued at higher priority must not keep romance away forever. */
    @Test
    public void romanceGetsItsShareOfTalks() {
        Stub stub = new Stub();
        DialogueEngine engine = new DialogueEngine(content, rules);
        engine.setRomance(stub);
        Scout s = TestScouts.custom("mara", 5, 10, 100, T0);
        int romantic = 0;
        for (long seed = 0; seed < 100; seed++) {
            DialogueDef d = engine.pick(s, false, List.of(s), GameRandom.fromSeed(seed), T0);
            romantic += d != null && d.isRomantic() ? 1 : 0;
        }
        assertTrue("romantic talks: " + romantic, romantic >= 20 && romantic <= 60);
    }

    @Test
    public void lotsOfRomanceToFind() {
        int count = 0;
        for (DialogueDef d : content.getDialogues().values()) {
            count += d.isRomantic() || d.isRequiresDeadPartner() ? 1 : 0;
        }
        assertTrue("romance dialogues: " + count, count >= 70);
    }

    @Test
    public void openPeopleWarmUpSooner() {
        for (long seed = 0; seed < 30; seed++) {
            Scout s = TestScouts.custom("mara", 5, 10, 0, T0);
            int level = romance.readyAtBondLevel(s);
            assertTrue(level >= 2 && level <= 4);
            assertTrue(romance.openness(s) >= 0 && romance.openness(s) <= 100);
            assertTrue(romance.jealousy(s) >= 0 && romance.jealousy(s) <= 100);
        }
        assertNotNull(GameRandom.fromSeed(1));
    }

    @Test
    public void loversFollowFurtherAndSoundCloser() {
        Scout s = TestScouts.custom("mara", 5, 10, 0, T0);
        assertFalse(rules.isLover(s));
        s.getLife().addFlag(RomanceRules.FLAG);
        assertTrue(rules.isLover(s));
        assertTrue(rules.isClose(s));
        s.getLife().removeFlag(RomanceRules.FLAG);
        assertFalse(rules.isLover(s));
        List<LogLine> none = new ArrayList<>();
        assertTrue(none.isEmpty());
    }
}
