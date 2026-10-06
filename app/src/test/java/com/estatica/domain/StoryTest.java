package com.estatica.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.domain.bunker.BunkerRules;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.StoryDef;
import com.estatica.domain.expedition.Expedition;
import com.estatica.domain.expedition.ExpeditionEngine;
import com.estatica.domain.expedition.ExpeditionResult;
import com.estatica.domain.expedition.Mission;
import com.estatica.domain.expedition.Segment;
import com.estatica.domain.scouts.Affinities;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutRules;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/** Phase 5: personal missions, the Voice's fragments, relationships. */
public class StoryTest {

    private static final long T0 = 1_000_000_000_000L;
    private static final long HOUR = 3_600_000L;

    private GameContent content;
    private ExpeditionEngine engine;

    @Before
    public void setUp() {
        content = TestContent.get();
        engine = new ExpeditionEngine(content);
    }

    private static Mission personal(String id) {
        return new Mission(Mission.Type.PERSONAL, id, 0, 0, false, false);
    }

    @Test
    public void personalMissionsPlayTheirOwnSituation() {
        for (StoryDef.PersonalMission m : content.getStory().getPersonalMissions()) {
            for (long seed = 0; seed < 20; seed++) {
                Scout s = m.getScout() != null
                        ? TestScouts.custom(m.getScout(), 6, 0, 60, T0)
                        : TestScouts.person(m.getBackground(), 6, 0, 60, T0);
                Expedition e = engine.dispatch(s, content.zone(m.getZone()), seed, personal(m.getId()), T0).expedition;
                long forced = e.getSegments().stream()
                        .filter(seg -> seg.getType() == Segment.Type.SITUATION && m.getEvent().equals(seg.getEventId()))
                        .count();
                assertEquals(m.getId() + " seed " + seed, 1, forced);
            }
        }
    }

    @Test
    public void irenesLabRevealsAFragmentOfLaVoz() {
        StoryDef.PersonalMission lab = content.getStory().personalMission("irene_lab");
        int found = 0;
        for (long seed = 0; seed < 60; seed++) {
            Scout irene = TestScouts.custom("irene", 8, 0, 60, T0);
            Expedition e = engine.dispatch(irene, content.zone(lab.getZone()), seed, personal(lab.getId()), T0).expedition;
            long t = T0;
            while (e.isActive()) {
                t += 60_000;
                ExpeditionResult r = engine.advance(e, irene, List.of(), t);
                if (r.getPlotFragments().contains("c1_3")) {
                    found++;
                }
                if (e.isAwaitingDecision() && lab.getEvent().equals(e.currentSegment().getEventId())) {
                    engine.choose(e, 0, t); // "Look for her notes"
                }
            }
        }
        assertTrue(found > 0);
    }

    @Test
    public void everyFragmentCanBeFound() {
        java.util.Set<String> sources = new java.util.HashSet<>();
        content.getSignals().getTransmissions().forEach(t -> {
            if (t.getPlot() != null) {
                sources.add(t.getPlot());
            }
        });
        content.getEvents().values().forEach(ev -> ev.getOptions().forEach(o -> {
            if (o.getSuccess().getPlot() != null) {
                sources.add(o.getSuccess().getPlot());
            }
        }));
        for (StoryDef.Fragment f : content.getStory().getChapters().get(0).getFragments()) {
            assertTrue("no way to find " + f.getId(), sources.contains(f.getId()));
        }
    }

    @Test
    public void clashingTraitsBuildRivalries() {
        ScoutRules rules = new ScoutRules(content);
        BunkerRules bunker = new BunkerRules(content, rules);
        Affinities affinities = new Affinities(100);
        // Lucho is reckless, Irene methodical (Método): they clash.
        Scout lucho = TestScouts.starter("lucho", T0);
        Scout irene = TestScouts.starter("irene", T0);
        lucho.getLife().setTaskId("cocina");
        irene.getLife().setTaskId("cocina");
        bunker.advance(List.of(lucho, irene), affinities, Map.of(), T0 + 200 * HOUR);
        assertTrue(affinities.get(lucho.getId(), irene.getId()) < 0);
        assertEquals("rivals", bunker.relationship(affinities, lucho, irene));

        Affinities friends = new Affinities(100);
        Scout mara = TestScouts.starter("mara", T0);
        Scout irene2 = TestScouts.starter("irene", T0);
        mara.getLife().setTaskId("entrenamiento");
        irene2.getLife().setTaskId("entrenamiento");
        bunker.advance(List.of(mara, irene2), friends, Map.of(), T0 + 40 * HOUR);
        assertTrue(friends.get(mara.getId(), irene2.getId()) >= 50);
        assertTrue(bunker.relationship(friends, mara, irene2) != null);
    }
}
