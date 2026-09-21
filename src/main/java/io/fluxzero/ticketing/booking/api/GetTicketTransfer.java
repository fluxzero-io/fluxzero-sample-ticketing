package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.*;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.booking.api.model.TicketTransfer;
import jakarta.validation.constraints.NotNull;
import static io.fluxzero.ticketing.common.Checks.require;

@RequiresUser
public record GetTicketTransfer(@NotNull TicketId ticketId) implements Request<TicketTransfer> {
    @HandleQuery TicketTransfer handle(User user) {
        var ticket = Fluxzero.loadModel(ticketId).get();
        require(ticket != null, "Unknown ticket");
        var transfer = Fluxzero.loadModel(ticketId, TicketTransfer.class).get();
        if (!ticket.customerId().equals(user.id()) && (transfer == null || !transfer.recipientId().equals(user.id())))
            throw new UnauthorizedException("Ticket belongs to another customer");
        return transfer;
    }
}
