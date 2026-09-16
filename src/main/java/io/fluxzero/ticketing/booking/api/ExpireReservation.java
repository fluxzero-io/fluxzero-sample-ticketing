package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

/** Close a due hold; stale, early and repeated timer deliveries are harmless. */
@NoUserRequired
public record ExpireReservation(@NotNull ReservationId reservationId) {
    @InterceptApply Object ignoreStale(@Nullable Reservation reservation) {
        return reservation != null && reservation.status() == ReservationStatus.HELD && !Fluxzero.currentTime().isBefore(reservation.expiresAt())
                ? this : null;
    }
    @Apply Reservation apply(Reservation reservation) { return reservation.withStatus(ReservationStatus.EXPIRED); }
}
