package com.estatica.data.repo;

import androidx.annotation.Nullable;

import com.estatica.domain.content.ScoutTemplate;
import com.google.gson.Gson;

/** Generated people are stored as JSON in the save: same shape as an authored template. */
final class PersonaJson {

    private static final Gson GSON = new Gson();

    private PersonaJson() {
    }

    @Nullable
    static String write(@Nullable ScoutTemplate persona) {
        return persona == null ? null : GSON.toJson(persona);
    }

    @Nullable
    static ScoutTemplate read(@Nullable String json) {
        return json == null ? null : GSON.fromJson(json, ScoutTemplate.class);
    }
}
