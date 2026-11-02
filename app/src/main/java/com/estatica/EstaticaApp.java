package com.estatica;

import android.app.Application;
import android.content.Context;

import com.estatica.data.settings.Language;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class EstaticaApp extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Language.wrap(base));
    }
}
