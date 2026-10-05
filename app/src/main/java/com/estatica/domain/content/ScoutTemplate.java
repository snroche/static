package com.estatica.domain.content;

import java.util.List;
import java.util.Map;

/** A scout definition from {@code scouts.json}. Filled by Gson. */
public final class ScoutTemplate {

    private String id;
    private String name;
    private String voice;
    private String bio;
    /** Stat key to {@code [base, ceiling]}. */
    private Map<String, int[]> stats;
    private List<String> traits;
    private String hiddenTrait;
    /** Item ids or tags they love. Never shown until discovered. */
    private List<String> likes;
    /** Item ids or tags they dislike. */
    private List<String> dislikes;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVoice() {
        return voice;
    }

    public String getBio() {
        return bio;
    }

    public Map<String, int[]> getStats() {
        return stats;
    }

    public List<String> getTraits() {
        return traits;
    }

    /** Nullable. Revealed with high bond (Phase 2). */
    public String getHiddenTrait() {
        return hiddenTrait;
    }

    public List<String> getLikes() {
        return likes != null ? likes : List.of();
    }

    public List<String> getDislikes() {
        return dislikes != null ? dislikes : List.of();
    }
}
