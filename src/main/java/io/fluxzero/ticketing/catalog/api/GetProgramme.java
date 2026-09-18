package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.ticketing.catalog.api.model.*;
import jakarta.validation.constraints.*;
import java.util.List;
import java.time.*;
import static io.fluxzero.common.api.search.constraints.MatchConstraint.match;
import static io.fluxzero.common.api.search.constraints.LookAheadConstraint.lookAhead;
import static io.fluxzero.common.api.search.constraints.BetweenConstraint.between;


/** An explicitly paged catalogue. Reservation commands recheck all admission rules. */
@NoUserRequired
public record GetProgramme(@PositiveOrZero int offset, @Min(1) @Max(100) int limit, @Size(max = 100) String term, @Size(max = 100) String city, @Pattern(regexp = "^$|\\d{4}-\\d{2}") String month) implements Request<GetProgramme.Page> {
    @HandleQuery Page handle() {
        var search = Fluxzero.search(Performance.class);
        if (term != null && !term.isBlank()) search = search.whereParent(Event.class, lookAhead(term, "details/title"));
        if (city != null && !city.isBlank()) search = search.whereAncestor(Venue.class, match(city, true, "details/city"));
        if (month != null && !month.isBlank()) {
            YearMonth period;
            try { period = YearMonth.parse(month); }
            catch (java.time.DateTimeException e) { throw new io.fluxzero.sdk.tracking.handling.IllegalCommandException("Invalid month"); }
            var zone = ZoneId.of("Europe/Amsterdam");
            search = search.constraint(between(period.atDay(1).atStartOfDay(zone).toInstant(),
                    period.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(), "details/startsAt"));
        }
        var items = search.sortBy("details/startsAt").sortBy("performanceId").skip(offset).fetch(limit + 1);
        return new Page(items.stream().limit(limit).map(GetProgramme::describe).toList(), offset,
                items.size() > limit);
    }
    public static Show describe(Performance performance) {
        var hall = Fluxzero.loadModel(performance.hallId()).get();
        return new Show(new PerformanceSummary(performance.performanceId(), performance.details(), performance.cancellation(),
                layout(performance.layout())), Fluxzero.loadModel(performance.eventId()).get(),
                new HallSummary(hall.hallId(), layout(hall.details())),
                Fluxzero.loadModel(hall.venueId()).get());
    }
    public record Page(List<Show> items, int offset, boolean hasMore) {}
    private static LayoutSummary layout(HallDetails details) {
        return new LayoutSummary(details.name(), details.layoutNotice(), details.sections().stream()
                .map(s -> new SectionSummary(s.id(), s.name(), s.mode())).toList());
    }
    public record Show(PerformanceSummary performance, Event event, HallSummary hall, Venue venue) {}
    public record PerformanceSummary(PerformanceId performanceId, PerformanceDetails details,
                                     Performance.Cancellation cancellation, LayoutSummary layout) {}
    public record HallSummary(HallId hallId, LayoutSummary details) {}
    public record LayoutSummary(String name, String layoutNotice, List<SectionSummary> sections) {}
    public record SectionSummary(String id, String name, AdmissionMode mode) {}
}
