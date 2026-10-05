package com.estatica.domain.time;

import static org.junit.Assert.assertEquals;

import com.estatica.domain.content.TerminalText.ElapsedUnits;

import org.junit.Test;

public class ElapsedFormatterTest {

    private static final ElapsedUnits UNITS = new ElapsedUnits("d", "h", "min", "un rato");
    private static final long MIN = 60_000L;
    private static final long HOUR = 60 * MIN;
    private static final long DAY = 24 * HOUR;

    @Test
    public void underAMinute() {
        assertEquals("un rato", ElapsedFormatter.format(59_999, UNITS));
        assertEquals("un rato", ElapsedFormatter.format(-5, UNITS));
    }

    @Test
    public void minutesOnly() {
        assertEquals("12 min", ElapsedFormatter.format(12 * MIN + 30_000, UNITS));
    }

    @Test
    public void hoursAndMinutes() {
        assertEquals("3 h 12 min", ElapsedFormatter.format(3 * HOUR + 12 * MIN, UNITS));
        assertEquals("3 h", ElapsedFormatter.format(3 * HOUR + 59_000, UNITS));
    }

    @Test
    public void daysShowAtMostTwoUnits() {
        assertEquals("2 d 3 h", ElapsedFormatter.format(2 * DAY + 3 * HOUR + 45 * MIN, UNITS));
        assertEquals("2 d", ElapsedFormatter.format(2 * DAY + 45 * MIN, UNITS));
    }
}
