package com.estatica.data.content;

import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.domain.content.ContentValidator;
import com.estatica.domain.content.GameContent;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Every text ships in English and Spanish ({@code content/es/}): nothing missing, nothing
 * stale, the same placeholders, and the Spanish game passes the same checks as the English one.
 */
public class TranslationTest {

    private static final String LANG = "es";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}:]*)}");

    private static List<String> files() throws IOException {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(TestContent.DIR)) {
            s.filter(p -> p.toString().endsWith(".json")).forEach(p -> out.add(p.getFileName().toString()));
        }
        return out;
    }

    private static JsonElement base(String file) throws IOException {
        try (Reader r = Files.newBufferedReader(TestContent.DIR.resolve(file), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r);
        }
    }

    private static Map<String, String> translation(String file) throws IOException {
        Map<String, String> out = new HashMap<>();
        Path p = TestContent.DIR.resolve(LANG).resolve(file);
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(r).getAsJsonObject().entrySet()) {
                    out.put(e.getKey(), e.getValue().getAsString());
                }
            }
        }
        return out;
    }

    private static List<String> placeholders(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
        out.sort(String::compareTo);
        return out;
    }

    @Test
    public void everyTextHasItsTranslation() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String file : files()) {
            Map<String, String> texts = ContentOverlay.texts(file, base(file));
            Map<String, String> es = translation(file);
            for (Map.Entry<String, String> t : texts.entrySet()) {
                String tr = es.get(t.getKey());
                if (tr == null) {
                    problems.add(file + ": missing " + t.getKey());
                } else if (!placeholders(t.getValue()).equals(placeholders(tr))) {
                    problems.add(file + ": placeholders differ at " + t.getKey());
                }
            }
            for (String key : es.keySet()) {
                if (!texts.containsKey(key)) {
                    problems.add(file + ": stale " + key);
                }
            }
        }
        assertTrue(problems.size() + " problems, first: "
                + problems.subList(0, Math.min(10, problems.size())), problems.isEmpty());
    }

    @Test
    public void theSpanishGamePassesTheSameChecks() throws IOException {
        GameContent content = ContentLoader.load(new ContentParser(), file -> {
            JsonElement root = base(file);
            ContentOverlay.apply(root, translation(file));
            return new StringReader(root.toString());
        });
        ContentValidator.validate(content);
    }
}
