package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.tracking.handling.IllegalCommandException;

/** Expected business refusals shared by commands and behavior tests. */
public interface BookingErrors {
    IllegalCommandException seatUnavailable = new IllegalCommandException("Seat is unavailable");
    IllegalCommandException sectionCapacityExceeded = new IllegalCommandException("Section capacity exceeded");
}
