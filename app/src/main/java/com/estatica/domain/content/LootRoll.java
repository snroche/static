package com.estatica.domain.content;

/** "Between min and max of a resource" entry used by zones and event outcomes. Filled by Gson. */
public final class LootRoll {

    private String resource;
    private int min;
    private int max;
    /** Relative weight when picking one entry out of a table. */
    private int weight = 1;

    public LootRoll() {
    }

    public LootRoll(String resource, int min, int max, int weight) {
        this.resource = resource;
        this.min = min;
        this.max = max;
        this.weight = weight;
    }

    public String getResource() {
        return resource;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public int getWeight() {
        return weight;
    }
}
