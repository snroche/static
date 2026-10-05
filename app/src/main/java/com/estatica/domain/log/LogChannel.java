package com.estatica.domain.log;

/** Who or what produced a line in the radio log. */
public enum LogChannel {
    /** Bunker terminal / system messages. */
    SYSTEM,
    /** Echo of what the operator typed. Shown instantly, never typewritten. */
    OPERATOR,
    /** Incoming voice transmission (scouts, survivors, unknown sources). */
    RADIO,
    /** Urgent message. */
    ALERT;

    public static LogChannel fromName(String name) {
        for (LogChannel channel : values()) {
            if (channel.name().equals(name)) {
                return channel;
            }
        }
        return SYSTEM;
    }
}
