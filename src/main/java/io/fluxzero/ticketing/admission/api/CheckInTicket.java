package io.fluxzero.ticketing.admission.api;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.admission.api.model.CheckIn;
import io.fluxzero.ticketing.admission.api.model.Gate;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.booking.api.model.TicketStatus;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import static io.fluxzero.ticketing.common.Checks.require;

/** Admission is an atomic create, not a read-then-write check in a scanner or projection. */
@RequiresUser
public record CheckInTicket(@NotNull TicketId ticketId, @NotNull PerformanceId performanceId) {
    @AssertLegal Object allowed(Ticket ticket, Performance performance, @Nullable Gate gate) {
        require(ticket.performanceId().equals(performanceId), "Ticket belongs to another performance");
        require(ticket.status() == TicketStatus.VALID && !performance.cancelled(), "Ticket is not valid for admission");
        require(gate != null && gate.open(), "Admission is closed");
        return null;
    }
    @AssertLegal Object staff(User user) { return StaffPermission.forUser(performanceId, user, Permission.ADMISSION); }
    @Apply CheckIn apply(User user, Instant timestamp) {
        return new CheckIn(ticketId, timestamp, user.id());
    }
}
