package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.booking.api.model.Reservation;

/** Normalized creation, emitted only together with the complete inventory claim. */
public record ReservationHeld(ReservationId reservationId, Reservation reservation) {
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED)
    Reservation apply() { return reservation; }
}
