package com.estatica.data.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** The console's words: aliases, accents, and suggestions without game state. */
public class ConsoleCommandsTest {

    @Test
    public void aliasesAndAccentsMapToTheSameCommand() {
        assertEquals("inventory", ConsoleCommands.command("inv"));
        assertEquals("inventory", ConsoleCommands.command(" STORES "));
        assertEquals("help", ConsoleCommands.command("?"));
        assertEquals("crew", ConsoleCommands.command("Scouts"));
        assertEquals("send", ConsoleCommands.command("dispatch"));
        assertNull(ConsoleCommands.command("dance"));
    }

    @Test
    public void typingCompletesCommandNames() {
        List<String> s = ConsoleCommands.suggest("re", null, List.of());
        assertTrue(s.contains("recall "));
        assertTrue(ConsoleCommands.suggest("se", null, List.of()).contains("send "));
    }

    @Test
    public void anEmptyInputOffersRecentCommandsNewestFirst() {
        List<String> s = ConsoleCommands.suggest("", null, List.of("crew", "map", "radio", "crew"));
        assertEquals("crew", s.get(0));
        assertEquals("radio", s.get(1));
        assertTrue(s.size() <= 4);
    }
}
