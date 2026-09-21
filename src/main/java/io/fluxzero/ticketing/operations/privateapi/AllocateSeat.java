package io.fluxzero.ticketing.operations.privateapi;

import io.fluxzero.sdk.modeling.*;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.booking.api.SeatInventoryId;
import io.fluxzero.ticketing.booking.api.model.SeatInventory;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.operations.api.ProductionHoldId;
import jakarta.annotation.Nullable;
import java.time.Instant;
import static io.fluxzero.ticketing.common.Checks.require;

/** Part of the allocation transaction, sharing the same seat as online sales. */
public record AllocateSeat(SeatInventoryId seatInventoryId, PerformanceId performanceId, String sectionId,
                           String seatId, ProductionHoldId productionHoldId, Instant decidedAt, boolean release) {
    @AssertLegal void validate(@Nullable SeatInventory current) {
        require(release ? current != null && productionHoldId.equals(current.productionHoldId())
                : current == null || !current.occupiedAt(decidedAt), "Seat is unavailable for this allocation");
    }
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED)
    SeatInventory apply(@Nullable SeatInventory current) {
        return new SeatInventory(seatInventoryId, performanceId, sectionId, seatId, null, null, false,
                release ? null : productionHoldId);
    }
}
