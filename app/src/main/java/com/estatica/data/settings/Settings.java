package com.estatica.data.settings;

import android.content.Context;
import android.content.SharedPreferences;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Per-device preferences (not game state): sound and notifications, toggled with the
 * terminal commands {@code sonido} and {@code notificaciones} (GDD §9).
 */
@Singleton
public final class Settings {

    private static final String SOUND = "sound";
    private static final String NOTIFICATIONS = "notifications";

    private final SharedPreferences prefs;

    @Inject
    public Settings(@ApplicationContext Context context) {
        this.prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public boolean isSoundOn() {
        return prefs.getBoolean(SOUND, true);
    }

    /** @return the new value */
    public boolean toggleSound() {
        boolean value = !isSoundOn();
        prefs.edit().putBoolean(SOUND, value).apply();
        return value;
    }

    public boolean areNotificationsOn() {
        return prefs.getBoolean(NOTIFICATIONS, true);
    }

    /** @return the new value */
    public boolean toggleNotifications() {
        boolean value = !areNotificationsOn();
        prefs.edit().putBoolean(NOTIFICATIONS, value).apply();
        return value;
    }
}
