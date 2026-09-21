package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.access.api.model.Person;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.operations.api.model.StaffAccess;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

import static io.fluxzero.common.api.search.constraints.LookAheadConstraint.lookAhead;

/** Operators can find signed-in people or inspect the current staff list, one page at a time. */
@RequiresAnyRole("OPERATOR")
public record GetStaffDirectory(@NotNull PerformanceId performanceId, boolean assigned,
                                @Size(max = 100) String term, @PositiveOrZero int offset)
        implements Request<GetStaffDirectory.Page> {
    public record Entry(String subject, String name, Set<StaffAccess.Permission> permissions) {}
    public record Page(List<Entry> items, boolean hasMore) {}

    @HandleQuery Page handle() {
        if (assigned) {
            var grants = Fluxzero.search(StaffAccess.class).match(performanceId, true, "performanceId")
                    .sortBy("subject").skip(offset).fetch(21);
            return new Page(grants.stream().limit(20).map(grant -> {
                Person person = Fluxzero.loadModel(grant.subject(), Person.class).get();
                return new Entry(grant.subject(), person == null ? grant.subject() : person.name(), grant.permissions());
            }).toList(), grants.size() > 20);
        }
        var search = Fluxzero.search(Person.class);
        if (term != null && !term.isBlank()) search = search.constraint(lookAhead(term, "name", "subject"));
        var people = search.sortBy("name").sortBy("subject").skip(offset).fetch(21);
        return new Page(people.stream().limit(20).map(person -> {
            var access = Fluxzero.loadModel(StaffAccessId.of(performanceId, person.subject())).get();
            return new Entry(person.subject(), person.name(), access == null ? Set.of() : access.permissions());
        }).toList(), people.size() > 20);
    }
}
