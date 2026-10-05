package com.estatica.domain.content;

/** Chance of finding a personal object on each find of a zone. Filled by Gson. */
public final class ItemDrop {

    private String item;
    private double chance;

    public String getItem() {
        return item;
    }

    public double getChance() {
        return chance;
    }
}
