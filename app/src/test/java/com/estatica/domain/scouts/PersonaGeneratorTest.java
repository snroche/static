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
import com.estatica.domain.content.PeopleDef;
import com.estatica.domain.content.ScoutTemplate;
import com.estatica.domain.content.StoryDef;
import com.estatica.domain.content.TraitDef;
import com.estatica.domain.dialogue.DialogueEngine;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Generated survivors: built from parts, never the same as the people around them. */
public class PersonaGeneratorTest {

    private static final long T0 = 1_000_000_000L;

    private GameContent content;
    private PersonaGenerator generator;

    @Before
    public void setUp() {
        content = TestContent.get();
        generator = new PersonaGenerator(content);
    }

    private ScoutTemplate roll(long seed) {
        return generator.generate(PersonaGenerator.ID_PREFIX + seed, 0, List.of(), List.of(), Set.of(),
                GameRandom.fromSeed(seed));
    }

    @Test
    public void sameSeedSamePerson() {
        ScoutTemplate a = roll(7);
        ScoutTemplate b = roll(7);
        assertEquals(a.getName(), b.getName());
        assertEquals(a.getBackground(), b.getBackground());
        assertEquals(a.getTraits(), b.getTraits());
        assertEquals(a.getHiddenTrait(), b.getHiddenTrait());
        assertEquals(a.getVoice(), b.getVoice());
    }

    @Test
    public void everyPersonIsWellFormed() {
        int statMax = content.getBalance().getScouts().getStatMax();
        for (long seed = 0; seed < 300; seed++) {
            ScoutTemplate p = roll(seed);
            String where = "seed " + seed;
            assertTrue(where, ScoutFactory.isAuthored(p) == false);
            PeopleDef.Background bg = content.getPeople().background(p.getBackground());
            assertNotNull(where, bg);
            assertTrue(where + ": voice fits background", bg.getVoices().contains(p.getVoice()));
            assertEquals(where, 2, p.getTraits().size());
            TraitDef a = content.trait(p.getTraits().get(0));
            TraitDef b = content.trait(p.getTraits().get(1));
            assertFalse(where + ": same trait twice", a == b);
            assertFalse(where + ": clashing traits", a.clashesWith(b));
            if (p.getHiddenTrait() != null) {
                TraitDef hidden = content.trait(p.getHiddenTrait());
                assertTrue(where, hidden.isHideable());
                assertFalse(where, p.getTraits().contains(hidden.getId()));
                assertFalse(where, hidden.clashesWith(a) || hidden.clashesWith(b));
            }
            for (int[] range : p.getStats().values()) {
                assertTrue(where, range[0] >= 1 && range[0] < range[1] && range[1] <= statMax);
            }
            assertEquals(where, 3, p.getHabits().size());
            assertFalse(where, p.getLikes().isEmpty());
            for (String dislike : p.getDislikes()) {
                assertFalse(where + ": likes and dislikes the same", p.getLikes().contains(dislike));
            }
            assertNotNull(where, p.getBio());
            assertFalse(where, p.getRescueLines().isEmpty());
        }
    }

    @Test
    public void nobodyIsLikeTheLast() {
        List<ScoutTemplate> crew = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<String> backgrounds = new HashSet<>();
        Set<Set<String>> pairs = new HashSet<>();
        GameRandom rng = GameRandom.fromSeed(42);
        for (int i = 0; i < 6; i++) {
            ScoutTemplate p = generator.generate(PersonaGenerator.ID_PREFIX + i, 1, crew, List.of(), names, rng);
            assertTrue("name " + p.getName(), names.add(p.getName()));
            assertTrue("background " + p.getBackground(), backgrounds.add(p.getBackground()));
            assertTrue("traits " + p.getTraits(), pairs.add(new HashSet<>(p.getTraits())));
            crew.add(p);
        }
    }

    @Test
    public void theNextRecruitDoesNotSoundLikeTheLast() {
        for (long seed = 0; seed < 100; seed++) {
            ScoutTemplate last = roll(seed);
            ScoutTemplate next = generator.generate(PersonaGenerator.ID_PREFIX + "n", 0, List.of(), List.of(last),
                    Set.of(), GameRandom.fromSeed(seed + 1000));
            PeopleDef.Background bg = content.getPeople().background(next.getBackground());
            if (bg.getVoices().size() > 1) {
                assertFalse("seed " + seed, last.getVoice().equals(next.getVoice()));
            }
            assertFalse("seed " + seed, last.getBackground().equals(next.getBackground()));
        }
    }

