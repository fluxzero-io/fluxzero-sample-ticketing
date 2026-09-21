package io.fluxzero.ticketing.admission.api;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.admission.api.model.Gate;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import static io.fluxzero.ticketing.common.Checks.require;

@RequiresUser
public record SetGateOpen(@NotNull PerformanceId performanceId, boolean open) {
    @AssertLegal Object allowed(Performance performance, User user) {
        require(!open || !performance.cancelled(), "A cancelled performance cannot admit visitors");
        return StaffPermission.forUser(performanceId, user, Permission.MANAGE);
    }
    @Apply Gate apply(@Nullable Gate current) { return new Gate(performanceId, open); }
}
