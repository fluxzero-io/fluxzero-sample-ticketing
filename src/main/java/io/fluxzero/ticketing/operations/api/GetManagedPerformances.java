package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.catalog.api.GetProgramme;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.api.model.SalesWindow;
import io.fluxzero.ticketing.operations.api.model.StaffAccess;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@RequiresUser
public record GetManagedPerformances(@PositiveOrZero int offset) implements Request<GetManagedPerformances.Page> {
    public record Item(GetProgramme.Show show, SalesWindow salesWindow, SalesWindow.Status salesStatus) {}
    public record Page(List<Item> items, int offset, boolean hasMore, boolean operator) {}

    @HandleQuery Page handle(User user) {
        List<Performance> performances;
        boolean operator = user.hasRole("OPERATOR");
        boolean hasMore;
        if (operator) {
            var page = Fluxzero.search(Performance.class).sortBy("details/startsAt").sortBy("performanceId")
                    .skip(offset).fetch(21);
            performances = page.stream().limit(20).toList();
            hasMore = page.size() > 20;
        } else {
            var page = Fluxzero.search(StaffAccess.class).match(user.id(), true, "subject")
                    .match(StaffAccess.Permission.MANAGE, true, "permissions")
                    .sortBy("staffAccessId").skip(offset).fetch(21);
            performances = page.stream().limit(20).map(a -> Fluxzero.loadModel(a.performanceId()).get())
                    .filter(Objects::nonNull).toList();
            hasMore = page.size() > 20;
        }
        Instant now = Fluxzero.currentTime();
        return new Page(performances.stream().map(performance -> {
            var window = Fluxzero.loadModel(performance.performanceId(), SalesWindow.class).get();
            return new Item(GetProgramme.describe(performance), window, SalesWindow.statusAt(window, performance, now));
        }).toList(), offset, hasMore, operator);
    }
}
