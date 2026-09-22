package io.fluxzero.ticketing.waitlist.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.booking.privateapi.ReservationCancelled;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.waitlist.api.model.WaitlistEntry;
import io.fluxzero.ticketing.waitlist.privateapi.WaitlistLeft;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import static io.fluxzero.ticketing.common.Checks.require;

@RequiresUser
public record LeaveWaitlist(@NotNull WaitlistEntryId waitlistEntryId) {
    @InterceptApply Object decide(WaitlistEntry entry, Performance performance, User user) {
        if (!entry.customerId().equals(user.id())) throw new UnauthorizedException("Waitlist request belongs to another customer");
        if (entry.state() == WaitlistEntry.State.LEFT) return null;
        var changes = new ArrayList<Object>();
        if (entry.reservationId() != null) {
            var reservation = Fluxzero.loadModel(entry.reservationId()).get();
            require(reservation != null && reservation.status() != ReservationStatus.CONFIRMED, "Manage purchased tickets from your booking");
            changes.addAll(ReservationCancelled.changes(reservation, performance, Fluxzero.currentTime()));
        }
        changes.add(new WaitlistLeft(waitlistEntryId));
        return changes;
    }
}
