package io.fluxzero.ticketing.commands;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.ticketing.domain.Reservation;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.domain.Ids.ReservationId;
import static io.fluxzero.ticketing.domain.Values.ReservationStatus;

/** Close a due hold; stale, early and repeated timer deliveries are harmless. */
@NoUserRequired
public record ExpireReservation(@NotNull ReservationId reservationId) {
    @InterceptApply Object ignoreStale(Reservation reservation) {
        return reservation.status() == ReservationStatus.HELD && !Fluxzero.currentTime().isBefore(reservation.expiresAt())
                ? this : null;
    }
    @Apply Reservation apply(Reservation reservation) { return reservation.withStatus(ReservationStatus.EXPIRED); }
}
