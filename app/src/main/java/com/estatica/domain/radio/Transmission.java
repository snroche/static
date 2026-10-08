package com.estatica.domain.radio;

import com.estatica.core.Ids;

import java.util.Objects;

/** An encrypted message caught on the dial, waiting to be decoded (GDD §6.2). */
public final class Transmission {

    public enum Kind {
        CAESAR, MORSE, ATBASH, TAP, VIGENERE, BASE64, PLAYFAIR, NUMBERS, BOOK;

        /** Saved kinds from newer versions fall back to Caesar instead of crashing. */
        public static Kind fromName(String name) {
            for (Kind k : values()) {
                if (k.name().equals(name)) {
                    return k;
                }
            }
            return CAESAR;
        }
    }

    public enum Status { PENDING, SOLVED }

    private final String id;
    /** Content id in signals.json. */
    private final String sourceId;
    private final Kind kind;
    /** Normalized plain text. */
    private final String plainText;
    /** Caesar shift used to encrypt (0 for Morse). */
    private final int shift;
    private final String bandId;
    /** Zone the intel is about, or null. */
    private final String zoneId;
    private final long receivedAtMillis;
    private Status status;
    /**
     * Vigenère/Playfair: the keyword. Numbers/Book: the cipher text itself (it depends on a pad
     * page or a book the content holds). Null for the rest.
     */
    private final String key;

    public Transmission(String id, String sourceId, Kind kind, String plainText, int shift,
                        String bandId, String zoneId, long receivedAtMillis, Status status) {
        this(id, sourceId, kind, plainText, shift, bandId, zoneId, receivedAtMillis, status, null);
    }

    public Transmission(String id, String sourceId, Kind kind, String plainText, int shift,
                        String bandId, String zoneId, long receivedAtMillis, Status status, String key) {
        this.key = key;
        this.id = Objects.requireNonNull(id);
        this.sourceId = Objects.requireNonNull(sourceId);
        this.kind = Objects.requireNonNull(kind);
        this.plainText = Objects.requireNonNull(plainText);
        this.shift = shift;
        this.bandId = bandId;
        this.zoneId = zoneId;
        this.receivedAtMillis = receivedAtMillis;
        this.status = Objects.requireNonNull(status);
    }

    public static Transmission create(String sourceId, Kind kind, String plainText, int shift,
                                      String bandId, String zoneId, long now) {
        return create(sourceId, kind, plainText, shift, bandId, zoneId, now, null);
    }

    public static Transmission create(String sourceId, Kind kind, String plainText, int shift,
                                      String bandId, String zoneId, long now, String key) {
        return new Transmission(Ids.newId(), sourceId, kind, Cipher.normalize(plainText), shift,
                bandId, zoneId, now, Status.PENDING, key);
    }

    /** What the operator sees. */
    public String cipherText() {
        switch (kind) {
            case MORSE:
                return Cipher.toMorse(plainText);
            case ATBASH:
                return Ciphers.atbash(plainText);
            case TAP:
                return Ciphers.tap(plainText);
            case VIGENERE:
                return Ciphers.vigenere(plainText, key != null ? key : "", false);
            case BASE64:
                return Ciphers.base64(plainText);
            case PLAYFAIR:
                return Ciphers.toNato(Ciphers.playfair(plainText, key != null ? key : "", false));
            case NUMBERS:
            case BOOK:
                return key != null ? key : plainText;
            default:
                return Cipher.caesar(plainText, shift);
        }
    }

    public boolean check(String answer) {
        if (kind == Kind.PLAYFAIR) {
            // Playfair drops spaces and pads with X: compare letters only, Xs aside.
            return lettersNoX(answer).equals(lettersNoX(plainText));
        }
        return Cipher.matches(answer, plainText);
    }

    private static String lettersNoX(String text) {
        return Cipher.normalize(text).replace(" ", "").replace("J", "I").replace("X", "");
    }

    public String getKey() {
        return key;
    }

    public String getId() {
        return id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getPlainText() {
        return plainText;
    }

    public int getShift() {
        return shift;
    }

    public String getBandId() {
        return bandId;
    }

    public String getZoneId() {
        return zoneId;
    }

    public long getReceivedAtMillis() {
        return receivedAtMillis;
    }

    public Status getStatus() {
        return status;
    }

    public void markSolved() {
        status = Status.SOLVED;
    }
}
