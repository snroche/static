package com.estatica.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * He or she, by name. Content writes {@code {he:other}}, {@code {his:name}}, {@code {Him:a}}…:
 * the part after the colon is the template variable holding a person's name (or {@code op}
 * for the operator), the part before it the form wanted. Names in the bunker are unique, so
 * the session keeps a registry of who is a woman; TextTemplate resolves the forms with it.
 */
public final class Pronouns {

    /** Name to true for a woman, false for a man. Set by the session. */
    private static final Map<String, Boolean> FEMALE = new ConcurrentHashMap<>();
    private static volatile boolean operatorFemale;

    private Pronouns() {
    }

    public static void register(String name, boolean female) {
        if (name != null) {
            FEMALE.put(name, female);
        }
    }

    public static void setOperatorFemale(boolean female) {
        operatorFemale = female;
    }

    public static boolean isOperatorFemale() {
        return operatorFemale;
    }

    /** Unknown names (someone heard once on the radio) still get a stable answer. */
    public static boolean isFemale(String name) {
        Boolean known = FEMALE.get(name);
        return known != null ? known : Math.floorMod(name.hashCode(), 2) == 1;
    }

    /**
     * The form for a person, or null if {@code form} isn't a pronoun form.
     *
     * @param form he, him, his, hers, himself, man, boy, guy (any capitalized too)
     */
    public static String form(String form, boolean female) {
        int slash = form.indexOf('/');
        if (slash > 0 && slash < form.length() - 1) {
            // Any pair, for languages that gender more than pronouns: {solo/sola:op}.
            return female ? form.substring(slash + 1) : form.substring(0, slash);
        }
        String lower;
        switch (form.toLowerCase(java.util.Locale.ROOT)) {
            case "he":
                lower = female ? "she" : "he";
                break;
            case "him":
                lower = female ? "her" : "him";
                break;
            case "his":
                lower = female ? "her" : "his";
                break;
            case "hers":
                lower = female ? "hers" : "his";
                break;
            case "himself":
                lower = female ? "herself" : "himself";
                break;
            case "man":
                lower = female ? "woman" : "man";
                break;
            case "boy":
                lower = female ? "girl" : "boy";
                break;
            case "guy":
                lower = female ? "girl" : "guy";
                break;
            case "handsome":
                lower = female ? "beautiful" : "handsome";
                break;
            default:
                return null;
        }
        return Character.isUpperCase(form.charAt(0))
                ? Character.toUpperCase(lower.charAt(0)) + lower.substring(1) : lower;
    }

    /** Resolves "he:other" against the template's variables, or null if it isn't one. */
    static String resolve(String key, Map<String, String> vars) {
        int colon = key.indexOf(':');
        if (colon <= 0 || colon == key.length() - 1) {
            return null;
        }
        String ref = key.substring(colon + 1);
        boolean female;
        if ("op".equals(ref)) {
            female = operatorFemale;
        } else {
            String name = vars.get(ref);
            if (name == null) {
                return null;
            }
            female = isFemale(name);
        }
        return form(key.substring(0, colon), female);
    }
}
