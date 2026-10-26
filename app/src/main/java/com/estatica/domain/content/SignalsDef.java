package com.estatica.domain.content;

import java.util.List;
import java.util.Map;

/**
 * What can be heard on the dial ({@code signals.json}): teaser texts per signal kind,
 * rare finds and encrypted transmissions. Filled by Gson.
 */
public final class SignalsDef {

    /**
     * Signal kind to fragments heard while tuning in. Transmissions also have one entry per
     * scheme ("TRANSMISSION:MORSE"), so what you hear matches what you get.
     */
    private Map<String, List<String>> teasers;
    private List<Rare> rare;
    private List<Transmission> transmissions;
    private List<Station> stations;
    /** Echoes of the fallen on the dial: what they say ({name}, {words}). */
    private Echo echo;

    public List<Station> getStations() {
        return stations != null ? stations : List.of();
    }

    public Echo getEcho() {
        return echo;
    }

    /** Teasers for a kind, or for a kind and scheme with a fallback to the kind alone. */
    public List<String> teasers(String kind, String scheme) {
        if (scheme != null) {
            List<String> specific = teasers.get(kind + ":" + scheme);
            if (specific != null && !specific.isEmpty()) {
                return specific;
            }
        }
        return teasers.get(kind);
    }

    public Map<String, List<String>> getTeasers() {
        return teasers;
    }

    public List<Rare> getRare() {
        return rare;
    }

    public List<Transmission> getTransmissions() {
        return transmissions;
    }

    /** A strange find on the dial: a line of text and maybe some resources. */
    public static final class Rare {
        private String id;
        private String band;
        private List<String> text;
        private Map<String, Integer> resources;
        /** Nullable lore fragment kept in the Diary. */
        private String lore;
        /** What it sounds like while tuning in; null for the generic teasers. */
        private String teaser;
        /** Sound while tuned (Audio name); null for a voice. */
        private String audio;

        public String getTeaser() {
            return teaser;
        }

        public String getAudio() {
            return audio != null ? audio : "VOICE";
        }

        /** Nothing to keep: no supplies, no lore. Free to listen to. */
        public boolean isFlavor() {
            return (resources == null || resources.isEmpty()) && lore == null;
        }

        public String getId() {
            return id;
        }

        /** Band id, or null for any. */
        public String getBand() {
            return band;
        }

        public List<String> getText() {
            return text;
        }

        public Map<String, Integer> getResources() {
            return resources != null ? resources : Map.of();
        }

        public String getLore() {
            return lore;
        }
    }

    /** An encrypted message. Plain text uses only A–Z and spaces once normalized. */
    public static final class Transmission {
        public enum Reward { CACHE, DANGER, LORE, RESOURCES, MORSE_TABLE, PLOT }

        private String id;
        private String band;
        private int difficulty = 1;
        /** Plain text; {zone} is replaced by the target zone name. */
        private String text;
        private Reward reward;
        private Map<String, Integer> resources;
        private String lore;
        /** PLOT: fragment id. */
        private String plot;
        /** Only offered once (tutorial, lore). */
        private boolean unique;
        /** Scheme to use (Transmission.Kind name); null = Caesar or Morse at random. */
        private String kind;
        /** Vigenère/Playfair keyword, or the pad key item id (Numbers). */
        private String key;
        /** What the operator can work the key out from ("Net KESTREL", "the thing everyone fears"). */
        private String keyHint;
        /** Only heard at this dial position or higher on its band. */
        private int minPosition;

        public String getKind() {
            return kind;
        }

        public String getKey() {
            return key;
        }

        public String getKeyHint() {
            return keyHint;
        }

        public int getMinPosition() {
            return minPosition;
        }

        public String getId() {
            return id;
        }

        public String getBand() {
            return band;
        }

        public int getDifficulty() {
            return difficulty;
        }

        public String getText() {
            return text;
        }

        /** Other ways the same message comes through (same meaning, other words). */
        private List<String> variants;

        /** One of the wordings, picked by {@code pick} (any number; stable for the same value). */
        public String text(long pick) {
            if (variants == null || variants.isEmpty()) {
                return text;
            }
            int i = (int) Math.floorMod(pick, (long) variants.size() + 1);
            return i == 0 ? text : variants.get(i - 1);
        }

