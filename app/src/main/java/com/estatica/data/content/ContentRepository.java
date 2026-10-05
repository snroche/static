package com.estatica.data.content;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.WorkerThread;

import com.estatica.domain.content.ContentValidator;
import com.estatica.domain.content.GameContent;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/** Loads {@code assets/content/*.json} once, validates it, and caches the result. */
@Singleton
public final class ContentRepository {

    private final AssetManager assets;
    private final ContentParser parser;
    private GameContent cached;

    @Inject
    public ContentRepository(@ApplicationContext Context context, ContentParser parser) {
        this.assets = context.getAssets();
        this.parser = parser;
    }

    @WorkerThread
    public synchronized GameContent get() {
        if (cached == null) {
            GameContent content = load(new ContentLoader.Source() {
                @Override
                public Reader open(String file) throws IOException {
                    return new InputStreamReader(assets.open("content/" + file), StandardCharsets.UTF_8);
                }
            });
            ContentValidator.validate(content);
            cached = content;
        }
        return cached;
    }

    private GameContent load(ContentLoader.Source source) {
        try {
            return ContentLoader.load(parser, source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
