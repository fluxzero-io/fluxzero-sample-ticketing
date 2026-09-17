package io.fluxzero.ticketing.booking.api;

import io.fluxzero.common.api.search.constraints.BetweenConstraint;
import io.fluxzero.common.api.search.constraints.MatchConstraint;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.ticketing.booking.api.model.Availability;
import io.fluxzero.ticketing.booking.api.model.SeatInventory;
import io.fluxzero.ticketing.booking.api.model.SectionAvailability;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.AdmissionMode;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

import static io.fluxzero.ticketing.common.Checks.require;
import static java.time.temporal.ChronoUnit.SECONDS;

/** Advisory selection data; ReserveTickets always rechecks the authoritative graph. */
@NoUserRequired
public record GetAvailability(@NotNull PerformanceId performanceId) implements Request<Availability> {

    @HandleQuery
    Availability handle() {
        var graph = Fluxzero.loadGraph(performanceId);
        Performance performance = graph.get();
        require(performance != null, "Unknown performance");
        Instant now = Fluxzero.currentTime();
        boolean bookable = !performance.cancelled() && now.isBefore(performance.details().startsAt());
        List<SectionAvailability> sections = performance.layout().sections().stream().map(s -> {
            int remaining = 0;
            if (bookable) {
                if (s.mode() == AdmissionMode.RESERVED_SEATING) {
                    long occupied = Fluxzero.search(SeatInventory.class)
                            .match(performanceId, true, "performanceId").match(s.id(), true, "sectionId")
                            .any(MatchConstraint.match(true, true, "sold"),
                                    BetweenConstraint.atLeast(
                                            now.truncatedTo(SECONDS).plusSeconds(1), "expiresAt"))
                            .count();
                    remaining = Math.toIntExact(s.capacity() - occupied);
                }
                else {
                    var stock = Fluxzero.loadModel(new SectionInventoryId(performanceId, s.id())).get();
                    remaining = Math.toIntExact(s.capacity() - (stock == null ? 0 : stock.occupiedAt(now)));
                }
            }
            return new SectionAvailability(s.id(), s.name(), s.mode(), performance.details().sectionPrices().get(s.id()), remaining);
        }).toList();
        return new Availability(performanceId, performance.hallId(), performance.details(),
                performance.layout().layoutNotice(), bookable, sections);
    }
}
