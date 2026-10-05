package com.estatica;

import com.estatica.data.content.ContentLoader;
import com.estatica.data.content.ContentParser;
import com.estatica.domain.content.ContentValidator;
import com.estatica.domain.content.GameContent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Loads the real shipped content from {@code src/main/assets/content} for tests. */
public final class TestContent {

    public static final Path DIR = Paths.get("src", "main", "assets", "content");

    private static GameContent cached;

    private TestContent() {
    }

    public static synchronized GameContent get() {
        if (cached == null) {
            try {
                cached = ContentLoader.load(new ContentParser(),
                        file -> Files.newBufferedReader(DIR.resolve(file), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            ContentValidator.validate(cached);
        }
        return cached;
    }
}
