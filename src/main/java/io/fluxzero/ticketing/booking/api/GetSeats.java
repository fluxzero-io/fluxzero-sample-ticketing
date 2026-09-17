package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.ticketing.booking.api.model.SeatInventory;
import io.fluxzero.ticketing.booking.api.model.SeatPage;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.AdmissionMode;
import io.fluxzero.ticketing.catalog.api.model.Seat;
import jakarta.validation.constraints.*;
import java.util.stream.Collectors;

import static io.fluxzero.ticketing.catalog.CatalogRules.section;
import static io.fluxzero.ticketing.common.Checks.require;

/** Advisory section selection, with at most 100 layout seats and one bounded inventory search. */
@NoUserRequired
public record GetSeats(@NotNull PerformanceId performanceId, @NotBlank String sectionId,
                       @PositiveOrZero int offset, @Min(1) @Max(100) int limit) implements Request<SeatPage> {
    @HandleQuery SeatPage handle() {
        var performance = Fluxzero.loadModel(performanceId).get();
        require(performance != null, "Unknown performance");
        var section = section(performance, sectionId);
        require(section.mode() == AdmissionMode.RESERVED_SEATING, "Section has no reserved seats");
        int total = section.seats().size();
        var seats = section.seats().subList(Math.min(offset, total), (int) Math.min((long) offset + limit, total));
        var now = Fluxzero.currentTime();
        boolean bookable = !performance.cancelled() && now.isBefore(performance.details().startsAt());
        var stocks = seats.isEmpty() ? java.util.Map.<String, SeatInventory>of() : Fluxzero.search(SeatInventory.class)
                .match(performanceId, true, "performanceId").match(sectionId, true, "sectionId")
                .match(seats.stream().map(Seat::id).toList(), true, "seatId")
                .fetch(limit).stream().collect(Collectors.toMap(SeatInventory::seatId, s -> s));
        return new SeatPage(seats.stream().map(seat -> new SeatPage.SeatChoice(seat,
                bookable && (stocks.get(seat.id()) == null || !stocks.get(seat.id()).occupiedAt(now)))).toList(),
                offset, total, (long) offset + seats.size() < total);
    }
}
