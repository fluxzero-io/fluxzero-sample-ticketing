package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.catalog.api.model.Event;
import io.fluxzero.ticketing.catalog.api.model.SeatingPlan;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.api.model.PerformanceDetails;
import io.fluxzero.ticketing.catalog.api.model.Section;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.common.Checks.require;

/** Open booking for one performance with an immutable seating plan and section prices. */
@RequiresAnyRole("OPERATOR")
public record SchedulePerformance(@NotNull PerformanceId performanceId, @NotNull EventId eventId,
                                  @NotNull SeatingPlanId seatingPlanId, @NotNull @Valid PerformanceDetails details) {
    @AssertLegal void validate(SeatingPlan plan, Event event) {
        require(details.startsAt().isAfter(Fluxzero.currentTime()), "Performance must start in the future");
        require(details.sectionPrices().keySet().equals(plan.details().sections().stream()
                .map(Section::id).collect(java.util.stream.Collectors.toSet())), "Price every section exactly once");
    }
    @Apply Performance apply(SeatingPlan plan) {
        return new Performance(performanceId, eventId, seatingPlanId, details, Performance.Cancellation.NONE);
    }
}
