package com.estatica.data.content;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.WorkerThread;

import com.estatica.domain.content.ContentValidator;
import com.estatica.domain.content.GameContent;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/** Loads {@code assets/content/*.json} once, validates it, and caches the result. */
@Singleton
public final class ContentRepository {

    private final AssetManager assets;
    private final ContentParser parser;
    /** "en" or a translation under {@code content/<language>/}. */
    private final String language;
    private GameContent cached;

    @Inject
    public ContentRepository(@ApplicationContext Context context, ContentParser parser) {
        this.assets = context.getAssets();
        this.parser = parser;
        this.language = com.estatica.data.settings.Language.current(context);
    }

    @WorkerThread
    public synchronized GameContent get() {
        if (cached == null) {
            GameContent content = load(new ContentLoader.Source() {
                @Override
                public Reader open(String file) throws IOException {
                    Reader base = new InputStreamReader(assets.open("content/" + file), StandardCharsets.UTF_8);
                    return "en".equals(language) ? base : translated(base, file);
                }
            });
            ContentValidator.validate(content);
            cached = content;
        }
        return cached;
    }

    /** The English file with its translation laid over it (missing lines stay in English). */
    private Reader translated(Reader base, String file) throws IOException {
        Map<String, String> lines = new java.util.HashMap<>();
        try (Reader r = new InputStreamReader(assets.open("content/" + language + "/" + file), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(r).getAsJsonObject().entrySet()) {
                lines.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (java.io.FileNotFoundException e) {
            return base; // nothing translated in this file yet
        }
        JsonElement root;
        try (Reader r = base) {
            root = JsonParser.parseReader(r);
        }
        ContentOverlay.apply(root, lines);
        return new StringReader(root.toString());
    }

    private GameContent load(ContentLoader.Source source) {
        try {
            return ContentLoader.load(parser, source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
