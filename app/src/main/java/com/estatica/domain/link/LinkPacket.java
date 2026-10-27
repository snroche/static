package com.estatica.domain.link;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Something one bunker sends another (GDD §12): a message, a supply drop, a wanderer, a last
 * transmission, or a broadcast from the developers. It travels as a code ({@link LinkCodec}),
 * by text, QR or Bluetooth; no server is involved. Only the fields of its type are set.
 */
public final class LinkPacket {

    public enum Type {
        MESSAGE(1), DROP(2), WANDERER(3), LAST_WORDS(4), BROADCAST(5);

        final int code;

        Type(int code) {
            this.code = code;
        }

        static Type fromCode(int code) {
            for (Type t : values()) {
                if (t.code == code) {
                    return t;
                }
            }
            return null;
        }
    }

    /** A piece of gear in a drop. */
    public static final class Gear {
        public final String gearId;
        /** GearPiece.Rarity name. */
        public final String rarity;
        public final int condition;

        public Gear(String gearId, String rarity, int condition) {
            this.gearId = Objects.requireNonNull(gearId);
            this.rarity = Objects.requireNonNull(rarity);
            this.condition = condition;
        }
    }

    private final String id;
    private final Type type;
    /** The sender's callsign ({@link Callsign}). */
    private final String callsign;
    /** What the sender's crew calls them, or "". */
    private final String sender;
    private final long createdMinutes;

    // MESSAGE (text), DROP (a note), WANDERER (a memory), LAST_WORDS, BROADCAST (body).
    String text = "";
    // MESSAGE: a Transmission.Kind name, or PLAIN.
    String scheme = "PLAIN";
    int shift;
    // WANDERER, LAST_WORDS.
    String name = "";
    boolean female;
    final Map<String, Integer> stats = new LinkedHashMap<>();
    final List<String> traits = new ArrayList<>();
    // DROP, BROADCAST.
    final Map<String, Integer> resources = new LinkedHashMap<>();
    final List<Gear> gear = new ArrayList<>();
    // BROADCAST.
    String title = "";
    String lore = "";
    /** Local epoch day after which it no longer airs; 0 for never. */
    int expiresDay;

    LinkPacket(String id, Type type, String callsign, String sender, long createdMinutes) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.callsign = callsign != null ? callsign : "";
        this.sender = sender != null ? sender : "";
        this.createdMinutes = createdMinutes;
    }

    private static LinkPacket create(Type type, String callsign, String sender, long nowMillis) {
        return new LinkPacket(UUID.randomUUID().toString(), type, callsign, sender, nowMillis / 60_000L);
    }

    public static LinkPacket message(String callsign, String sender, long now, String text, String scheme, int shift) {
        LinkPacket p = create(Type.MESSAGE, callsign, sender, now);
        p.text = text;
        p.scheme = scheme;
        p.shift = shift;
        return p;
    }

    public static LinkPacket drop(String callsign, String sender, long now, Map<String, Integer> resources,
                                  List<Gear> gear, String note) {
        LinkPacket p = create(Type.DROP, callsign, sender, now);
        p.resources.putAll(resources);
        p.gear.addAll(gear);
        p.text = note != null ? note : "";
        return p;
    }

    /** A crew member as others will meet them ({@code type} WANDERER or LAST_WORDS). */
    public static LinkPacket person(Type type, String callsign, String sender, long now, String name, boolean female,
                                    Map<String, Integer> stats, List<String> traits, String words) {
        LinkPacket p = create(type, callsign, sender, now);
        p.name = name;
        p.female = female;
        p.stats.putAll(stats);
        p.traits.addAll(traits);
        p.text = words != null ? words : "";
        return p;
    }

    public static LinkPacket broadcast(String id, long now, String title, String text, Map<String, Integer> resources,
                                       String lore, int expiresDay) {
        LinkPacket p = new LinkPacket(id, Type.BROADCAST, "", "", now / 60_000L);
        p.title = title;
        p.text = text;
        p.resources.putAll(resources);
        p.lore = lore != null ? lore : "";
        p.expiresDay = expiresDay;
        return p;
    }

    public String getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public String getCallsign() {
        return callsign;
    }

    public String getSender() {
        return sender;
    }

    public long getCreatedMinutes() {
        return createdMinutes;
    }

    public String getText() {
        return text;
    }

    public String getScheme() {
        return scheme;
    }

    public int getShift() {
        return shift;
    }

    public String getName() {
        return name;
    }

    public boolean isFemale() {
        return female;
    }

    public Map<String, Integer> getStats() {
        return Collections.unmodifiableMap(stats);
    }

    public List<String> getTraits() {
        return Collections.unmodifiableList(traits);
    }

    public Map<String, Integer> getResources() {
        return Collections.unmodifiableMap(resources);
    }

    public List<Gear> getGear() {
        return Collections.unmodifiableList(gear);
    }

    public String getTitle() {
        return title;
    }

    public String getLore() {
        return lore;
    }

    public int getExpiresDay() {
        return expiresDay;
    }
}
