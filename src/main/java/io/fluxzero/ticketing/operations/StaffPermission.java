package io.fluxzero.ticketing.operations;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.operations.api.StaffAccessId;
import io.fluxzero.ticketing.operations.api.model.StaffAccess;
import jakarta.annotation.Nullable;

/** Recursive assertion keeps the particular grant in the atomic command's read dependencies. */
public record StaffPermission(StaffAccessId staffAccessId, StaffAccess.Permission permission) {
    public static StaffPermission forUser(PerformanceId performanceId, User user, StaffAccess.Permission permission) {
        return user.hasRole("OPERATOR") ? null : new StaffPermission(StaffAccessId.of(performanceId, user.id()), permission);
    }

    public static void require(PerformanceId performanceId, User user, StaffAccess.Permission permission) {
        if (user.hasRole("OPERATOR")) return;
        var access = Fluxzero.getDocument(StaffAccessId.of(performanceId, user.id()), StaffAccess.class).orElse(null);
        if (access == null || !access.permissions().contains(permission))
            throw new UnauthorizedException("Staff permission required for this performance");
    }
    @AssertLegal void permitted(@Nullable StaffAccess access) {
        if (access == null || !access.permissions().contains(permission))
            throw new UnauthorizedException("Staff permission required for this performance");
    }
}
