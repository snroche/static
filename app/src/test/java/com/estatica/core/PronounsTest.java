package com.estatica.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Map;

public class PronounsTest {

    @Test
    public void formsFollowTheNamedPerson() {
        Pronouns.register("Mara", true);
        Pronouns.register("Leo", false);
        Map<String, String> vars = Map.of("name", "Mara", "other", "Leo");
        assertEquals("Mara says she misses him.",
                TextTemplate.format("{name} says {he:name} misses {him:other}.", vars));
        assertEquals("Her bag, his coat. She laughs.",
                TextTemplate.format("{His:name} bag, {his:other} coat. {He:name} laughs.", vars));
    }

    @Test
    public void theOperatorHasASetting() {
        Pronouns.setOperatorFemale(true);
        assertEquals("beautiful", TextTemplate.format("{handsome:op}", Map.of()));
        Pronouns.setOperatorFemale(false);
        assertEquals("handsome", TextTemplate.format("{handsome:op}", Map.of()));
    }

    @Test
    public void unknownKeysStayUntouched() {
        assertEquals("{he:nobody} {x:name}", TextTemplate.format("{he:nobody} {x:name}", Map.of("name", "Mara")));
    }
}
