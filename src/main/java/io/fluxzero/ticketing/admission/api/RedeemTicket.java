package io.fluxzero.ticketing.admission.api;

import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.ticketing.admission.TicketCredentials;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Normalize a scanned capability before committing; the capability is not a retained admission event. */
@RequiresUser
public record RedeemTicket(@NotNull PerformanceId performanceId, @NotBlank String credential) {
    public TicketId ticketId() { return TicketCredentials.ticketId(credential); }
    @InterceptApply Object verify(Ticket ticket) {
        TicketCredentials.verify(credential, ticket);
        return new CheckInTicket(ticketId(), performanceId);
    }
}