        public List<String> getVariants() {
            return variants != null ? variants : List.of();
        }

        public Reward getReward() {
            return reward;
        }

        public Map<String, Integer> getResources() {
            return resources != null ? resources : Map.of();
        }

        public String getLore() {
            return lore;
        }

        public boolean isUnique() {
            return unique;
        }

        public String getPlot() {
            return plot;
        }

        /** Needs a target zone ({zone} in text, CACHE/DANGER rewards). */
        public boolean needsZone() {
            return reward == Reward.CACHE || reward == Reward.DANGER || (text != null && text.contains("{zone}"));
        }
    }

    /**
     * A fixed channel: always at the same frequency, free to listen to, saying something new
     * every few minutes (weather loops, a ham in his garden, things that breathe).
     */
    public static final class Station {
        private String id;
        private String band;
        private int position;
        private String name;
        /** Sound while tuned (Audio name). */
        private String audio;
        /** Only on the air at night. */
        private boolean night;
        private int minutesPerLine = 20;
        private List<String> lines;
        /** Used instead when someone has fallen ({dead}). Nullable. */
        private List<String> deadLines;
        /** Kept in the Diary the first time you listen. Nullable. */
        private String lore;
        /** Stress for everyone at home when you lock on (creepy ones). */
        private int stress;
        /** When it's on the air; null for always. */
        private Schedule schedule;
        /** A new line per airing instead of turning every few minutes. */
        private boolean perAiring;
        /** What each airing leaves to whoever hears it (you, or the radio assistant). */
        private Map<String, Integer> resources;
        /** CACHE or DANGER intel on a known place, per airing. Nullable. */
        private String intel;

        public Schedule getSchedule() {
            return schedule;
        }

        public boolean isPerAiring() {
            return perAiring;
        }

        public Map<String, Integer> getResources() {
            return resources != null ? resources : Map.of();
        }

        public String getIntel() {
            return intel;
        }

        /** Something to catch each time it airs. */
        public boolean hasBulletin() {
            return schedule != null && (!getResources().isEmpty() || intel != null);
        }

        public String getId() {
            return id;
        }

        public String getBand() {
            return band;
        }

        public int getPosition() {
            return position;
        }

        public String getName() {
            return name;
        }

        public String getAudio() {
            return audio != null ? audio : "VOICE";
        }

        public boolean isNight() {
            return night;
        }

        public int getMinutesPerLine() {
            return Math.max(1, minutesPerLine);
        }

        public List<String> getLines() {
            return lines != null ? lines : List.of();
        }

        public List<String> getDeadLines() {
            return deadLines != null ? deadLines : List.of();
        }

        public String getLore() {
            return lore;
        }

        public int getStress() {
            return stress;
        }
    }

    /** Every {@code everyHours} from {@code atHour} (local time), for {@code minutes}. */
    public static final class Schedule {
        private double everyHours = 24;
        private double atHour;
        private int minutes = 60;

        public double getEveryHours() {
            return everyHours;
        }

        public double getAtHour() {
            return atHour;
        }

        public int getMinutes() {
            return minutes;
        }
    }

    /** What the fallen sound like when they turn up on the dial. */
    public static final class Echo {
        /** Hours after a death before the echo can be heard. */
        private double afterHours = 6;
        /** One window in this many carries each echo. */
        private int everyWindows = 3;
        private List<String> teasers;
        /** Logged when you lock on: {name}, {words}. */
        private List<String> heard;
        /** Instead of {@code heard}, for someone who was the operator's partner. */
        private List<String> heardPartner;

        public List<String> getHeardPartner() {
            return heardPartner != null ? heardPartner : List.of();
        }
        /** Diary entry the first time: {name}, {words}. */
        private String lore;
        private int stress;

        public double getAfterHours() {
            return afterHours;
        }

        public int getEveryWindows() {
            return Math.max(1, everyWindows);
        }

        public List<String> getTeasers() {
            return teasers != null ? teasers : List.of();
        }

        public List<String> getHeard() {
            return heard != null ? heard : List.of();
        }

        public String getLore() {
            return lore;
        }

        public int getStress() {
            return stress;
        }
    }
}
