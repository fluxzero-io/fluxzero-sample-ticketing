package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.booking.api.model.SeatInventory;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import jakarta.annotation.Nullable;
import java.time.Instant;

import static io.fluxzero.ticketing.common.Checks.require;

/** Internal part of an atomic reservation decision, never a standalone command. */
public record ChangeSeatInventory(SeatInventoryId seatInventoryId, PerformanceId performanceId,
                                  ReservationId reservationId, Instant expiresAt, Instant decidedAt, Action action) {
    public enum Action { HOLD, SELL, RELEASE }
    @AssertLegal void validate(@Nullable SeatInventory current) {
        if (action == Action.HOLD) require(current == null || !current.occupiedAt(decidedAt), "Seat is unavailable");
        if (action == Action.SELL) require(current != null && reservationId.equals(current.reservationId())
                && current.occupiedAt(decidedAt) && !current.sold(), "Seat hold is no longer owned by this reservation");
    }
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED)
    SeatInventory apply(@Nullable SeatInventory current) {
        if (action == Action.RELEASE) {
            return current != null && reservationId.equals(current.reservationId())
                    ? new SeatInventory(seatInventoryId, performanceId, null, null, false) : current;
        }
        return new SeatInventory(seatInventoryId, performanceId, reservationId, expiresAt, action == Action.SELL);
    }
}
