package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.booking.TransferRules;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.access.api.model.Person;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import java.time.Duration;
import static io.fluxzero.ticketing.common.Checks.require;

@RequiresUser
public record OfferTicketTransfer(@NotNull TicketId ticketId, @NotBlank @Size(max = 200) String recipientId,
                                   @PositiveOrZero long expectedVersion) {
    @AssertLegal void validate(Ticket ticket, Performance performance, @Nullable TicketTransfer transfer, User user) {
        if (!ticket.customerId().equals(user.id())) throw new UnauthorizedException("Ticket belongs to another customer");
        TransferRules.transferable(ticket, performance);
        require(!recipientId.equals(user.id()), "Choose another recipient");
        require(Fluxzero.loadModel(recipientId, Person.class).get() != null, "Recipient must sign in once before receiving tickets");
        require((transfer == null ? 0 : transfer.version()) == expectedVersion, "Transfer invitation has changed");
        require(transfer == null || !transfer.openAt(Fluxzero.currentTime()), "Cancel the current invitation first");
    }
    @Apply TicketTransfer apply(Ticket ticket, Performance performance, @Nullable TicketTransfer transfer) {
        var expires = Fluxzero.currentTime().plus(Duration.ofHours(24));
        if (performance.details().startsAt().isBefore(expires)) expires = performance.details().startsAt();
        return new TicketTransfer(ticketId, Math.incrementExact(expectedVersion), ticket.customerId(), recipientId,
                expires, TicketTransfer.Status.OFFERED);
    }
}
