package com.estatica.domain.bunker;

import com.estatica.core.GameRandom;
import com.estatica.domain.content.Balance;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.GearDef;
import com.estatica.domain.content.RecipeDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * THE FAIR, the trader on the radio (pure rules). Prices are in chips. Each day, from the
 * game seed: two goods it wants (pays more for them), one it's short of (charges more), a
 * couple of blueprints and pieces of gear on offer. Standing with the trader moves every
 * price a little; selling a lot of one thing in a day drives its price down.
 */
public final class MarketRules {

    /** What's for sale today besides raw goods. */
    public static final class Offer {
        public enum Kind { BLUEPRINT, GEAR }

        public final Kind kind;
        /** Recipe id (blueprint) or gear id. */
        public final String id;
        /** Gear only. */
        public final GearPiece.Rarity rarity;
        public final int price;

        Offer(Kind kind, String id, GearPiece.Rarity rarity, int price) {
            this.kind = kind;
            this.id = id;
            this.rarity = rarity;
            this.price = price;
        }

        /** Stable key for "already bought today". */
        public String key() {
            return kind.name() + ":" + id + (rarity != null ? ":" + rarity.name() : "");
        }
    }

    /** One day at the trader. */
    public static final class Day {
        /** Goods it pays extra for today. */
        public final Set<String> wanted;
        /** Goods it's short of today (costs extra). */
        public final Set<String> scarce;
        public final List<Offer> offers;

        Day(Set<String> wanted, Set<String> scarce, List<Offer> offers) {
            this.wanted = Collections.unmodifiableSet(wanted);
            this.scarce = Collections.unmodifiableSet(scarce);
            this.offers = Collections.unmodifiableList(offers);
        }
    }

    private static final long SALT = 0x5EEDFE11AL;

    private final GameContent content;
    private final Balance.Market market;
    private final long seed;

    public MarketRules(GameContent content, long seed) {
        this.content = content;
        this.market = content.getBalance().getMarket();
        this.seed = seed;
    }

    /**
     * @param day        days since the epoch (local)
     * @param unknown    blueprints the bunker doesn't have yet
     * @param reputation 0–100
     */
    public Day day(long day, Predicate<RecipeDef> unknown, int reputation) {
        GameRandom rng = GameRandom.fromSeed(seed ^ SALT ^ day * 0x9E3779B97F4A7C15L);
        List<String> goods = new ArrayList<>(market.getGoods().keySet());
        Collections.sort(goods);
        shuffle(goods, rng);
        Set<String> wanted = new LinkedHashSet<>();
        Set<String> scarce = new LinkedHashSet<>();
        for (String g : goods) {
            if (wanted.size() < market.getWantedPerDay()) {
                wanted.add(g);
            } else if (scarce.size() < market.getScarcePerDay()) {
                scarce.add(g);
            }
        }
        List<Offer> offers = new ArrayList<>();
        List<RecipeDef> plans = new ArrayList<>();
        for (RecipeDef r : content.getRecipes().values()) {
            if (unknown.test(r)) {
                plans.add(r);
            }
        }
        plans.sort((a, b) -> a.getId().compareTo(b.getId()));
        shuffle(plans, rng);
        for (int i = 0; i < Math.min(market.getBlueprintsPerDay(), plans.size()); i++) {
            RecipeDef r = plans.get(i);
            offers.add(new Offer(Offer.Kind.BLUEPRINT, r.getId(), null, blueprintPrice(r)));
        }
        List<String> gear = new ArrayList<>();
        for (GearDef g : content.getGear().values()) {
            if (g.isFindable()) {
                gear.add(g.getId());
            }
        }
        Collections.sort(gear);
        shuffle(gear, rng);
        boolean rareStock = reputation >= market.getRareStockReputation();
        for (int i = 0; i < Math.min(market.getGearPerDay(), gear.size()); i++) {
            GearPiece.Rarity rarity = rng.chance(rareStock ? 0.5 : 0.25)
                    ? (rareStock && rng.chance(0.5) ? GearPiece.Rarity.RARE : GearPiece.Rarity.UNCOMMON)
                    : GearPiece.Rarity.COMMON;
            int price = market.getGearBuy().getOrDefault(rarity.name(), 20);
            offers.add(new Offer(Offer.Kind.GEAR, gear.get(i), rarity, price));
        }
        return new Day(wanted, scarce, offers);
    }

    /** Chips for a plan: harder things cost more to learn. */
    public int blueprintPrice(RecipeDef r) {
        return market.getBlueprintBase() + market.getBlueprintPerCraft() * r.getMinCraft();
    }

    /** What one unit costs today, before any courier fee. */
    public int buyPrice(String good, Day day, int reputation) {
        int[] p = market.getGoods().get(good);
        if (p == null) {
            return -1;
        }
        double price = p[0] * (day.scarce.contains(good) ? market.getScarceMultiplier() : 1.0);
        return Math.max(1, (int) Math.round(price * standing(reputation, true)));
    }

    /** What the trader pays for one more unit, given how many were sold to them today already. */
    public int sellPrice(String good, Day day, int reputation, int soldToday) {
        int[] p = market.getGoods().get(good);
        if (p == null) {
            return -1;
        }
        double price = p[1] * (day.wanted.contains(good) ? market.getWantedMultiplier() : 1.0);
        double glut = Math.max(market.getSaturationFloor(), 1 - soldToday * market.getSaturationPerUnit());
        return Math.max(1, (int) Math.round(price * glut * standing(reputation, false)));
    }

    /** Total for selling {@code qty} in one go (each unit a little cheaper than the last). */
    public int sellTotal(String good, Day day, int reputation, int soldToday, int qty) {
        int total = 0;
        for (int i = 0; i < qty; i++) {
            total += sellPrice(good, day, reputation, soldToday + i);
        }
        return total;
    }

    /** What the trader pays for a piece of gear. Worn pieces fetch less. */
    public int gearSellPrice(GearPiece piece, int reputation) {
        int base = market.getGearSell().getOrDefault(piece.getRarity().name(), 6);
        GearDef def = content.gear(piece.getGearId());
        int scale = def != null ? def.getRepairScale() : 1;
        double condition = Math.max(0.2, piece.getCondition() / 100.0);
        return Math.max(1, (int) Math.round(base * scale * condition * standing(reputation, false)));
    }

    /** The courier's cut on top of a purchase. */
    public int courierFee(int price) {
        return (int) Math.ceil(price * market.getCourierFee());
    }

    /** Good standing: buy a little cheaper and sell a little dearer (and the reverse). */
    private double standing(int reputation, boolean buying) {
        double swing = (reputation - 50) / 50.0 * market.getReputationSwing();
        return buying ? 1 - swing : 1 + swing;
    }

    /** Reputation after a trade. */
    public int afterTrade(int reputation) {
        return Math.min(100, reputation + market.getReputationPerTrade());
    }

    public Map<String, int[]> goods() {
        return market.getGoods();
    }

    private static <T> void shuffle(List<T> list, GameRandom rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            Collections.swap(list, i, rng.nextInt(i + 1));
        }
    }
}
