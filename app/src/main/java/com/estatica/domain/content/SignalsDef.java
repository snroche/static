package com.estatica.domain.content;

import java.util.List;
import java.util.Map;

/**
 * What can be heard on the dial ({@code signals.json}): teaser texts per signal kind,
 * rare finds and encrypted transmissions. Filled by Gson.
 */
public final class SignalsDef {

    /** Signal kind to fragments heard while tuning in ({zone}, {name} placeholders). */
    private Map<String, List<String>> teasers;
    private List<Rare> rare;
    private List<Transmission> transmissions;

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
}
