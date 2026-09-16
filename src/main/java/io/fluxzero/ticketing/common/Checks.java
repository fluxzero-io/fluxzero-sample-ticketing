package io.fluxzero.ticketing.common;

import io.fluxzero.sdk.tracking.handling.IllegalCommandException;

/** Shared command precondition, without domain-specific policy. */
public final class Checks {
    private Checks() {}
    public static void require(boolean allowed, String reason) {
        if (!allowed) throw new IllegalCommandException(reason);
    }
}
