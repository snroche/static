package com.estatica.domain.expedition;

/** What the scout says about an option instead of the exact probability (GDD §4.2). */
public enum OddsHint {
    GOOD("oddsGood"),
    UNSURE("oddsUnsure"),
    /** "esto me da mala espina": also counts as a death warning (GDD §5.5). */
    BAD("oddsBad");

    private final String phraseKey;

    OddsHint(String phraseKey) {
        this.phraseKey = phraseKey;
    }

    /** Key into the voice's phrase bank. */
    public String phraseKey() {
        return phraseKey;
    }
}
