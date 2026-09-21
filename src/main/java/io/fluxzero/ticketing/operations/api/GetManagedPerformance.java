package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.catalog.api.GetProgramme;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.api.model.SalesWindow;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.common.Checks.require;

@RequiresUser
public record GetManagedPerformance(@NotNull PerformanceId performanceId)
        implements Request<GetManagedPerformance.View> {
    public record View(GetProgramme.Show show, SalesWindow salesWindow,
                       SalesWindow.Status salesStatus, boolean operator) {}

    @HandleQuery View handle(User user) {
        StaffPermission.require(performanceId, user, Permission.MANAGE);
        Performance performance = Fluxzero.loadModel(performanceId).get();
        require(performance != null, "Unknown performance");
        var window = Fluxzero.loadModel(performanceId, SalesWindow.class).get();
        return new View(GetProgramme.describe(performance), window,
                SalesWindow.statusAt(window, performance, Fluxzero.currentTime()), user.hasRole("OPERATOR"));
    }
}
