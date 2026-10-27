package com.estatica.domain.expedition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.domain.content.EventDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.ZoneDef;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutRules;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/** Situations react to who's there: some options and some events exist only for certain people. */
public class WhoIsThereTest {

    private static final long T0 = 1_000_000_000_000L;

    private GameContent content;
    private ExpeditionEngine engine;

    @Before
    public void setUp() {
        content = TestContent.get();
        engine = new ExpeditionEngine(content);
    }

    private static int indexOf(EventDef event, String optionId) {
        for (int i = 0; i < event.getOptions().size(); i++) {
            if (event.getOptions().get(i).getId().equals(optionId)) {
                return i;
            }
        }
        throw new AssertionError(optionId);
    }

    @Test
    public void onlyANurseCanTreatTheWoundedProperly() {
        EventDef wounded = content.event("herido");
        EventDef.Option treat = wounded.getOptions().get(indexOf(wounded, "tratar"));
        Scout nurse = TestScouts.person("nurse", 5, 10, 0, T0);
        Scout mechanic = TestScouts.person("mechanic", 5, 10, 0, T0);
        assertTrue(engine.isAvailable(nurse, treat));
        assertFalse(engine.isAvailable(mechanic, treat));
        // Starters have no background: authored people only get trait options.
        assertFalse(engine.isAvailable(TestScouts.starter("mara", T0), treat));
    }

    @Test
    public void traitOptionsNeedTheTraitVisible() {
        EventDef basement = content.event("ruido_sotano");
        EventDef.Option dark = basement.getOptions().get(indexOf(basement, "a_oscuras"));
        Scout irene = TestScouts.starter("irene", T0); // Methodical, not cold-blooded
        assertFalse(engine.isAvailable(irene, dark));
        Scout lucho = TestScouts.starter("lucho", T0);
        assertFalse(new ScoutRules(content).fits(lucho, List.of("sangre_fria"), null));
    }

    @Test
    public void pendingOptionsKeepIndexesAndHideWhatDoesNotFit() {
        for (long seed = 0; seed < 200; seed++) {
            Scout s = TestScouts.person("nurse", 5, 10, 0, T0);
            ExpeditionEngine.Dispatch d = engine.dispatch(s, content.zone("hospital"), seed, T0);
            long t = T0;
            while (d.expedition.isActive() && !d.expedition.isAwaitingDecision()) {
                t += 30_000;
                engine.advance(d.expedition, s, List.of(), t);
            }
            if (!d.expedition.isAwaitingDecision()) {
                continue;
            }
            EventDef event = engine.pendingEvent(d.expedition);
            List<String> labels = engine.pendingOptions(d.expedition, s);
            for (int i = 0; i < event.getOptions().size(); i++) {
                EventDef.Option o = event.getOptions().get(i);
                if (engine.isAvailable(s, o) && engine.affordable(d.expedition, o)) {
                    // Options that cost or save time carry it after the label: "(+4 min)".
                    assertTrue(labels.get(i), labels.get(i).startsWith(o.getLabel()));
                } else {
                    assertNull(labels.get(i));
                }
            }
        }
    }

    @Test
    public void onTheirOwnTheyNeverPickAnOptionThatIsNotTheirs() {
        AutoDecider decider = new AutoDecider(new CheckCalculator(content.getBalance().getChecks(), new ScoutRules(content)));
        Scout mara = TestScouts.starter("mara", T0);
        for (EventDef e : content.getEvents().values()) {
            for (long seed = 0; seed < 5; seed++) {
                int pick = decider.pick(e, mara, content.zone("almacen"), com.estatica.domain.content.TraitDef.Bias.RISKY,
                        com.estatica.core.GameRandom.fromSeed(seed), o -> engine.isAvailable(mara, o));
                assertTrue(e.getId(), engine.isAvailable(mara, e.getOptions().get(pick)));
            }
        }
    }

    @Test
    public void someSituationsOnlyHappenToSomePeople() {
        ZoneDef store = content.zone("almacen");
        assertTrue(store.getEvents().contains("escotilla"));
        for (long seed = 0; seed < 200; seed++) {
            Scout mara = TestScouts.starter("mara", T0); // Light feet, Loner: not curious
            Expedition e = engine.dispatch(mara, store, seed, T0).expedition;
            for (Segment seg : e.getSegments()) {
                assertFalse("seed " + seed, "escotilla".equals(seg.getEventId()));
            }
        }
        assertFalse(engine.fits(TestScouts.starter("mara", T0), content.event("escotilla")));
        assertTrue(engine.fits(TestScouts.starter("irene", T0), content.event("escotilla")));
        assertTrue(engine.fits(TestScouts.person("trucker", 5, 0, 0, T0), content.event("semi")));
    }

    @Test
    public void theDecisionCardHasTheSituationAndAGutFeelingPerOption() {
        int checked = 0;
        for (long seed = 0; seed < 100 && checked < 10; seed++) {
            Scout s = TestScouts.person("nurse", 5, 10, 0, T0);
            ExpeditionEngine.Dispatch d = engine.dispatch(s, content.zone("hospital"), seed, T0);
            long t = T0;
            while (d.expedition.isActive() && !d.expedition.isAwaitingDecision()) {
                t += 30_000;
                engine.advance(d.expedition, s, List.of(), t);
            }
            if (!d.expedition.isAwaitingDecision()) {
                continue;
            }
            checked++;
            EventDef event = engine.pendingEvent(d.expedition);
            List<String> text = engine.situationText(d.expedition, s);
            assertEquals(event.getIntro().size(), text.size());
            List<String> hints = engine.pendingHints(d.expedition, s, ExpeditionEngine.Party.ALONE);
            assertEquals(event.getOptions().size(), hints.size());
            for (int i = 0; i < hints.size(); i++) {
                boolean mine = engine.isAvailable(s, event.getOptions().get(i));
                assertEquals(mine, hints.get(i) != null);
                if (mine) {
                    assertTrue(List.of("GOOD", "UNSURE", "BAD", "LOST").contains(hints.get(i)));
                    assertTrue(content.getTerminal().getExpedition().getHintLabels().containsKey(hints.get(i)));
                }
            }
        }
        assertTrue(checked > 0);
    }
}
