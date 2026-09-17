package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.booking.api.model.SectionInventory;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.Map;

import static io.fluxzero.ticketing.common.Checks.require;

/** Internal count adjustment committed together with its reservation transition. */
public record ChangeSectionInventory(SectionInventoryId sectionInventoryId, PerformanceId performanceId,
                                     Instant expiresAt, Instant decidedAt, int heldDelta, int soldDelta, int capacity) {
    @AssertLegal void validate(@Nullable SectionInventory current) {
        if (heldDelta > 0) require((current == null ? 0L : current.occupiedAt(decidedAt)) + heldDelta <= capacity,
                "Section capacity exceeded");
        var next = apply(current);
        require(next.sold() >= 0 && next.holds().values().stream().allMatch(n -> n > 0), "Invalid inventory release");
        require(next.occupiedAt(decidedAt) <= capacity, "Section capacity exceeded");
    }
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED)
    SectionInventory apply(@Nullable SectionInventory current) {
        var stock = current == null ? new SectionInventory(sectionInventoryId, performanceId, 0, Map.of()) : current;
        return stock.change(decidedAt, expiresAt, heldDelta, soldDelta);
    }
}
