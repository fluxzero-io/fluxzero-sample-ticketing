package io.fluxzero.ticketing.access.api.model;

import io.fluxzero.sdk.tracking.handling.authentication.User;

/** Browser subjects are customers; only trusted internal work has operational roles. */
public record TicketingUser(String id) implements User {
    public static final TicketingUser SYSTEM = new TicketingUser("$system");
    @Override public boolean hasRole(String role) {
        return equals(SYSTEM) && java.util.Set.of("OPERATOR", "PAYMENTS", "BILLING").contains(role);
    }
}
