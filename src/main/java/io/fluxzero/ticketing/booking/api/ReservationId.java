package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.booking.api.model.Reservation;

public final class ReservationId extends Id<Reservation> {
    public ReservationId(String value) { super(value, "reservation-"); }
}
