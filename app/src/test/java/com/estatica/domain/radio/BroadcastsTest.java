package com.estatica.domain.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.domain.content.SignalsDef;

import org.junit.Test;

/** Station timetables (local time). */
public class BroadcastsTest {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private static SignalsDef.Station station(String id) {
        for (SignalsDef.Station s : TestContent.get().getSignals().getStations()) {
            if (s.getId().equals(id)) {
                return s;
            }
        }
        throw new AssertionError(id);
    }

    @Test
    public void bertoTalksForAnHourEveryEvening() {
        SignalsDef.Station berto = station("st_garden");
        long day = 100 * DAY;
        assertFalse(Broadcasts.onAir(berto, day + 18 * HOUR + 59 * MINUTE));
        assertTrue(Broadcasts.onAir(berto, day + 19 * HOUR));
        assertTrue(Broadcasts.onAir(berto, day + 19 * HOUR + 59 * MINUTE));
        assertFalse(Broadcasts.onAir(berto, day + 20 * HOUR));
        assertEquals(day + DAY + 19 * HOUR, Broadcasts.nextStart(berto, day + 20 * HOUR));
    }

    @Test
    public void theVoiceIsThreeMinutesOnTheHour() {
        SignalsDef.Station voice = station("st_voice_hours");
        long t = 100 * DAY + 7 * HOUR;
        assertTrue(Broadcasts.onAir(voice, t + 2 * MINUTE));
        assertFalse(Broadcasts.onAir(voice, t + 3 * MINUTE));
        assertEquals(Broadcasts.airing(voice, t) + 1, Broadcasts.airing(voice, t + HOUR));
    }

    @Test
    public void unscheduledStationsAreAlwaysOn() {
        SignalsDef.Station weather = station("st_weather");
        for (long t = 0; t < DAY; t += 7 * MINUTE) {
            assertTrue(Broadcasts.onAir(weather, t));
        }
    }

    @Test
    public void bulletinStationsLeaveSomething() {
        assertTrue(station("st_relief").hasBulletin());
        assertTrue(station("st_scav").hasBulletin());
        assertFalse(station("st_weather").hasBulletin());
    }
}
