package com.estatica.domain.expedition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.core.GameRandom;
import com.estatica.domain.content.DialogueDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.ItemDef;
import com.estatica.domain.dialogue.DialogueEngine;
import com.estatica.domain.log.LogLine;
import com.estatica.domain.scouts.DialogueOutcomes;
import com.estatica.domain.scouts.GiftRules;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutRules;
import com.estatica.domain.scouts.StatType;
import com.estatica.domain.world.WeatherRules;
import com.estatica.domain.world.World;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Situations tied to the sky, the dark and earlier choices; favorites; full bonds. */
public class SpecialMomentsTest {

    private static final long T0 = 1_000_000_000_000L;
    private final GameContent content = TestContent.get();

    private static World sky(WeatherRules.Weather weather, boolean night) {
        return new World() {
            @Override
            public WeatherRules.Weather weatherAt(long millis) {
                return weather;
            }

            @Override
            public boolean nightAt(long millis) {
                return night;
            }

            @Override
            public double checkBonus(String stat, long millis) {
                return 0;
            }

            @Override
            public double noiseAt(long millis) {
                return 0;
            }
        };
    }

    /** Every situation that turns up on trips to a zone, over many seeds. */
    private Set<String> seen(ExpeditionEngine engine, String zone, int seeds) {
        Set<String> out = new java.util.HashSet<>();
        for (long seed = 0; seed < seeds; seed++) {
            Scout s = TestScouts.custom("irene", 5, 0, 0, T0);
            for (Segment seg : engine.dispatch(s, content.zone(zone), seed, T0).expedition.getSegments()) {
                if (seg.getEventId() != null) {
                    out.add(seg.getEventId());
                }
            }
        }
        return out;
    }

    @Test
    public void stormSituationsOnlyComeUpInAStorm() {
        ExpeditionEngine engine = new ExpeditionEngine(content);
        engine.setWorld(sky(WeatherRules.Weather.CLEAR, false));
        Set<String> clear = seen(engine, "almacen", 400);
        assertFalse(clear.contains("w_storm_doorway"));
        assertFalse(clear.contains("w_storm_bus"));
        assertFalse("night only", clear.contains("w_night_window"));

        engine.setWorld(sky(WeatherRules.Weather.STORM, true));
        Set<String> storm = seen(engine, "almacen", 400);
        assertTrue(storm.contains("w_storm_doorway") || storm.contains("w_storm_bus"));
        assertTrue(storm.contains("w_night_window"));
        assertFalse("clear-sky ones stay away", storm.contains("w_clear_solar"));
    }

    @Test
    public void onceOnlyAndChainedSituationsFollowTheFlags() {
        ExpeditionEngine engine = new ExpeditionEngine(content);
        Set<String> fresh = seen(engine, "afueras", 600);
        assertTrue("rare, but it turns up", fresh.contains("p_bike_frame"));
        assertFalse("needs the old man helped first", fresh.contains("r_kiosk_gift"));

        Predicate<String> flags = f -> f.equals(ExpeditionEngine.seenFlag("p_bike_frame")) || f.equals("kiosk_helped");
        engine.setWorldFlags(flags);
        Set<String> later = seen(engine, "afueras", 600);
        assertFalse("once is once", later.contains("p_bike_frame"));
        assertTrue(later.contains("r_kiosk_gift"));
    }

    @Test
    public void projectPartsComeHomeWithTheScout() {
        assertEquals("project_bici_ruta", content.projectOf("bike_frame").getId());
        assertNotNull(content.part("sidecar_engine"));
        ExpeditionEngine engine = new ExpeditionEngine(content);
        boolean delivered = false;
        boolean flagged = false;
        for (long seed = 0; seed < 2000 && !delivered; seed++) {
            Scout s = TestScouts.custom("irene", 9, 0, 60, T0);
            Expedition e = engine.dispatch(s, content.zone("afueras"), seed, T0).expedition;
            long t = T0;
            while (e.isActive()) {
                t += 60_000;
                ExpeditionResult r = engine.advance(e, s, List.of(), t);
                if (e.isAwaitingDecision()) {
                    engine.choose(e, 0, t);
                }
                delivered |= r.getDeliveredItems().contains(ExpeditionEngine.PART_PREFIX + "bike_frame");
                flagged |= r.getWorldFlags().contains(ExpeditionEngine.seenFlag("p_bike_frame"));
            }
        }
        assertTrue(delivered);
        assertTrue("once-only situations leave their mark", flagged);
        assertEquals("Racing bike frame", engine.carriedName(ExpeditionEngine.PART_PREFIX + "bike_frame"));
    }

    @Test
    public void theirFavoriteIsTheirOwnAndLiftsThem() {
        ScoutRules rules = new ScoutRules(content);
        GiftRules gifts = new GiftRules(content, rules);
        Scout mara = TestScouts.custom("mara", 5, 40, 20, T0);
        assertEquals("armonica", content.favoriteOf(mara).getId());
        int wits = rules.effectiveStat(mara, StatType.WITS);
        GiftRules.Result r = gifts.give(mara, content.item("armonica"), GameRandom.fromSeed(3), T0);
        assertEquals(GiftRules.Reaction.FAVORITE, r.reaction);
        assertTrue(mara.getStress() < 40);
        assertTrue(mara.getLife().hasFlag(GiftRules.FAVORITE_KNOWN));
        assertEquals("a gift they love lifts them for the next trip", wits + 1, rules.effectiveStat(mara, StatType.WITS));
        boolean warm = false;
        for (LogLine line : r.lines) {
            warm |= LogLine.Cue.WARM.equals(line.getCue());
        }
        assertTrue(warm);
        rules.markReturned(mara, T0);
        assertEquals("spent once they're back", wits, rules.effectiveStat(mara, StatType.WITS));
    }

    @Test
    public void generatedPeopleHaveAStableFavoriteAmongWhatTheyLike() {
        ScoutRules rules = new ScoutRules(content);
        for (String background : List.of("railway", "teen", "nurse", "trucker", "teacher", "mechanic", "cook", "musician")) {
            Scout s = TestScouts.person(background, 5, 0, 0, T0);
            ItemDef fav = content.favoriteOf(s);
            if (fav == null) {
                continue;
            }
            assertEquals(fav.getId(), content.favoriteOf(s).getId());
            assertEquals(GiftRules.Reaction.FAVORITE, new GiftRules(content, rules).reactionTo(s, fav));
        }
    }

    @Test
    public void aFullBondEasesInsteadOfGrowing() {
        ScoutRules rules = new ScoutRules(content);
        Scout s = TestScouts.custom("irene", 5, 50, content.getBalance().getBond().getMax(), T0);
        List<LogLine> lines = new ArrayList<>();
        DialogueOutcomes.applyBond(content, rules, s, 10, T0, lines);
        assertEquals(content.getBalance().getBond().getMax(), s.getLife().getBond());
        assertTrue(s.getStress() < 50);
        assertEquals(1, lines.size());
    }

    @Test
    public void someTalksWaitForTheirWeather() {
        ScoutRules rules = new ScoutRules(content);
        DialogueEngine engine = new DialogueEngine(content, rules);
        DialogueDef storm = content.dialogue("wx_storm_cant_sleep");
        Scout s = TestScouts.custom("irene", 5, 10, 30, T0);
        engine.setWorld(sky(WeatherRules.Weather.CLEAR, false));
        assertFalse(engine.isEligible(s, storm, List.of(s), T0));
        engine.setWorld(sky(WeatherRules.Weather.STORM, false));
        assertTrue(engine.isEligible(s, storm, List.of(s), T0));
    }
}
