package com.estatica.domain.scouts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.core.GameRandom;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.VignetteDef;
import com.estatica.domain.log.LogLine;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Small moments in the bunker: two people at home, the right kind of moment for how they get on. */
public class VignetteRulesTest {

    private static final long T0 = 1_000_000_000_000L;
    private final GameContent content = TestContent.get();
    private final ScoutRules rules = new ScoutRules(content);
    private final VignetteRules vignettes = new VignetteRules(content, rules);

    @Test
    public void itTakesTwo() {
        Scout mara = TestScouts.starter("mara", T0);
        assertNull(vignettes.pick(List.of(mara), new Affinities(100), false, Set.of(), GameRandom.fromSeed(1), T0));
    }

    @Test
    public void rivalsGetRivalMomentsAndFriendsDont() {
        for (long seed = 0; seed < 100; seed++) {
            Scout a = TestScouts.starter("mara", T0);
            Scout b = TestScouts.starter("irene", T0);
            Affinities neutral = new Affinities(100);
            VignetteRules.Moment m = vignettes.pick(List.of(a, b), neutral, false, Set.of(),
                    GameRandom.fromSeed(seed), T0);
            assertNotNull(m);
            assertFalse("neutral pairs get no rivalry or friendship scenes", m.vignette.getRequires() != null);
            assertFalse("no night scenes by day", m.vignette.isNight());
        }
        int rivalScenes = 0;
        for (long seed = 0; seed < 100; seed++) {
            Scout a = TestScouts.starter("mara", T0);
            Scout b = TestScouts.starter("irene", T0);
            Affinities bad = new Affinities(100);
            bad.put(a.getId(), b.getId(), -100);
            VignetteRules.Moment m = vignettes.pick(List.of(a, b), bad, true, Set.of(), GameRandom.fromSeed(seed), T0);
            assertFalse("rivals aren't friends", "friends".equals(m.vignette.getRequires()));
            rivalScenes += "rivals".equals(m.vignette.getRequires()) ? 1 : 0;
        }
        assertTrue(rivalScenes > 0);
    }

    @Test
    public void namesAreFilledAndRecentOnesDontRepeat() {
        Set<String> recent = new HashSet<>();
        for (VignetteDef v : content.getVignettes()) {
            recent.add(v.getId());
        }
        Scout a = TestScouts.starter("mara", T0);
        Scout b = TestScouts.starter("irene", T0);
        assertNull(vignettes.pick(List.of(a, b), new Affinities(100), true, recent, GameRandom.fromSeed(2), T0));
        VignetteRules.Moment m = vignettes.pick(List.of(a, b), new Affinities(100), true, Set.of(),
                GameRandom.fromSeed(2), T0);
        for (LogLine l : m.lines) {
            assertFalse(l.getText(), l.getText().contains("{a}") || l.getText().contains("{b}"));
        }
    }
}
