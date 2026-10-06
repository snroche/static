package com.estatica.domain.content;

/** A bunker resource from {@code resources.json}. Filled by Gson. */
public final class ResourceDef {

    public enum Kind { MATERIAL, KIT, PART, GEAR }

    private String id;
    private String name;
    private Kind kind = Kind.MATERIAL;
    /** Three-letter label for the status bar, or null if it isn't shown there. */
    private String shortName;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public Kind getKind() {
        return kind != null ? kind : Kind.MATERIAL;
    }
}
