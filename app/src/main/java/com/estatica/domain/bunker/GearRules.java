package com.estatica.domain.bunker;

import com.estatica.core.GameRandom;
import com.estatica.domain.content.Balance;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.GearDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Equipment over time: what turns up out there (farther = better), what the workshop turns
 * out (a good crafter makes better pieces), how it wears and what fixing it costs.
 */
public final class GearRules {

    private final GameContent content;
    private final Balance.Gear balance;

    public GearRules(GameContent content) {
        this.content = content;
        this.balance = content.getBalance().getGear();
    }

    /** A piece found on the way back, or null. Odds and rarity grow with the band. */
    public GearPiece find(int bandIndex, GameRandom rng) {
        double[] chances = balance.getFindChance();
        double chance = chances[Math.min(bandIndex, chances.length - 1)];
        if (!rng.chance(chance)) {
            return null;
        }
        List<int[]> weights = balance.getRarityWeights();
        int[] w = weights.get(Math.min(bandIndex, weights.size() - 1));
        return GearPiece.fresh(pickGear(rng), pickRarity(w, rng));
    }

    /** Rarity of a crafted piece: better with a skilled hand at the bench. */
    public GearPiece.Rarity crafted(int bestCraft, GameRandom rng) {
        double rare = Math.max(0, bestCraft - balance.getCraftRareAbove()) * balance.getCraftRarePerPoint();
        double uncommon = bestCraft * balance.getCraftUncommonPerPoint();
        double roll = rng.nextDouble();
        if (roll < rare) {
            return GearPiece.Rarity.RARE;
        }
        return roll < rare + uncommon ? GearPiece.Rarity.UNCOMMON : GearPiece.Rarity.COMMON;
    }

    /** Wear after an expedition. @return the condition before, to detect crossings */
    public int wear(GearPiece piece, boolean injured) {
        int before = piece.getCondition();
        int amount = balance.getWear().getOrDefault(piece.getRarity().name(), 15)
                + (injured ? balance.getInjuryWear() : 0);
        piece.wear(amount);
        return before;
    }

    public boolean isWorn(GearPiece piece) {
        return piece.getCondition() <= balance.getWornAt();
    }

    /** What fixing it costs: more for better pieces. */
    public Map<String, Integer> repairCost(GearPiece piece) {
        return balance.getRepairCost().getOrDefault(piece.getRarity().name(), Map.of());
    }

    private String pickGear(GameRandom rng) {
        List<String> ids = new ArrayList<>(content.getGear().keySet());
        return rng.pick(ids);
    }

    private static GearPiece.Rarity pickRarity(int[] weights, GameRandom rng) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int roll = rng.nextInt(Math.max(1, total));
        GearPiece.Rarity[] rarities = GearPiece.Rarity.values();
        for (int i = 0; i < weights.length && i < rarities.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return rarities[i];
            }
        }
        return GearPiece.Rarity.COMMON;
    }

    // ---- Effects scale with rarity ----

    public static int statBonus(GearDef def, GearPiece piece, String stat) {
        return (int) Math.round(def.statBonus(stat) * piece.getRarity().power);
    }

    public static double lootMultiplier(GearDef def, GearPiece piece) {
        return 1 + (def.getLootMultiplier() - 1) * piece.getRarity().power;
    }

    public static double noiseFactor(GearDef def, GearPiece piece) {
        return Math.max(0.2, 1 - (1 - def.getNoiseFactor()) * piece.getRarity().power);
    }
}
