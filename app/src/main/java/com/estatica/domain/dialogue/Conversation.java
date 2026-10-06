package com.estatica.domain.dialogue;

import com.estatica.core.Ids;

import java.util.Objects;

/** A conversation waiting for the operator's answer, or already finished. */
public final class Conversation {

    public enum Status { PENDING, ANSWERED, EXPIRED }

    private final String id;
    private final String scoutId;
    private final String dialogueId;
    private final long startedAtMillis;
    /** Started by the scout ("got a minute?"); these expire if ignored. */
    private final boolean initiated;
    private Status status;

    public Conversation(String id, String scoutId, String dialogueId, long startedAtMillis,
                        boolean initiated, Status status) {
        this.id = Objects.requireNonNull(id);
        this.scoutId = Objects.requireNonNull(scoutId);
        this.dialogueId = Objects.requireNonNull(dialogueId);
        this.startedAtMillis = startedAtMillis;
        this.initiated = initiated;
        this.status = Objects.requireNonNull(status);
    }

    public static Conversation start(String scoutId, String dialogueId, long now, boolean initiated) {
        return new Conversation(Ids.newId(), scoutId, dialogueId, now, initiated, Status.PENDING);
    }

    public String getId() {
        return id;
    }

    public String getScoutId() {
        return scoutId;
    }

    public String getDialogueId() {
        return dialogueId;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public boolean isInitiated() {
        return initiated;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
