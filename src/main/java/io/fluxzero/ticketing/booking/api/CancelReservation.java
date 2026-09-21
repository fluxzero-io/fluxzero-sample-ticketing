package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.admission.api.model.CheckIn;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.privateapi.ReservationCancelled;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.booking.ReservationRules.owner;
import static io.fluxzero.ticketing.common.Checks.require;

/** Cancel a complete purchase, void its tickets and require refunds without erasing money. */
@RequiresUser
public record CancelReservation(@NotNull ReservationId reservationId) {
    @InterceptApply
    Object decide(Graph<Reservation> graph, Performance performance, User user) {
        Reservation reservation = graph.get();
        owner(reservation, user);
        require(graph.descendantModels(CheckIn.class).isEmpty(), "A used ticket cannot be cancelled by its customer");
        return ReservationCancelled.changes(reservation, performance, Fluxzero.currentTime());
    }
}
