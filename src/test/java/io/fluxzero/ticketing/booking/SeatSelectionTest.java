package io.fluxzero.ticketing.booking;

import io.fluxzero.ticketing.booking.api.GetSeats;
import io.fluxzero.ticketing.booking.api.GetAvailability;
import io.fluxzero.ticketing.booking.api.model.SeatPage;
import io.fluxzero.ticketing.booking.api.model.Availability;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SeatSelectionTest extends TicketingTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void pagesKeepLayoutPositionsAndExpiredHoldsBecomeAvailableWithoutSettlement(boolean async) {
        held(async).whenQuery(new GetSeats(SHOW, "stalls", 0, 2))
                .expectResult((SeatPage page) -> {
                    assertEquals(List.of("A1", "A2"), page.seats().stream().map(s -> s.seat().id()).toList());
                    assertTrue(page.seats().stream().noneMatch(SeatPage.SeatChoice::available));
                    return page.hasMore() && page.total() == 4;
                }).andThen().whenQuery(new GetSeats(SHOW, "stalls", 2, 2))
                .expectResult((SeatPage page) -> !page.hasMore() && page.seats().stream().allMatch(SeatPage.SeatChoice::available))
                .andThen().whenTimeElapses(Duration.ofMinutes(15)).expectSuccessfulResult()
                .andThen().whenQuery(new GetSeats(SHOW, "stalls", 0, 2))
                .expectResult((SeatPage page) -> page.seats().stream().allMatch(SeatPage.SeatChoice::available))
                .andThen().whenQuery(new GetAvailability(SHOW)).expectResult((Availability a) -> a.sections().getFirst().remaining() == 4)
                .andThen().whenQuery(new GetSeats(SHOW, "stalls", 10, 2))
                .expectResult((SeatPage page) -> page.seats().isEmpty() && !page.hasMore());
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void excessiveAndNegativePagesAreRejected(boolean async) {
        fixture(async).whenQuery(new GetSeats(SHOW, "stalls", 0, 101)).expectExceptionalResult()
                .andThen().whenQuery(new GetSeats(SHOW, "stalls", -1, 10)).expectExceptionalResult();
    }
}
