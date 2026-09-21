package io.fluxzero.ticketing.operations.api.model;

import io.fluxzero.sdk.modeling.*;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.operations.api.ProductionHoldId;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

/** An organizer allocation, with retained reason and explicit release; never a customer reservation. */
@Model(persistence = {ModelPersistence.EVENT_SOURCED, ModelPersistence.DOCUMENT})
public record ProductionHold(@EntityId ProductionHoldId productionHoldId,
                             @Parent(pathInParent = "productionHolds") PerformanceId performanceId,
                             Details details, List<Position> positions, String createdBy, Instant createdAt,
                             Instant releasedAt) {
    public ProductionHold { positions = List.copyOf(positions); }
    public record Details(@NotBlank @Size(max = 200) String reason) {}
    public record Position(@NotBlank String sectionId, String seatId, @Positive int quantity) {}
    public boolean active() { return releasedAt == null; }
}
