package io.fluxzero.ticketing.booking.api.model;

import io.fluxzero.common.search.Sortable;
import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.ModelPersistence;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.SeatInventoryId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import java.time.Instant;

/** Current ownership of one seat; old reservations do not participate in availability reads. */
@Model(persistence = ModelPersistence.DOCUMENT)
public record SeatInventory(@EntityId SeatInventoryId seatInventoryId,
                            @Parent(pathInParent = "seatInventory") PerformanceId performanceId,
                            String sectionId, String seatId, ReservationId reservationId,
                            @Sortable Instant expiresAt, boolean sold, io.fluxzero.ticketing.operations.api.ProductionHoldId productionHoldId) {
    public boolean occupiedAt(Instant now) {
        return productionHoldId != null || reservationId != null && (sold || now.isBefore(expiresAt));
    }
}
