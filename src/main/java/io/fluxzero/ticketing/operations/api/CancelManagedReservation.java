package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.ticketing.admission.api.model.CheckIn;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.booking.privateapi.ReservationCancelled;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.common.Checks.require;

/** Cancel one managed order and retain any resulting refund obligation. */
@RequiresUser
public record CancelManagedReservation(@NotNull ReservationId reservationId) {
    @AssertLegal Object allowed(Reservation reservation, User user) {
        return StaffPermission.forUser(reservation.performanceId(), user, Permission.MANAGE);
    }

    @InterceptApply Object decide(Graph<Reservation> graph, Performance performance) {
        Reservation reservation = graph.get();
        if (reservation.status() == ReservationStatus.CANCELLED) return null;
        require(graph.descendantModels("tickets/checkIns", CheckIn.class).isEmpty(),
                "An admitted order cannot be cancelled; it requires a separate support decision");
        require(reservation.status() == ReservationStatus.HELD || reservation.status() == ReservationStatus.CONFIRMED,
                "Only active orders can be cancelled");
        return ReservationCancelled.changes(reservation, performance, Fluxzero.currentTime());
    }
}
