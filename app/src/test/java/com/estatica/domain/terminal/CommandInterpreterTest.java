package com.estatica.domain.terminal;

import static org.junit.Assert.assertEquals;

import com.estatica.domain.content.TerminalText;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class CommandInterpreterTest {

    private CommandInterpreter interpreter;

    @Before
    public void setUp() {
        String json = "{"
                + "\"boot\":[\"x\"],"
                + "\"reconnect\":[[\"y\"]],"
                + "\"elapsedUnits\":{\"day\":\"d\",\"hour\":\"h\",\"minute\":\"min\",\"lessThanMinute\":\"-\"},"
                + "\"unknownCommand\":\"I don't understand '{input}'\","
                + "\"commands\":{\"help\":[\"list\"],\"status\":[\"v{version}\"]},"
                + "\"statNames\":{},\"expedition\":{},\"bunker\":{}"
                + "}";
        // Plain Gson: this test is about commands, not about which sections terminal.json needs.
        TerminalText text = new com.google.gson.Gson().fromJson(json, TerminalText.class);
        interpreter = new CommandInterpreter(text);
    }

    @Test
    public void knownCommandIsFormatted() {
        assertEquals(List.of("v1.2.3"), interpreter.respond("status", Map.of("version", "1.2.3")));
    }

    @Test
    public void inputIsNormalized() {
        assertEquals(List.of("list"), interpreter.respond("  HÉLP ", Map.of()));
    }

    @Test
    public void unknownCommandEchoesTrimmedInput() {
        assertEquals(List.of("I don't understand 'dance'"), interpreter.respond(" dance ", Map.of()));
    }
}
