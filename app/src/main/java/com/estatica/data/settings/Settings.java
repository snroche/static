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
    private static final String HAPTICS = "haptics";
    private static final String TEXT_SPEED = "text_speed";
    private static final String CRT = "crt";
    private static final String OPERATOR_FEMALE = "operator_female";
    private static final String SEND_SORT = "send_sort";

    private final SharedPreferences prefs;

    @Inject
    public Settings(@ApplicationContext Context context) {
        this.prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** How the send screen orders places: DANGER (default), PICKED or TIME. */
    public String getSendSort() {
        return prefs.getString(SEND_SORT, "DANGER");
    }

    public void setSendSort(String sort) {
        prefs.edit().putString(SEND_SORT, sort).apply();
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

    public boolean isHapticsOn() {
        return prefs.getBoolean(HAPTICS, true);
    }

    public boolean toggleHaptics() {
        boolean value = !isHapticsOn();
        prefs.edit().putBoolean(HAPTICS, value).apply();
        return value;
    }

    /** 0 slow … 3 instant; 1 is the default. */
    public int getTextSpeed() {
        return prefs.getInt(TEXT_SPEED, 1);
    }

    public void setTextSpeed(int speed) {
        prefs.edit().putInt(TEXT_SPEED, speed).apply();
    }

    /** The operator is a woman (the crew always say "the operator"; some lines change). */
    public boolean isOperatorFemale() {
        return prefs.getBoolean(OPERATOR_FEMALE, false);
    }

    public boolean toggleOperatorFemale() {
        boolean value = !isOperatorFemale();
        prefs.edit().putBoolean(OPERATOR_FEMALE, value).apply();
        return value;
    }

    /** CRT scanlines over the screen. */
    public boolean isCrtOn() {
        return prefs.getBoolean(CRT, true);
    }

    public boolean toggleCrt() {
        boolean value = !isCrtOn();
        prefs.edit().putBoolean(CRT, value).apply();
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
