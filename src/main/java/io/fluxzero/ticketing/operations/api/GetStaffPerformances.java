package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.catalog.api.GetProgramme;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.operations.api.model.StaffAccess;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Objects;

@RequiresUser
public record GetStaffPerformances(@PositiveOrZero int offset) implements Request<GetStaffPerformances.Page> {
    public record Page(List<GetProgramme.Show> items, int offset, boolean hasMore, boolean operator) {}
    @HandleQuery Page handle(User user) {
        if (user.hasRole("OPERATOR")) {
            var items = Fluxzero.search(Performance.class).sortBy("details/startsAt").sortBy("performanceId").skip(offset).fetch(21);
            return new Page(items.stream().limit(20).map(GetProgramme::describe).toList(), offset, items.size() > 20, true);
        }
        var grants = Fluxzero.search(StaffAccess.class).match(user.id(), true, "subject").sortBy("staffAccessId").skip(offset).fetch(21);
        var items = grants.stream().limit(20).map(a -> Fluxzero.loadModel(a.performanceId()).get())
                .filter(Objects::nonNull).map(GetProgramme::describe).toList();
        return new Page(items, offset, grants.size() > 20, false);
    }
}
