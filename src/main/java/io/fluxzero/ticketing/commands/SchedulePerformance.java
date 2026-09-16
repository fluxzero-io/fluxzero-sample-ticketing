package io.fluxzero.ticketing.commands;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.domain.Hall;
import io.fluxzero.ticketing.domain.Performance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.domain.Ids.EventId;
import static io.fluxzero.ticketing.domain.Ids.HallId;
import static io.fluxzero.ticketing.domain.Ids.PerformanceId;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.domain.Rules.section;
import static io.fluxzero.ticketing.domain.Values.PerformanceDetails;
import static io.fluxzero.ticketing.domain.Values.Section;

/** Open booking for one performance with a frozen hall layout and section prices. */
@RequiresAnyRole("OPERATOR")
public record SchedulePerformance(@NotNull PerformanceId performanceId, @NotNull EventId eventId,
                                  @NotNull HallId hallId, @NotNull @Valid PerformanceDetails details) {
    @AssertLegal void validate(Hall hall, io.fluxzero.ticketing.domain.Event event) {
        require(details.startsAt().isAfter(Fluxzero.currentTime()), "Performance must start in the future");
        require(details.sectionPrices().keySet().equals(hall.details().sections().stream()
                .map(Section::id).collect(java.util.stream.Collectors.toSet())), "Price every section exactly once");
    }
    @Apply Performance apply(Hall hall) {
        return new Performance(performanceId, eventId, hallId, details, hall.details(), false);
    }
}
