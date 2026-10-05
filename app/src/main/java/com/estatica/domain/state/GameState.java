package com.estatica.domain.state;

/** Global save state of one operator's game. */
public final class GameState {

    private final String operatorId;
    private final long rngSeed;
    private final long rngState;
    private final long createdAtMillis;
    private final long lastSeenAtMillis;
    private final boolean newGame;

    public GameState(String operatorId, long rngSeed, long rngState, long createdAtMillis,
                     long lastSeenAtMillis, boolean newGame) {
        this.operatorId = operatorId;
        this.rngSeed = rngSeed;
        this.rngState = rngState;
        this.createdAtMillis = createdAtMillis;
        this.lastSeenAtMillis = lastSeenAtMillis;
        this.newGame = newGame;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public long getRngSeed() {
        return rngSeed;
    }

    public long getRngState() {
        return rngState;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    /** Last time the operator had the app open; base for catch-up. */
    public long getLastSeenAtMillis() {
        return lastSeenAtMillis;
    }

    /** True only for the session in which the save was created. */
    public boolean isNewGame() {
        return newGame;
    }
}
