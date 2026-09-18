package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.catalog.api.model.*;
import io.fluxzero.ticketing.payment.api.model.Money;
import java.time.*;
import java.util.Map;

/** Explicit local startup command, never an HTTP operation or production startup hook. */
@RequiresAnyRole("OPERATOR")
public record SeedDemoProgramme() {
    @InterceptApply java.util.List<Object> apply() {
        if (!Fluxzero.loadGraph(new VenueId("concertgebouw")).isEmpty()) return java.util.List.of();
        var first = Fluxzero.currentTime().atZone(ZoneId.of("Europe/Amsterdam")).toLocalDate()
                .plusWeeks(3).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                .atTime(20, 0).atZone(ZoneId.of("Europe/Amsterdam")).toInstant();
        var commands = new java.util.ArrayList<>(DemoCatalog.commands(first));
        var event = new EventId("after-hours");
        commands.add(new CreateEvent(event, new EventDetails("After Hours", "Fictional electronic live set.")));
        commands.add(new SchedulePerformance(new PerformanceId("after-hours"), event,
                DemoCatalog.RONDA_PLAN, new PerformanceDetails(first.plus(Duration.ofDays(6)).plusSeconds(7200),
                ZoneId.of("Europe/Amsterdam"), Map.of("floor", new Money(2800, "EUR")))));
        return commands;
    }
}
