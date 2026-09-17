package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.privateapi.ReservationCancelled;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.booking.ReservationRules.owner;

/** Cancel a complete purchase, void its tickets and require refunds without erasing money. */
@RequiresUser
public record CancelReservation(@NotNull ReservationId reservationId) {
    @InterceptApply
    Object decide(Reservation reservation, Performance performance, User user) {
        owner(reservation, user);
        return ReservationCancelled.changes(reservation, performance, Fluxzero.currentTime());
    }
}
