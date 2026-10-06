package com.estatica.domain.dialogue;

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
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;
import com.estatica.domain.scouts.Memory;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutRules;
import com.estatica.domain.scouts.ScoutTrait;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class DialogueEngineTest {

    private static final long T0 = 1_000_000_000L;
    private static final long HOUR = 3_600_000L;

    private GameContent content;
    private ScoutRules rules;
    private DialogueEngine engine;
    private GameRandom rng;

    @Before
    public void setUp() {
        content = TestContent.get();
        rules = new ScoutRules(content);
        engine = new DialogueEngine(content, rules);
        rng = GameRandom.fromSeed(1);
    }

    @Test
    public void everyStarterHasSomethingToSayOnDayOne() {
        for (String id : List.of("mara", "tano", "irene")) {
            Scout s = TestScouts.starter(id, T0);
            DialogueDef d = engine.pick(s, false, List.of(s), rng, T0);
            assertNotNull(id, d);
            assertEquals("their own story comes before small talk", id, d.getScout());
        }
    }

    @Test
    public void openedDialoguesAreNotRepeated() {
        Scout mara = TestScouts.starter("mara", T0);
        DialogueDef first = engine.pick(mara, false, List.of(mara), rng, T0);
        engine.open(mara, first, false, List.of(mara), rng, T0);
        DialogueDef second = engine.pick(mara, false, List.of(mara), rng, T0);
        assertFalse(first.getId().equals(second.getId()));
        assertFalse(engine.isEligible(mara, first, List.of(mara), T0));
    }

    @Test
    public void bondLevelGatesPersonalConversations() {
        DialogueDef confession = content.dialogue("mara_confianza");
        Scout stranger = TestScouts.custom("mara", 5, 10, 0, T0);
        Scout close = TestScouts.custom("mara", 5, 10, 40, T0);
        assertFalse(engine.isEligible(stranger, confession, List.of(stranger), T0));
        assertTrue(engine.isEligible(close, confession, List.of(close), T0));
    }

    @Test
    public void answerAppliesBondMemoryAndFlags() {
        Scout mara = TestScouts.starter("mara", T0);
        DialogueDef bici = content.dialogue("mara_bici");
        engine.open(mara, bici, false, List.of(mara), rng, T0);
        DialogueEngine.Answer answer = engine.answer(mara, bici, 0, List.of(mara), T0);
        assertEquals(5, mara.getLife().getBond());
        assertEquals(LogChannel.OPERATOR, answer.lines.get(0).getChannel());
        assertEquals(1, answer.memories.size());
        assertEquals(Memory.Type.TALK, answer.memories.get(0).getType());

        DialogueDef music = content.dialogue("mara_musica");
        engine.answer(mara, music, 0, List.of(mara), T0);
        assertTrue(mara.getLife().hasFlag("likes:armonica"));
    }

    @Test
    public void confessionRevealsTheHiddenTrait() {
        Scout mara = TestScouts.custom("mara", 5, 10, 40, T0);
        assertTrue(mara.getTraits().stream().anyMatch(t -> !t.isVisible()));
        DialogueEngine.Answer answer = engine.answer(mara, content.dialogue("mara_confianza"), 1, List.of(mara), T0);
        assertTrue(mara.getTraits().stream().allMatch(ScoutTrait::isVisible));
        assertTrue(answer.lines.stream().anyMatch(l -> l.getText().contains("Distrustful")));
    }

    @Test
    public void guidanceNeedsTheStatAndHappensOnce() {
        DialogueDef guide = content.dialogue("mara_guia");
        Scout novice = TestScouts.custom("mara", 5, 10, 20, T0);
        Scout skilled = TestScouts.custom("mara", 7, 10, 20, T0);
        assertFalse(engine.isEligible(novice, guide, List.of(novice), T0));
        assertTrue(engine.isEligible(skilled, guide, List.of(skilled), T0));
        assertEquals("guidance is scout-initiated", guide, engine.pick(skilled, true, List.of(skilled), rng, T0));

        engine.answer(skilled, guide, 0, List.of(skilled), T0);
        assertTrue(skilled.hasTrait("sombra"));
        assertTrue(rules.hasSpecialization(skilled));
        assertFalse(engine.isEligible(skilled, guide, List.of(skilled), T0));
    }

    @Test
    public void griefComesDaysLaterAndOncePerDeath() {
        Scout mara = TestScouts.starter("mara", T0);
        Scout tano = TestScouts.starter("tano", T0);
        rules.markDead(tano, T0, "escuela");
        List<Scout> everyone = List.of(mara, tano);
        DialogueDef grief = content.dialogue("duelo_recuerdo");

        assertFalse(engine.isEligible(mara, grief, everyone, T0 + 23 * HOUR));
        assertTrue(engine.isEligible(mara, grief, everyone, T0 + 25 * HOUR));
        List<LogLine> lines = engine.open(mara, grief, true, everyone, rng, T0 + 25 * HOUR);
        assertTrue(lines.stream().anyMatch(l -> l.getText().contains("tano")));
        assertFalse(engine.isEligible(mara, grief, everyone, T0 + 50 * HOUR));
    }

    @Test
    public void noConversationsWhileAwayOrDead() {
        Scout mara = TestScouts.starter("mara", T0);
        rules.markDeparted(mara, T0);
        assertNull(engine.pick(mara, false, List.of(mara), rng, T0));
    }
}
