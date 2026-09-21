package io.fluxzero.ticketing.admission.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.admission.api.model.Gate;
import io.fluxzero.ticketing.catalog.api.GetProgramme;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.operations.api.StaffAccessId;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import static io.fluxzero.ticketing.common.Checks.require;

@RequiresUser
public record GetAdmissionDesk(@NotNull PerformanceId performanceId) implements Request<GetAdmissionDesk.Desk> {
    public record Desk(GetProgramme.Show show, boolean open, Set<Permission> permissions) {}
    @HandleQuery Desk handle(User user) {
        var access = Fluxzero.loadModel(StaffAccessId.of(performanceId, user.id())).get();
        Set<Permission> permissions = user.hasRole("OPERATOR") ? Set.of(Permission.values()) : access == null ? Set.of() : access.permissions();
        if (permissions.isEmpty()) throw new UnauthorizedException("Staff permission required for this performance");
        var performance = Fluxzero.loadModel(performanceId).get();
        require(performance != null, "Unknown performance");
        Gate gate = Fluxzero.loadModel(performanceId, Gate.class).get();
        return new Desk(GetProgramme.describe(performance), gate != null && gate.open() && !performance.cancelled(), permissions);
    }
}