    @Test
    public void fartherBandsGrowStrongerPeople() {
        int near = 0;
        int far = 0;
        for (long seed = 0; seed < 200; seed++) {
            near += ceilings(generator.generate("p-a", 0, List.of(), List.of(), Set.of(), GameRandom.fromSeed(seed)));
            far += ceilings(generator.generate("p-b", 2, List.of(), List.of(), Set.of(), GameRandom.fromSeed(seed)));
        }
        assertTrue(far > near);
    }

    private static int ceilings(ScoutTemplate p) {
        int sum = 0;
        for (int[] range : p.getStats().values()) {
            sum += range[1];
        }
        return sum;
    }

    @Test
    public void recruitsKeepWhoTheyAre() {
        ScoutTemplate p = roll(3);
        Scout s = ScoutFactory.fromTemplate(p, content.getBalance().getScouts(), T0, 30);
        assertNull("generated people have no content template", s.getTemplateId());
        assertEquals(p, content.persona(s));
        assertEquals(p.getName(), s.getName());
        assertEquals(p.getVoice(), s.getVoiceId());
        assertTrue(s.hasTrait(p.getTraits().get(0)));
    }

    @Test
    public void everyBackgroundHasSomethingToSayAndSomewhereToGo() {
        ScoutRules rules = new ScoutRules(content);
        DialogueEngine engine = new DialogueEngine(content, rules);
        for (PeopleDef.Background bg : content.getPeople().getBackgrounds()) {
            Scout s = TestScouts.person(bg.getId(), 5, 10, 100, T0);
            List<Scout> everyone = List.of(s);
            boolean talksAboutIt = false;
            for (DialogueDef d : content.getDialogues().values()) {
                talksAboutIt |= bg.getId().equals(d.getRequiresBackground()) && engine.isEligible(s, d, everyone, T0);
            }
            assertTrue(bg.getId(), talksAboutIt);
            boolean mission = false;
            for (StoryDef.PersonalMission m : content.getStory().getPersonalMissions()) {
                mission |= m.isFor(s.getTemplateId(), content.persona(s));
            }
            assertTrue(bg.getId(), mission);
        }
    }

    @Test
    public void personalityDialoguesFollowTraitsAndConfessionsFollowHiddenOnes() {
        ScoutRules rules = new ScoutRules(content);
        DialogueEngine engine = new DialogueEngine(content, rules);
        // Starters get trait dialogues too: Tano is reckless.
        Scout tano = TestScouts.custom("tano", 5, 10, 100, T0);
        Scout irene = TestScouts.custom("irene", 5, 10, 100, T0);
        DialogueDef reckless = content.dialogue("imprudencia_salto");
        assertTrue(engine.isEligible(tano, reckless, List.of(tano), T0));
        assertFalse(engine.isEligible(irene, reckless, List.of(irene), T0));

        for (long seed = 0; seed < 50; seed++) {
            ScoutTemplate p = roll(seed);
            if (p.getHiddenTrait() == null) {
                continue;
            }
            Scout s = ScoutFactory.fromTemplate(p, content.getBalance().getScouts(), T0, 30);
            s.getLife().setBond(100);
            DialogueDef confession = null;
            for (DialogueDef d : content.getDialogues().values()) {
                if (p.getHiddenTrait().equals(d.getRequiresHiddenTrait())) {
                    confession = d;
                }
            }
            assertNotNull(p.getHiddenTrait(), confession);
            assertTrue(engine.isEligible(s, confession, List.of(s), T0));
            engine.open(s, confession, false, List.of(s), GameRandom.fromSeed(seed), T0);
            engine.answer(s, confession, 0, List.of(s), T0);
            for (ScoutTrait t : s.getTraits()) {
                assertTrue("revealed " + t.getTraitId(), t.isVisible());
            }
            return;
        }
        throw new AssertionError("no generated person with a hidden trait");
    }
}
