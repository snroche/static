package com.estatica.domain.scouts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.core.GameRandom;
import com.estatica.domain.content.DialogueDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.dialogue.DialogueEngine;
import com.estatica.domain.log.LogLine;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Moods, ambient chatter and conversations about the people they get along with. */
public class PersonalityTest {

    private static final long T0 = 1_000_000_000L;

    private GameContent content;
    private ScoutRules rules;

    @Before
    public void setUp() {
        content = TestContent.get();
        rules = new ScoutRules(content);
    }

    @Test
    public void moodFollowsStressAndBond() {
        assertEquals("breaking", rules.mood(TestScouts.custom("mara", 5, 80, 0, T0)));
        assertEquals("tense", rules.mood(TestScouts.custom("mara", 5, 50, 0, T0)));
        assertEquals("steady", rules.mood(TestScouts.custom("mara", 5, 10, 0, T0)));
        assertEquals("content", rules.mood(TestScouts.custom("mara", 5, 10, 100, T0)));
    }

    @Test
    public void nobodyHomeMeansSilence() {
        Scout mara = TestScouts.starter("mara", T0);
        mara.setStatus(ScoutStatus.AWAY);
        BarkRules barks = new BarkRules(content, rules);
        assertNull(barks.bark(List.of(mara), new Affinities(100), GameRandom.fromSeed(1), T0));
    }

    @Test
    public void rivalsComplainAboutEachOther() {
        Scout lucho = TestScouts.starter("lucho", T0);
        Scout irene = TestScouts.starter("irene", T0);
        Affinities affinities = new Affinities(100);
        affinities.put(lucho.getId(), irene.getId(), -100);
        BarkRules barks = new BarkRules(content, rules);
        boolean mentioned = false;
        for (long seed = 0; seed < 40; seed++) {
            LogLine line = barks.bark(List.of(lucho, irene), affinities, GameRandom.fromSeed(seed), T0);
            assertNotNull(line);
            assertFalse("placeholders are filled: " + line.getText(), line.getText().contains("{"));
            String other = line.getSpeaker().equals(lucho.getName()) ? irene.getName() : lucho.getName();
            mentioned |= line.getText().toLowerCase().contains(other.toLowerCase());
        }
        assertTrue(mentioned);
    }

    @Test
    public void friendsTalkAboutEachOtherAndTheAnswerMovesAffinity() {
        Scout mara = TestScouts.starter("mara", T0);
        Scout irene = TestScouts.starter("irene", T0);
        Map<String, Double> adjusted = new HashMap<>();
        DialogueEngine.Relations relations = new DialogueEngine.Relations() {
            @Override
            public Scout find(Scout scout, String kind) {
                return "friend".equals(kind) ? (scout == mara ? irene : mara) : null;
            }

            @Override
            public void adjust(Scout a, Scout b, double delta) {
                adjusted.merge(Affinities.key(a.getId(), b.getId()), delta, Double::sum);
            }
        };
        DialogueDef talk = content.dialogue("amigos_charla");
        List<Scout> everyone = List.of(mara, irene);

        assertFalse("needs a friend", new DialogueEngine(content, rules).isEligible(mara, talk, everyone, T0));

        DialogueEngine engine = new DialogueEngine(content, rules, relations);
        assertTrue(engine.isEligible(mara, talk, everyone, T0));
        List<LogLine> lines = engine.open(mara, talk, false, everyone, GameRandom.fromSeed(1), T0);
        // Mara speaks in lowercase.
        assertTrue(lines.get(0).getText().toLowerCase().contains(irene.getName().toLowerCase()));
        engine.answer(mara, talk, 0, everyone, T0);
        assertEquals(5.0, adjusted.get(Affinities.key(mara.getId(), irene.getId())), 0.001);
    }
}
