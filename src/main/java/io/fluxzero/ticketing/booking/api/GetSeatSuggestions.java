package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.ticketing.booking.api.model.SeatPage;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.Seat;
import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Advisory groups of standard seats. Each request inspects at most 100 inventory positions. */
@NoUserRequired
public record GetSeatSuggestions(@NotNull PerformanceId performanceId, @NotBlank String sectionId,
                                 @Min(1) @Max(12) int quantity, @PositiveOrZero int offset)
        implements Request<GetSeatSuggestions.Page> {
    public record Page(List<List<Seat>> groups, int nextOffset, boolean hasMore) {}

    @HandleQuery Page handle() {
        SeatPage page = Fluxzero.queryAndWait(new GetSeats(performanceId, sectionId, offset, 100));
        var result = new ArrayList<List<Seat>>();
        int examined = 0;
        int starts = Math.max(0, page.seats().size() - quantity + 1);
        if (starts == 0) return new Page(List.of(), page.total(), false);
        for (; examined < starts && result.size() < 5; examined++) {
            var group = page.seats().subList(examined, examined + quantity);
            if (group.stream().anyMatch(s -> !s.available() || s.seat().kind() != Seat.Kind.STANDARD)) continue;
            boolean adjacent = true;
            for (int i = 1; i < group.size(); i++) {
                if (!Objects.equals(group.get(i - 1).seat().nextSeatId(), group.get(i).seat().id())) {
                    adjacent = false;
                    break;
                }
            }
            if (adjacent) result.add(group.stream().map(SeatPage.SeatChoice::seat).toList());
        }
        int next = offset + examined;
        return new Page(List.copyOf(result), next, next <= page.total() - quantity);
    }
}
