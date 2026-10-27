package com.estatica.domain.link;

import com.estatica.domain.content.Balance;
import com.estatica.domain.content.GameContent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What another bunker may send and what arrives (GDD §12). Codes can be forged by anyone who
 * reads this file, so rewards stay modest and everything received is trimmed to the limits in
 * {@code balance.json → link} before it touches the save.
 */
public final class LinkRules {

    /** A message sent in the clear. */
    public static final String PLAIN = "PLAIN";
    /** Schemes a message can be sent in, easiest first: the ones that need no content (keys, pads, books). */
    public static final List<String> MESSAGE_SCHEMES = List.of(PLAIN, "CAESAR", "MORSE", "ATBASH", "TAP", "BASE64");

    private final GameContent content;
    private final Balance.Link balance;

    public LinkRules(GameContent content) {
        this.content = content;
        this.balance = content.getBalance().getLink();
    }

    public Balance.Link balance() {
        return balance;
    }

    /** Total units in a drop. */
    public static int units(Map<String, Integer> resources) {
        int n = 0;
        for (int v : resources.values()) {
            n += Math.max(0, v);
        }
        return n;
    }

    /** Whether a drop can be sent: something in it, within the limits. */
    public boolean dropAllowed(Map<String, Integer> resources, int gearPieces) {
        int units = units(resources);
        for (String id : resources.keySet()) {
            if (!balance.getDropResources().contains(id)) {
                return false;
            }
        }
        return (units > 0 || gearPieces > 0) && units <= balance.getDropMaxUnits()
                && gearPieces <= balance.getDropMaxGear();
    }

    /**
     * A received packet with everything out of bounds trimmed away: unknown resources, gear and
     * traits dropped, amounts and texts cut to the limits. Null if nothing usable is left.
     */
    public LinkPacket sanitize(LinkPacket in) {
        LinkPacket out = new LinkPacket(in.getId(), in.getType(), cut(in.getCallsign(), 8), cut(in.getSender(), 16),
                in.getCreatedMinutes());
        switch (in.getType()) {
            case MESSAGE:
                out.scheme = MESSAGE_SCHEMES.contains(in.scheme) ? in.scheme : PLAIN;
                out.shift = Math.floorMod(in.shift, 26);
                out.text = cut(in.text, balance.getMessageMaxChars());
                return out.text.isBlank() ? null : out;
            case DROP: {
                out.resources.putAll(capped(in.resources, balance.getDropMaxUnits()));
                for (LinkPacket.Gear g : in.gear) {
                    if (out.gear.size() < balance.getDropMaxGear() && content.getGear().containsKey(g.gearId)) {
                        out.gear.add(new LinkPacket.Gear(g.gearId, rarity(g.rarity), Math.max(1, Math.min(100, g.condition))));
                    }
                }
                out.text = cut(in.text, balance.getNoteMaxChars());
                return out.resources.isEmpty() && out.gear.isEmpty() ? null : out;
            }
            case WANDERER:
            case LAST_WORDS: {
                out.name = cut(in.name, 16);
                out.female = in.female;
                for (Map.Entry<String, Integer> e : in.stats.entrySet()) {
                    if (com.estatica.domain.scouts.StatType.fromKey(e.getKey()) != null) {
                        out.stats.put(e.getKey(), Math.max(0, Math.min(20, e.getValue())));
                    }
                }
                for (String t : in.traits) {
                    if (out.traits.size() < 3 && content.getTraits().containsKey(t)) {
                        out.traits.add(t);
                    }
                }
                out.text = cut(in.text, balance.getWordsMaxChars());
                return out.name.isBlank() ? null : out;
            }
            case BROADCAST:
                // Signed by us: kept whole, apart from resources nobody knows.
                out.title = in.title;
                out.text = in.text;
                for (Map.Entry<String, Integer> e : in.resources.entrySet()) {
                    if (content.getResources().containsKey(e.getKey()) && e.getValue() > 0) {
                        out.resources.put(e.getKey(), e.getValue());
                    }
                }
                out.lore = in.lore;
                out.expiresDay = in.expiresDay;
                return out;
            default:
                return null;
        }
    }

    /** Known drop resources, the total cut to {@code max} in the order they came. */
    private Map<String, Integer> capped(Map<String, Integer> resources, int max) {
        Map<String, Integer> out = new LinkedHashMap<>();
        int left = max;
        for (Map.Entry<String, Integer> e : resources.entrySet()) {
            int v = Math.min(left, e.getValue());
            if (v > 0 && balance.getDropResources().contains(e.getKey())) {
                out.merge(e.getKey(), v, Integer::sum);
                left -= v;
            }
        }
        return out;
    }

    private static String rarity(String name) {
        for (String r : new String[]{"COMMON", "UNCOMMON", "RARE"}) {
            if (r.equals(name)) {
                return r;
            }
        }
        return "COMMON";
    }

    static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        String clean = s.replaceAll("[\\p{Cntrl}&&[^\n]]", "").trim();
        return clean.length() > max ? clean.substring(0, max).trim() : clean;
    }

    /**
     * What decoding a message from another bunker is worth: up to the amounts for its scheme
     * (harder schemes, more), each rolled from 1. Nothing for plain messages.
     */
    public Map<String, Integer> decodeReward(String scheme, com.estatica.core.GameRandom rng) {
        Map<String, Integer> max = balance.getDecodeRewards().get(scheme);
        Map<String, Integer> out = new java.util.TreeMap<>();
        if (max == null) {
            return out;
        }
        for (Map.Entry<String, Integer> e : new java.util.TreeMap<>(max).entrySet()) {
            if (e.getValue() > 0) {
                out.put(e.getKey(), rng.nextIntInclusive(1, e.getValue()));
            }
        }
        return out;
    }

    /** A wanderer's strongest stat (first in stat order on ties), or null. */
    public static String bestStat(LinkPacket wanderer) {
        String best = null;
        int top = Integer.MIN_VALUE;
        for (com.estatica.domain.scouts.StatType t : com.estatica.domain.scouts.StatType.values()) {
            Integer v = wanderer.getStats().get(t.key());
            if (v != null && v > top) {
                top = v;
                best = t.key();
            }
        }
        return best;
    }

    /** What a wanderer leaves the team they meet, by their strongest stat. */
    public Map<String, Integer> wandererGift(LinkPacket wanderer) {
        String best = bestStat(wanderer);
        Map<String, Integer> gift = best != null ? balance.getWanderer().getGifts().get(best) : null;
        return gift != null ? gift : Map.of();
    }

    /** "CACHE" or "DANGER" if the wanderer's strongest stat points the way, or null. */
    public String wandererIntel(LinkPacket wanderer) {
        String best = bestStat(wanderer);
        return best != null ? balance.getWanderer().getIntel().get(best) : null;
    }

    /**
     * Where a wanderer waits: the farthest of the places the team could reach (by band order,
     * then id, so the same code lands the same way). Null when nowhere is known.
     */
    public static String waitingZone(List<String> reachable, Map<String, Integer> bandIndex) {
        List<String> sorted = new ArrayList<>(reachable);
        sorted.sort((a, b) -> {
            int c = Integer.compare(bandIndex.getOrDefault(b, 0), bandIndex.getOrDefault(a, 0));
            return c != 0 ? c : a.compareTo(b);
        });
        return sorted.isEmpty() ? null : sorted.get(0);
    }
}
