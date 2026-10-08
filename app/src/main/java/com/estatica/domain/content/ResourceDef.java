package com.estatica.domain.content;

/** A bunker resource from {@code resources.json}. Filled by Gson. */
public final class ResourceDef {

    /** What it's for, for the info sheet. */
    private String use;

    public String getUse() {
        return use;
    }

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

    /** Nerd Font glyph for the status bar, or null. */
    private String icon;

    public String getIcon() {
        return icon;
    }

    public String getShortName() {
        return shortName;
    }

    public Kind getKind() {
        return kind != null ? kind : Kind.MATERIAL;
    }
}
