package com.estatica.data.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Translations over the English content. A translation file ({@code content/es/events.json})
 * maps paths to text: {@code "c_trader/options/trade/label": "Comerciar"}. A path walks the
 * JSON by object keys, and through arrays by the element's {@code id} when it has one (by
 * index otherwise). Numbers and ids stay in the base file, in one place.
 */
public final class ContentOverlay {

    /** One lowercase token: an id, a flag, a stat, not something anyone reads. */
    private static final Pattern ID_LIKE = Pattern.compile("[a-z0-9_:.\\-]+");
    private static final Pattern LOWERCASE = Pattern.compile(".*\\p{Ll}.*", Pattern.DOTALL);

    /**
     * Maps whose keys are words on screen too (the info sheet matches glossary terms in the
     * text): a translation renames a key with {@code "glossary/Bond#key": "Vínculo"}.
     */
    private static final java.util.Set<String> KEYED = java.util.Set.of("glossary");
    public static final String KEY_SUFFIX = "#key";

    private ContentOverlay() {
    }

    /**
     * Whether a string in {@code file} is text to translate ({@code _comment}s aside). Terminal and
     * voice files keep single words that are shown (weather, units, slang), so only those count there.
     */
    public static boolean isText(String file, String value) {
        if (!LOWERCASE.matcher(value).matches()) {
            return false; // "F", "?", "SCR"
        }
        if (value.length() > 30 && !value.matches("(?s).*\\s.*")) {
            return false; // a key or a hash, not words
        }
        return file.equals("terminal.json") || file.equals("voices.json") || !ID_LIKE.matcher(value).matches();
    }

    /** Every translatable string of a content file, by path. */
    public static Map<String, String> texts(String file, JsonElement root) {
        Map<String, String> out = new LinkedHashMap<>();
        walk(root, "", (path, value) -> {
            String last = path.substring(path.lastIndexOf('/') + 1);
            if (isText(file, value) && !last.startsWith("_") && !last.equals("id")) {
                out.put(path, value);
            }
            return null;
        });
        for (String mapPath : KEYED) {
            JsonElement m = at(root, mapPath);
            if (m != null && m.isJsonObject()) {
                for (String key : m.getAsJsonObject().keySet()) {
                    out.put(mapPath + "/" + key + KEY_SUFFIX, key);
                }
            }
        }
        // A Vigenère keyword is a word the player types, from a hint in their language.
        JsonElement transmissions = at(root, "transmissions");
        if (transmissions != null && transmissions.isJsonArray()) {
            for (JsonElement t : transmissions.getAsJsonArray()) {
                JsonObject o = t.getAsJsonObject();
                if (o.has("kind") && "VIGENERE".equals(o.get("kind").getAsString())) {
                    out.put("transmissions/" + o.get("id").getAsString() + "/key", o.get("key").getAsString());
                }
            }
        }
        return out;
    }

    /** Replaces the strings that have a translation, in place. */
    public static void apply(JsonElement root, Map<String, String> translations) {
        walk(root, "", (path, value) -> translations.get(path));
        for (String mapPath : KEYED) {
            JsonElement m = at(root, mapPath);
            if (m == null || !m.isJsonObject()) {
                continue;
            }
            JsonObject o = m.getAsJsonObject();
            Map<String, JsonElement> entries = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                String renamed = translations.get(mapPath + "/" + e.getKey() + KEY_SUFFIX);
                entries.put(renamed != null ? renamed : e.getKey(), e.getValue());
            }
            for (String key : new java.util.ArrayList<>(o.keySet())) {
                o.remove(key);
            }
            entries.forEach(o::add);
        }
    }

    /** The element at an object path ("a/b"), or null. */
    private static JsonElement at(JsonElement root, String path) {
        JsonElement e = root;
        for (String segment : path.split("/")) {
            if (e == null || !e.isJsonObject()) {
                return null;
            }
            e = e.getAsJsonObject().get(segment);
        }
        return e;
    }

    private interface Visitor {
        /** @return the replacement, or null to keep it */
        String visit(String path, String value);
    }

    private static void walk(JsonElement e, String path, Visitor visitor) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : o.entrySet()) {
                String p = join(path, entry.getKey());
                JsonElement v = entry.getValue();
                if (isString(v)) {
                    String r = visitor.visit(p, v.getAsString());
                    if (r != null) {
                        entry.setValue(new JsonPrimitive(r));
                    }
                } else {
                    walk(v, p, visitor);
                }
            }
        } else if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) {
                JsonElement v = a.get(i);
                String p = join(path, segment(v, i));
                if (isString(v)) {
                    String r = visitor.visit(p, v.getAsString());
                    if (r != null) {
                        a.set(i, new JsonPrimitive(r));
                    }
                } else {
                    walk(v, p, visitor);
                }
            }
        }
    }

    private static String segment(JsonElement v, int index) {
        if (v.isJsonObject()) {
            JsonElement id = v.getAsJsonObject().get("id");
            if (id != null && isString(id)) {
                return id.getAsString();
            }
        }
        return String.valueOf(index);
    }

    private static boolean isString(JsonElement v) {
        return v.isJsonPrimitive() && v.getAsJsonPrimitive().isString();
    }

    private static String join(String path, String segment) {
        return path.isEmpty() ? segment : path + "/" + segment;
    }
}
