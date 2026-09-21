package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.*;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.ProductionHold;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import jakarta.validation.constraints.*;
import java.util.List;

@RequiresUser
public record GetProductionHolds(@NotNull PerformanceId performanceId, @PositiveOrZero int offset)
        implements Request<GetProductionHolds.Page> {
    public record Page(List<ProductionHold> items, int offset, boolean hasMore) {}
    @HandleQuery Page handle(User user) {
        StaffPermission.require(performanceId, user, Permission.MANAGE);
        var holds = Fluxzero.search(ProductionHold.class).match(performanceId, true, "performanceId")
                .sortBy("createdAt", true).sortBy("productionHoldId").skip(offset).fetch(21);
        return new Page(holds.stream().limit(20).toList(), offset, holds.size() > 20);
    }
}
