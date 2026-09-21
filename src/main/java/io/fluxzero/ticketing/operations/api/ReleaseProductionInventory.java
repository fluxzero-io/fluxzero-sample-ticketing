package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.operations.*;
import io.fluxzero.ticketing.operations.api.model.ProductionHold;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import jakarta.validation.constraints.NotNull;

@RequiresUser
public record ReleaseProductionInventory(@NotNull ProductionHoldId productionHoldId) {
    @InterceptApply Object decide(ProductionHold hold, Performance performance, User user) {
        StaffPermission.assertForUser(hold.performanceId(), user, Permission.MANAGE);
        return hold.active() ? ProductionAllocations.changes(hold, performance, Fluxzero.currentTime(), true) : null;
    }
}
