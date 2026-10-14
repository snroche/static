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

    /** Wear from time out there. @return the condition before, to detect crossings */
    public int wearForTrip(GearPiece piece, double hours) {
        int before = piece.getCondition();
        piece.wear(Math.max(1, (int) Math.round(hours * balance.getWearPerHour() * rarityWear(piece))));
        return before;
    }

    /** Wear from a failed check (scrapes, falls, a bad landing). */
    public int wearForFailure(GearPiece piece) {
        int before = piece.getCondition();
        piece.wear(Math.max(1, (int) Math.round(balance.getWearPerFailure() * rarityWear(piece))));
        return before;
    }

    /** Wear from taking a hit, by how bad it was. */
    public int wearForInjury(GearPiece piece, String injury) {
        int before = piece.getCondition();
        int base = balance.wearPerInjury(injury);
        if (base > 0) {
            piece.wear(Math.max(1, (int) Math.round(base * rarityWear(piece))));
        }
        return before;
    }

    private double rarityWear(GearPiece piece) {
        return balance.rarityWear(piece.getRarity().name());
    }

    public boolean isWorn(GearPiece piece) {
        return piece.getCondition() <= balance.getWornAt();
    }

    /** What fixing it costs: more for better pieces. */
    public Map<String, Integer> repairCost(GearPiece piece) {
        Map<String, Integer> base = balance.getRepairCost().getOrDefault(piece.getRarity().name(), Map.of());
        com.estatica.domain.content.GearDef def = content.gear(piece.getGearId());
        int scale = def != null ? def.getRepairScale() : 1;
        if (scale == 1) {
            return base;
        }
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        base.forEach((k, v) -> out.put(k, v * scale));
        return out;
    }

    /** Anything that can turn up out there: not vehicles, those get built. */
    private String pickGear(GameRandom rng) {
        List<String> ids = new ArrayList<>();
        for (com.estatica.domain.content.GearDef def : content.getGear().values()) {
            if (def.isFindable()) {
                ids.add(def.getId());
            }
        }
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

    /** Stat bonus: one more point per rarity step (flashlight +1, +2, +3). */
    public static int statBonus(GearDef def, GearPiece piece, String stat) {
        int base = def.statBonus(stat);
        return base == 0 ? 0 : base + piece.getRarity().ordinal();
    }

    /** Trip length: better pieces shave more off, gently (a rare motorbike isn't a jet). */
    public static double durationMultiplier(GearDef def, GearPiece piece) {
        double m = def.getDurationMultiplier();
        double power = 1 + (piece.getRarity().power - 1) * 0.5;
        return m >= 1 ? m : Math.max(0.35, 1 - (1 - m) * power);
    }

    /** Stress taken: better pieces help more. */
    public static double stressFactor(GearDef def, GearPiece piece) {
        double f = def.getStressFactor();
        return f >= 1 ? f : Math.max(0.3, 1 - (1 - f) * piece.getRarity().power);
    }

    /** First-aid kits: a rare one takes two levels off; uncommon and rare may survive the use. */
    public static int absorbLevels(GearPiece piece) {
        return piece.getRarity() == GearPiece.Rarity.RARE ? 2 : 1;
    }

    public static double absorbSurvives(GearPiece piece) {
        return piece.getRarity() == GearPiece.Rarity.COMMON ? 0 : 0.5;
    }

    /**
     * What this piece actually does, at its rarity: "Wits +2 · +38% loot · trips 13% shorter".
     *
     * @param words  terminal texts for each kind of effect
     * @param stats  stat key to its display name
     */
    public static String describe(GearDef def, GearPiece piece, Map<String, String> words, Map<String, String> stats) {
        List<String> parts = new ArrayList<>();
        if (def.getStatBonus() != null) {
            for (String stat : def.getStatBonus().keySet()) {
                parts.add(fill(words.get("stat"), stats.getOrDefault(stat, stat), statBonus(def, piece, stat)));
            }
        }
        if (def.getLootMultiplier() != 1.0) {
            parts.add(fill(words.get("loot"), null, (int) Math.round((lootMultiplier(def, piece) - 1) * 100)));
        }
        double d = durationMultiplier(def, piece);
        if (d < 1) {
            parts.add(fill(words.get("faster"), null, (int) Math.round((1 - d) * 100)));
        } else if (d > 1) {
            parts.add(fill(words.get("slower"), null, (int) Math.round((d - 1) * 100)));
        }
        if (def.getNoiseFactor() < 1) {
            parts.add(fill(words.get("noise"), null, (int) Math.round((1 - noiseFactor(def, piece)) * 100)));
        }
        if (def.getStressFactor() < 1) {
            parts.add(fill(words.get("stress"), null, (int) Math.round((1 - stressFactor(def, piece)) * 100)));
        }
        if (def.isAbsorbsInjury()) {
            parts.add(fill(words.get(absorbSurvives(piece) > 0 ? "absorbKeep" : "absorb"), null, absorbLevels(piece)));
        }
        if (def.isRide()) {
            parts.add(fill(words.get("seats"), null, def.getSeats()));
            if (def.getFuelPerHour() > 0) {
                parts.add(fill(words.get("fuel"), null, (int) Math.round(def.getFuelPerHour())));
            }
        }
        return String.join(" · ", parts);
    }

    private static String fill(String template, String stat, int n) {
        if (template == null) {
            return String.valueOf(n);
        }
        return template.replace("{stat}", stat != null ? stat : "").replace("{n}", String.valueOf(n));
    }

    public static double lootMultiplier(GearDef def, GearPiece piece) {
        return 1 + (def.getLootMultiplier() - 1) * piece.getRarity().power;
    }

    public static double noiseFactor(GearDef def, GearPiece piece) {
        return Math.max(0.2, 1 - (1 - def.getNoiseFactor()) * piece.getRarity().power);
    }
}
