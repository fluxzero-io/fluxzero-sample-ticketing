package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.ticketing.booking.api.model.Admission;
import io.fluxzero.ticketing.booking.api.model.Availability;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.SectionAvailability;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.api.model.Seat;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

import static io.fluxzero.ticketing.common.Checks.require;

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
        List<Admission> occupied = graph.childModels(Reservation.class).stream().filter(r -> r.occupiesAt(now))
                .flatMap(r -> r.admissions().stream()).toList();
        List<SectionAvailability> sections = performance.layout().sections().stream().map(s -> {
            List<Admission> used = occupied.stream().filter(a -> a.sectionId().equals(s.id())).toList();
            List<Seat> seats = bookable ? s.seats().stream()
                    .filter(seat -> used.stream().noneMatch(a -> seat.id().equals(a.seatId()))).toList() : List.of();
            return new SectionAvailability(s.id(), s.name(), s.mode(), performance.details().sectionPrices().get(s.id()),
                    bookable ? s.capacity() - used.size() : 0, seats);
        }).toList();
        return new Availability(performanceId, performance.hallId(), performance.details(),
                performance.layout().layoutNotice(), bookable, sections);
    }
}
