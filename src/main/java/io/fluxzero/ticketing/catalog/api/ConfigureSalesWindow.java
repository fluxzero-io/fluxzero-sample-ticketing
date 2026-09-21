package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.api.model.SalesWindow;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

import static io.fluxzero.ticketing.common.Checks.require;

/** Set the period for accepting new reservations without changing existing holds. */
@RequiresUser
public record ConfigureSalesWindow(@NotNull PerformanceId performanceId,
                                   @NotNull Instant opensAt, @NotNull Instant closesAt) {
    @AssertLegal Object allowed(Performance performance, User user) {
        require(opensAt.isBefore(closesAt), "Sales window must open before it closes");
        require(!closesAt.isAfter(performance.details().startsAt()), "Sales must close by performance start");
        require(!performance.cancelled(), "Performance is cancelled");
        return StaffPermission.forUser(performanceId, user, Permission.MANAGE);
    }

    @Apply SalesWindow apply(@Nullable SalesWindow current) {
        return new SalesWindow(performanceId, opensAt, closesAt);
    }
}
