package com.estatica.core;

import java.util.UUID;

/** Globally unique ids (UUID) so state can later be shared between bunkers (GDD §12). */
public final class Ids {

    private Ids() {
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
