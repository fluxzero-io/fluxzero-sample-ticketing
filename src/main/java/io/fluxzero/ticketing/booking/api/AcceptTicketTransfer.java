package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.booking.TransferRules;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import jakarta.validation.constraints.*;
import static io.fluxzero.ticketing.common.Checks.require;

@RequiresUser
public record AcceptTicketTransfer(@NotNull TicketId ticketId, @Positive long version) {
    @AssertLegal void validate(TicketTransfer transfer, Ticket ticket, Performance performance, User user) {
        if (!transfer.recipientId().equals(user.id())) throw new UnauthorizedException("Transfer belongs to another recipient");
        require(transfer.version() == version, "Transfer invitation has changed");
        if (transfer.status() == TicketTransfer.Status.ACCEPTED) return;
        require(transfer.openAt(Fluxzero.currentTime()) && transfer.senderId().equals(ticket.customerId()), "Transfer invitation is closed");
        TransferRules.transferable(ticket, performance);
    }
    @Apply TicketTransfer transfer(TicketTransfer transfer) { return transfer.withStatus(TicketTransfer.Status.ACCEPTED); }
    @Apply Ticket ticket(Ticket ticket, TicketTransfer transfer) {
        return ticket.customerId().equals(transfer.recipientId()) ? ticket
                : ticket.withCustomerId(transfer.recipientId()).withCredentialVersion(Math.incrementExact(ticket.credentialVersion()));
    }
}
