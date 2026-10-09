package com.estatica.ui;

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
 * aapt2 writes characters outside the Basic Multilingual Plane into the release resource table
 * as invalid UTF-8, and the resource shrinker then fails. Icons in XML use the font's BMP
 * aliases (U+E000 + (icon − U+F0000)); JSON content can keep the original codepoints.
 */
public class ResourceEncodingTest {

    @Test
    public void xmlResourcesStayInTheBasicPlane() throws IOException {
        Path res = Paths.get("src/main/res");
        List<String> bad = new ArrayList<>();
        Pattern entity = Pattern.compile("&#x([0-9A-Fa-f]+);");
        try (Stream<Path> files = Files.walk(res)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".xml"))::iterator) {
                String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                Matcher m = entity.matcher(text);
                while (m.find()) {
                    if (Integer.parseInt(m.group(1), 16) > 0xFFFF) {
                        bad.add(p.getFileName() + ": " + m.group());
                    }
                }
                if (text.codePoints().anyMatch(c -> c > 0xFFFF)) {
                    bad.add(p.getFileName() + ": literal astral character");
                }
            }
        }
        assertTrue(bad.toString(), bad.isEmpty());
    }
}
