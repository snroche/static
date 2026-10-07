package com.estatica.domain.bunker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.TestScouts;
import com.estatica.core.GameRandom;
import com.estatica.domain.log.LogLine;
import com.estatica.domain.scouts.Affinities;
import com.estatica.domain.scouts.BarkRules;
import com.estatica.domain.scouts.ScoutRules;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/** The bunker eats. Balance: 1 ration per person per day, cooks save up to 40 %. */
public class FoodRulesTest {

    private FoodRules food;

    @Before
    public void setUp() {
        food = new FoodRules(TestContent.get().getBalance().getFood());
    }

    @Test
    public void threePeopleEatThreeRationsADay() {
        FoodRules.Meal meal = food.eat(3, 0, 10, 0, 24);
        assertEquals(3, meal.eaten);
        assertEquals(0, meal.hungryHours, 1e-9);
    }

    @Test
    public void oneLongStepEqualsManyShortOnes() {
        FoodRules.Meal once = food.eat(3, 0, 50, 0, 30);
        int eaten = 0;
        double carry = 0;
        for (int i = 0; i < 300; i++) {
            FoodRules.Meal m = food.eat(3, 0, 50 - eaten, carry, 0.1);
            eaten += m.eaten;
            carry = m.carry;
        }
        assertEquals(once.eaten, eaten);
        assertEquals(once.carry, carry, 1e-6);
    }

    @Test
    public void cooksStretchTheRationsUpToACap() {
        assertEquals(0.8 * food.perHour(3, 0), food.perHour(3, 0.2), 1e-9);
        assertEquals(food.perHour(3, 0.4), food.perHour(3, 5.0), 1e-9);
    }

    @Test
    public void runningOutMeansHungerForTheRest() {
        // 3 people, 1 ration left, 24 h: the ration lasts 8 h, then 16 h hungry.
        FoodRules.Meal meal = food.eat(3, 0, 1, 0, 24);
        assertEquals(1, meal.eaten);
        assertEquals(16, meal.hungryHours, 1e-6);
        assertEquals(0, meal.carry, 1e-9);
        assertTrue(food.hungerStress(meal.hungryHours) > 0);
    }

    @Test
    public void daysLeftAndTheWarning() {
        assertEquals(2.0, food.daysLeft(3, 0, 6, 0), 1e-9);
        assertTrue(food.isLow(food.daysLeft(3, 0, 2, 0)));
        assertTrue(Double.isInfinite(food.daysLeft(0, 0, 2, 0)));
    }

    @Test
    public void hungryPeopleTalkAboutFood() {
        ScoutRules rules = new ScoutRules(TestContent.get());
        BarkRules barks = new BarkRules(TestContent.get(), rules);
        int aboutFood = 0;
        for (long seed = 0; seed < 50; seed++) {
            LogLine line = barks.bark(List.of(TestScouts.starter("irene", 0)), new Affinities(100),
                    GameRandom.fromSeed(seed), 0, true);
            aboutFood += TestContent.get().voice("formal").getPhrases().get("hungry").stream()
                    .anyMatch(p -> line.getText().equals(p)) ? 1 : 0;
        }
        assertTrue(aboutFood > 20);
    }
}
