package com.estatica.domain.bunker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.RoomDef;
import com.estatica.domain.content.TaskDef;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** The floor plan holds together: no overlaps, every link points somewhere real. */
public class RoomsTest {

    private final GameContent content = TestContent.get();

    @Test
    public void roomsDoNotOverlap() {
        Set<String> cells = new HashSet<>();
        for (RoomDef r : content.getBalance().getRooms()) {
            for (int x = r.getX(); x < r.getX() + r.getW(); x++) {
                for (int y = r.getY(); y < r.getY() + r.getH(); y++) {
                    assertTrue(r.getId() + " overlaps at " + x + "," + y, cells.add(x + "," + y));
                    assertTrue(r.getId() + " off the 10-wide grid", x < 10);
                }
            }
        }
    }

    @Test
    public void everyLinkPointsSomewhereReal() {
        for (RoomDef r : content.getBalance().getRooms()) {
            for (String id : r.getRequires()) {
                assertNotNull(r.getId() + " requires " + id, content.getBalance().room(id));
            }
            for (String m : r.getModules()) {
                assertNotNull(content.getBalance().module(m));
            }
            if (!r.isOpen()) {
                assertTrue(r.getId() + ": a sealed room costs work", r.getHours() > 0);
                assertFalse(r.getId() + ": and resources", r.getCost().isEmpty());
                assertNotNull(r.getId() + ": and says what's in the way", r.getSealed());
            }
        }
        for (TaskDef t : content.getTasks().values()) {
            if (t.getRoom() != null) {
                assertNotNull(t.getId() + " needs room " + t.getRoom(), content.getBalance().room(t.getRoom()));
            }
        }
    }

    @Test
    public void hydroponicsIsLateGame() {
        RoomDef hydro = content.getBalance().room("hydroponics");
        assertTrue(hydro.getRequires().contains("water"));
        int total = 0;
        for (int v : hydro.getCost().values()) {
            total += v;
        }
        assertTrue("the most expensive room", total >= 80 && hydro.getHours() >= 40);
        assertTrue("never enough to live on", content.getBalance().getRoomTuning().getHydroMaxShare() <= 0.3);
    }

    @Test
    public void everyModuleLivesInARoom() {
        Set<String> placed = new HashSet<>();
        for (RoomDef r : content.getBalance().getRooms()) {
            placed.addAll(r.getModules());
        }
        content.getBalance().getModules().forEach(m -> assertTrue(m.getId(), placed.contains(m.getId())));
    }
}
