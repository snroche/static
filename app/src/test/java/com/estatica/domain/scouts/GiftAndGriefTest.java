package com.estatica.domain.scouts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.core.GameRandom;
import com.estatica.domain.content.GameContent;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class GiftAndGriefTest {

    private static final long T0 = 1_000_000_000L;

    private GameContent content;
    private ScoutRules rules;
    private GiftRules gifts;
    private GriefRules grief;

    @Before
    public void setUp() {
        content = TestContent.get();
        rules = new ScoutRules(content);
        gifts = new GiftRules(content, rules);
        grief = new GriefRules(content, rules);
    }

    @Test
    public void theRightGiftMattersAndTeachesTastes() {
        Scout liked = TestScouts.custom("mara", 5, 10, 20, T0);
        GiftRules.Result r = gifts.give(liked, content.item("chocolate"), GameRandom.fromSeed(1), T0);
        assertEquals(GiftRules.Reaction.LIKED, r.reaction);
        assertEquals(35, liked.getLife().getBond());
        assertTrue(liked.getLife().hasFlag(GiftRules.LIKES + "chocolate"));
        assertEquals(Memory.Type.GIFT, r.memories.get(0).getType());

        Scout disliked = TestScouts.custom("mara", 5, 10, 20, T0);
        r = gifts.give(disliked, content.item("estampita"), GameRandom.fromSeed(1), T0);
        assertEquals(GiftRules.Reaction.DISLIKED, r.reaction);
        assertEquals(14, disliked.getLife().getBond());
        assertTrue(disliked.getLife().hasFlag(GiftRules.DISLIKES + "estampita"));
        assertTrue("a gift they dislike isn't treasured", r.memories.isEmpty());

        Scout neutral = TestScouts.custom("mara", 5, 10, 20, T0);
        assertEquals(GiftRules.Reaction.NEUTRAL,
                gifts.give(neutral, content.item("foto"), GameRandom.fromSeed(1), T0).reaction);
        assertEquals(23, neutral.getLife().getBond());
    }

    @Test
    public void closerCompanionsGrieveHarder() {
        Scout dead = TestScouts.custom("tano", 5, 0, 0, T0);
        Scout close = TestScouts.custom("mara", 5, 0, 0, T0);
        Scout distant = TestScouts.custom("irene", 5, 0, 0, T0);
        Affinities affinities = new Affinities(100);
        affinities.add(close.getId(), dead.getId(), 50);
        rules.markDead(dead, T0, "escuela");

        GriefRules.Result r = grief.onDeath(dead, List.of(dead, close, distant), affinities,
                GameRandom.fromSeed(3), T0);
        assertTrue(close.getStress() > distant.getStress());
        assertTrue(distant.getStress() > 0);
        assertEquals(2, r.memories.size());
    }

    @Test
    public void griefCanLeaveAScarOnTheClosest() {
        int scarred = 0;
        for (long seed = 0; seed < 100; seed++) {
            Scout dead = TestScouts.custom("tano", 5, 0, 0, T0);
            Scout close = TestScouts.custom("mara", 5, 0, 0, T0);
            Affinities affinities = new Affinities(100);
            affinities.add(close.getId(), dead.getId(), 60);
            rules.markDead(dead, T0, "escuela");
            grief.onDeath(dead, List.of(dead, close), affinities, GameRandom.fromSeed(seed), T0);
            if (close.hasTrait("rencor") || close.hasTrait("miedo")) {
                scarred++;
            }
        }
        assertTrue(scarred > 10 && scarred < 90);
    }
}
