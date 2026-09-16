package io.fluxzero.ticketing.queries;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.ticketing.domain.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;
import static io.fluxzero.ticketing.domain.Rules.*;

/** Advisory selection data; ReserveTickets always rechecks the authoritative graph. */
@NoUserRequired
public record GetAvailability(@NotNull PerformanceId performanceId) implements Request<GetAvailability.Availability> {
    public record SectionAvailability(String id, String name, AdmissionMode mode, Money price,
                                      int remaining, List<Seat> availableSeats) {}
    public record Availability(PerformanceId performanceId, HallId hallId, PerformanceDetails details,
                               String layoutNotice, boolean bookable, List<SectionAvailability> sections) {}
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
