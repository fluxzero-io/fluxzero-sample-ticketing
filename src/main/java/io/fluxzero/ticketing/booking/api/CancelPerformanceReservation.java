package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.booking.privateapi.ReservationCancelled;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.common.Checks.require;

/** Recheck authoritative state before settling one purchase from a cancellation search page. */
@RequiresAnyRole("OPERATOR")
public record CancelPerformanceReservation(@NotNull ReservationId reservationId) {
    @InterceptApply Object decide(@Nullable Reservation reservation, @Nullable Performance performance) {
        if (reservation == null || performance == null) return null;
        require(performance.cancelled(), "Performance is not cancelled");
        if (reservation.status() != ReservationStatus.HELD && reservation.status() != ReservationStatus.CONFIRMED) return null;
        return ReservationCancelled.changes(reservation, performance, Fluxzero.currentTime());
    }
}
