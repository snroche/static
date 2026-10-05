package com.estatica.domain.content;

import java.util.List;
import java.util.Map;

/**
 * A scout's "voice style" (GDD §2): a text transform plus phrase banks. Filled by Gson.
 * Phrase keys: departure, arrival, oddsGood, oddsUnsure, oddsBad, disobey, autoDecide,
 * injured, find, lastWords, transit.
 */
public final class VoiceDef {

    public enum Transform { NONE, LOWER, UPPER }

    private String id;
    private Transform transform = Transform.NONE;
    /** Whole-word replacements applied to every line (slang). */
    private Map<String, String> replacements;
    private Map<String, List<String>> phrases;

    public String getId() {
        return id;
    }

    public Transform getTransform() {
        return transform != null ? transform : Transform.NONE;
    }

    public Map<String, String> getReplacements() {
        return replacements;
    }

    public Map<String, List<String>> getPhrases() {
        return phrases;
    }
}
