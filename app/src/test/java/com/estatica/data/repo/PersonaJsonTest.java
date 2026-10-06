package com.estatica.data.repo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.estatica.TestScouts;
import com.estatica.domain.content.ScoutTemplate;

import org.junit.Test;

/** A generated person survives a trip through the save. */
public class PersonaJsonTest {

    @Test
    public void roundTrip() {
        ScoutTemplate p = TestScouts.persona("nurse");
        ScoutTemplate back = PersonaJson.read(PersonaJson.write(p));
        assertEquals(p.getId(), back.getId());
        assertEquals(p.getName(), back.getName());
        assertEquals(p.getBackground(), back.getBackground());
        assertEquals(p.getTraits(), back.getTraits());
        assertEquals(p.getHiddenTrait(), back.getHiddenTrait());
        assertEquals(p.getLikes(), back.getLikes());
        assertEquals(p.getRescueLines(), back.getRescueLines());
        assertEquals(p.getHabits().size(), back.getHabits().size());
        assertEquals(p.getHabits().get(2).getMinBondLevel(), back.getHabits().get(2).getMinBondLevel());
        for (String stat : p.getStats().keySet()) {
            assertArrayEquals(p.getStats().get(stat), back.getStats().get(stat));
        }
    }

    @Test
    public void authoredScoutsStoreNothing() {
        assertNull(PersonaJson.write(null));
        assertNull(PersonaJson.read(null));
    }
}
