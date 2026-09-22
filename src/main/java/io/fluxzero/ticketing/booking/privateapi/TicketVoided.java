package io.fluxzero.ticketing.booking.privateapi;

import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.booking.api.model.*;

public record TicketVoided(TicketId ticketId) {
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) Ticket apply(Ticket ticket) {
        return ticket.withStatus(TicketStatus.VOID);
    }
}
