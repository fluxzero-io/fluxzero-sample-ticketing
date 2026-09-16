package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.booking.api.model.Ticket;

public final class TicketId extends Id<Ticket> {
    public TicketId(String value) { super(value, "ticket-"); }
}
