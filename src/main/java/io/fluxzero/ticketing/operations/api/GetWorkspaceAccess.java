package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.operations.api.model.StaffAccess;

@RequiresUser
public record GetWorkspaceAccess() implements Request<GetWorkspaceAccess.Access> {
    public record Access(boolean manage, boolean admission) {}
    @HandleQuery Access handle(User user) {
        if (user.hasRole("OPERATOR")) return new Access(true, true);
        return new Access(has(user, StaffAccess.Permission.MANAGE), has(user, StaffAccess.Permission.ADMISSION));
    }
    private static boolean has(User user, StaffAccess.Permission permission) {
        return !Fluxzero.search(StaffAccess.class).match(user.id(), true, "subject")
                .match(permission, true, "permissions").fetch(1).isEmpty();
    }
}
