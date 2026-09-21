package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.catalog.api.model.Event;
import io.fluxzero.ticketing.catalog.api.SeatingPlanId;
import io.fluxzero.ticketing.catalog.api.model.SeatingPlan;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.ZoneId;
import java.util.List;

/** Independent catalogue pages; never traverses bookings or sends seat geometry. */
@RequiresAnyRole("OPERATOR")
public record GetOrganizerCatalog(@PositiveOrZero int eventOffset, @PositiveOrZero int planOffset)
        implements Request<GetOrganizerCatalog.Options> {
    public record Section(String id, String name) {}
    public record Plan(SeatingPlanId id, String name, String hall, String venue,
                       ZoneId timeZone, List<Section> sections) {}
    public record Options(List<Event> events, boolean moreEvents, List<Plan> plans, boolean morePlans) {}

    @HandleQuery Options handle() {
        var events = Fluxzero.search(Event.class).sortBy("eventId").skip(eventOffset).fetch(21);
        var plans = Fluxzero.search(SeatingPlan.class).sortBy("seatingPlanId").skip(planOffset).fetch(21);
        return new Options(events.stream().limit(20).toList(), events.size() > 20,
                plans.stream().limit(20).map(GetOrganizerCatalog::describe).toList(), plans.size() > 20);
    }

    private static Plan describe(SeatingPlan plan) {
        var hall = Fluxzero.loadModel(plan.hallId()).get();
        var venue = Fluxzero.loadModel(hall.venueId()).get();
        return new Plan(plan.seatingPlanId(), plan.details().name(), hall.details().name(),
                venue.details().name(), venue.details().timeZone(), plan.details().sections().stream()
                .map(section -> new Section(section.id(), section.name())).toList());
    }
}
