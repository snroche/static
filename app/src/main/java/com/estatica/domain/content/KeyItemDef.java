package com.estatica.domain.content;

/**
 * Something you keep and read, never use up: a notebook, a manual, a codebook, a pad page
 * ({@code keyitems.json}). Some unlock a way to decode transmissions. Filled by Gson.
 */
public final class KeyItemDef {

    /** Carried home with an expedition as "key:&lt;id&gt;" among the found items. */
    public static final String CARRIED_PREFIX = "key:";

    private String id;
    private String name;
    private String description;
    /** What's written in it, shown when read. */
    private String text;
    /** Transmission kind it lets you decode (e.g. "PLAYFAIR"), or null. */
    private String unlocks;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getText() {
        return text;
    }

    public String getUnlocks() {
        return unlocks;
    }
}
