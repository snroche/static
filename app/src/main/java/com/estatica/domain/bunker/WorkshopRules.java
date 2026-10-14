package com.estatica.domain.bunker;

import com.estatica.domain.content.Balance;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.GearDef;
import com.estatica.domain.content.RecipeDef;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The workshop after the rework: nothing gets made without someone at the bench, and how good
 * they are (Craft) sets the speed, the quality, what can be made at all, and what can be
 * upgraded. Also salvage: breaking unwanted gear back into materials.
 */
public final class WorkshopRules {

    private final GameContent content;
    private final Balance.Workshop balance;

    public WorkshopRules(GameContent content) {
        this.content = content;
        this.balance = content.getBalance().getWorkshop();
    }

    /** Work speed multiplier for the best Craft at the bench; 0 with nobody there. */
    public double speed(int bestCraft) {
        return bestCraft <= 0 ? 0 : balance.getSpeedBase() + balance.getSpeedPerCraft() * bestCraft;
    }

    /** Whether the bench has someone good enough for this recipe. */
    public boolean canMake(RecipeDef recipe, int bestCraft) {
        return bestCraft > 0 && bestCraft >= recipe.getMinCraft();
    }

    /** How many jobs fit: one per bench, plus a short line waiting. */
    public int capacity(int benches) {
        return benches + balance.getQueue();
    }

    /** Materials back from a piece: by rarity, more for bigger things, less if it's worn out. */
    public Map<String, Integer> salvage(GearPiece piece) {
        Map<String, Integer> base = balance.salvage(piece.getRarity().name());
        GearDef def = content.gear(piece.getGearId());
        int scale = def != null ? def.getRepairScale() : 1;
        double condition = piece.isBroken() ? 0.5 : 1.0;
        Map<String, Integer> out = new LinkedHashMap<>();
        base.forEach((k, v) -> {
            int n = (int) Math.floor(v * scale * condition);
            if (n > 0) {
                out.put(k, n);
            }
        });
        return out;
    }

    /** Craft needed to raise this piece one rarity, or -1 if it's already the best. */
    public int upgradeCraft(GearPiece piece) {
        return balance.upgradeCraft(piece.getRarity().ordinal());
    }

    /** What raising this piece one rarity costs, or an empty map if it can't go higher. */
    public Map<String, Integer> upgradeCost(GearPiece piece) {
        if (upgradeCraft(piece) < 0) {
            return Map.of();
        }
        GearDef def = content.gear(piece.getGearId());
        int scale = (def != null ? def.getRepairScale() : 1) * (piece.getRarity().ordinal() + 1);
        Map<String, Integer> out = new LinkedHashMap<>();
        balance.getUpgradeCost().forEach((k, v) -> out.put(k, v * scale));
        return out;
    }
}
