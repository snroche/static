package com.estatica.domain.time;

import com.estatica.domain.content.TerminalText.ElapsedUnits;

/** Formats a duration as its two largest units, e.g. "2 d 3 h" or "12 min". */
public final class ElapsedFormatter {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private ElapsedFormatter() {
    }

    public static String format(long millis, ElapsedUnits units) {
        if (millis < MINUTE) {
            return units.getLessThanMinute();
        }
        long days = millis / DAY;
        long hours = (millis % DAY) / HOUR;
        long minutes = (millis % HOUR) / MINUTE;

        if (days > 0) {
            return hours > 0
                    ? days + " " + units.getDay() + " " + hours + " " + units.getHour()
                    : days + " " + units.getDay();
        }
        if (hours > 0) {
            return minutes > 0
                    ? hours + " " + units.getHour() + " " + minutes + " " + units.getMinute()
                    : hours + " " + units.getHour();
        }
        return minutes + " " + units.getMinute();
    }
}
