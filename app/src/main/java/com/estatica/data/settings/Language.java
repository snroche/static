package com.estatica.data.settings;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/**
 * The game's language: the system's, or one picked in options. UI strings come from
 * {@code values-<lang>}, content from {@code assets/content/<lang>/} over the English base.
 * Read before Hilt is up (attachBaseContext), so it goes straight to the preferences.
 */
public final class Language {

    /** What the options cycle through; "" follows the system. */
    public static final String[] CHOICES = {"", "en", "es"};
    static final String KEY = "language";
    private static final String PREFS = "settings";

    private Language() {
    }

    /** "", "en" or "es": what was picked. */
    public static String picked(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
    }

    @android.annotation.SuppressLint("ApplySharedPref")
    public static void pick(Context context, String language) {
        // commit, not apply: the app restarts right after.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language).commit();
    }

    /** The language in effect: "en" or "es". */
    public static String current(Context context) {
        String picked = picked(context);
        if (!picked.isEmpty()) {
            return picked;
        }
        Locale system = Resources.getSystem().getConfiguration().getLocales().get(0);
        return "es".equals(system.getLanguage()) ? "es" : "en";
    }

    /** A context whose resources speak the game's language. */
    public static Context wrap(Context base) {
        if (picked(base).isEmpty()) {
            return base;
        }
        Locale locale = new Locale(current(base));
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }
}
