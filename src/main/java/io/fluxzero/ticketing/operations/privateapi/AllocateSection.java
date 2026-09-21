package io.fluxzero.ticketing.operations.privateapi;

import io.fluxzero.sdk.modeling.*;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.booking.api.SectionInventoryId;
import io.fluxzero.ticketing.booking.api.model.SectionInventory;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.Map;
import static io.fluxzero.ticketing.common.Checks.require;

public record AllocateSection(SectionInventoryId sectionInventoryId, PerformanceId performanceId,
                              int delta, int capacity, Instant decidedAt) {
    @AssertLegal void validate(@Nullable SectionInventory current) {
        var next = apply(current);
        require(next.blocked() >= 0 && next.occupiedAt(decidedAt) <= capacity, "Section allocation exceeds available capacity");
    }
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED)
    SectionInventory apply(@Nullable SectionInventory current) {
        var stock = current == null ? new SectionInventory(sectionInventoryId, performanceId, 0, Map.of(), 0) : current;
        return new SectionInventory(sectionInventoryId, performanceId, stock.sold(), stock.holds(),
                Math.addExact(stock.blocked(), delta));
    }
}
