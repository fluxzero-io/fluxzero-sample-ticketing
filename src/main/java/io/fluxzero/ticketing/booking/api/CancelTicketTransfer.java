package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.booking.api.model.TicketTransfer;
import jakarta.validation.constraints.*;
import static io.fluxzero.ticketing.common.Checks.require;

@RequiresUser
public record CancelTicketTransfer(@NotNull TicketId ticketId, @Positive long version) {
    @AssertLegal void validate(TicketTransfer transfer, User user) {
        if (!transfer.senderId().equals(user.id()) && !transfer.recipientId().equals(user.id()))
            throw new UnauthorizedException("Transfer belongs to another customer");
        require(transfer.version() == version, "Transfer invitation has changed");
        require(transfer.status() != TicketTransfer.Status.ACCEPTED, "Transfer already accepted");
    }
    @Apply TicketTransfer apply(TicketTransfer transfer) { return transfer.withStatus(TicketTransfer.Status.CANCELLED); }
}
