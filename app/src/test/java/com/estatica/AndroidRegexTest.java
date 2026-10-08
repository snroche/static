package com.estatica;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Android runs regexes on ICU, not the JDK engine these tests use. ICU rejects some inline
 * flags the JDK accepts ((?U), (?u)…): it crashed the game twice. Only i, m, s, x are safe.
 */
public class AndroidRegexTest {

    private static final Path SOURCES = Paths.get("src", "main", "java");
    /** An inline flag group inside a string literal: "(?" followed by flag letters and ")" or ":". */
    private static final Pattern INLINE_FLAGS = Pattern.compile("\"[^\"]*\\(\\?([a-zA-Z]+)[):]");

    @Test
    public void noInlineFlagsAndroidRejects() throws IOException {
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = INLINE_FLAGS.matcher(lines.get(i));
                    while (m.find()) {
                        if (!m.group(1).matches("[imsx]+")) {
                            bad.add(file.getFileName() + ":" + (i + 1) + " (?" + m.group(1) + ")");
                        }
                    }
                }
            }
        }
        assertTrue("inline regex flags Android's ICU rejects: " + bad, bad.isEmpty());
    }
}
