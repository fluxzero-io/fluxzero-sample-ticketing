package io.fluxzero.ticketing.access.api.model;

import io.fluxzero.sdk.tracking.handling.authentication.User;
import java.util.Set;

/** Verified identity with roles resolved by the trusted user provider, never from browser input. */
public record TicketingUser(String id, Set<String> roles) implements User {
    public static final TicketingUser SYSTEM = new TicketingUser("$system", Set.of("OPERATOR", "PAYMENTS", "BILLING", "IDENTITY"));
    public TicketingUser { roles = roles == null ? Set.of() : Set.copyOf(roles); }
    @Override public boolean hasRole(String role) { return roles.contains(role); }
}
