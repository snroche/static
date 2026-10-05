package com.estatica.domain.content;

import java.util.List;

/** A personal object from {@code items.json}: found on expeditions, given as a gift. Filled by Gson. */
public final class ItemDef {

    private String id;
    private String name;
    private String description;
    private List<String> tags;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getTags() {
        return tags != null ? tags : List.of();
    }
}
