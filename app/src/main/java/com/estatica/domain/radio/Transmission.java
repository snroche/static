package com.estatica.domain.radio;

import com.estatica.core.Ids;

import java.util.Objects;

/** An encrypted message caught on the dial, waiting to be decoded (GDD §6.2). */
public final class Transmission {

    public enum Kind { CAESAR, MORSE }

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

    public Transmission(String id, String sourceId, Kind kind, String plainText, int shift,
                        String bandId, String zoneId, long receivedAtMillis, Status status) {
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
        return new Transmission(Ids.newId(), sourceId, kind, Cipher.normalize(plainText), shift,
                bandId, zoneId, now, Status.PENDING);
    }

    /** What the operator sees: shifted letters or dots and dashes. */
    public String cipherText() {
        return kind == Kind.CAESAR ? Cipher.caesar(plainText, shift) : Cipher.toMorse(plainText);
    }

    public boolean check(String answer) {
        return Cipher.matches(answer, plainText);
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
