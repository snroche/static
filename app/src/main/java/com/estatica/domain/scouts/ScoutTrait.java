package com.estatica.domain.scouts;

/** A trait owned by a scout. Hidden traits apply their effects but aren't shown until revealed. */
public final class ScoutTrait {

    private final String traitId;
    private final boolean hidden;
    private boolean revealed;

    public ScoutTrait(String traitId, boolean hidden, boolean revealed) {
        this.traitId = traitId;
        this.hidden = hidden;
        this.revealed = revealed;
    }

    public String getTraitId() {
        return traitId;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isRevealed() {
        return revealed;
    }

    public boolean isVisible() {
        return !hidden || revealed;
    }

    public void reveal() {
        revealed = true;
    }
}
