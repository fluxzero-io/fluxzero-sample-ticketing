package io.fluxzero.ticketing.catalog;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.catalog.api.*;
import io.fluxzero.ticketing.catalog.api.model.*;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ConcertgebouwSeatingTest extends TicketingTestSupport {
    private static final PerformanceId RECITAL = new PerformanceId("night-lights-matinee");

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void sourceConfigurationPreservesNumberingGapsAccessibilityAndSpatialPagination(boolean async) {
        fixture(async).whenQuery(new GetSeats(RECITAL, "stalls", 0, 100))
                .expectResult((SeatPage page) -> page.total() == 378 && page.seats().size() == 100 && page.hasMore())
                .expectThat(f -> {
                    var layout = Fluxzero.loadModel(DemoCatalog.RECITAL_PLAN).get().details();
                    assertEquals("July 2023", layout.source().revision());
                    var stalls = layout.sections().getFirst();
                    var balcony = layout.sections().getLast();
                    assertEquals(62, balcony.capacity());
                    assertEquals(30, stalls.seats().stream().filter(s -> s.row().equals("0")).count());
                    assertEquals(Seat.Kind.WHEELCHAIR, seat(stalls, "9-1").kind());
                    assertEquals(Seat.Kind.COMPANION, seat(stalls, "9-2").kind());
                    assertEquals(Seat.Kind.WHEELCHAIR, seat(stalls, "10-24").kind());
                    assertTrue(seat(stalls, "1-1").position().x() > seat(stalls, "1-21").position().x());
                    assertTrue(seat(stalls, "13-7").position().x() - seat(stalls, "13-8").position().x() > 6);
                    assertFalse(balcony.seats().stream().anyMatch(s -> s.id().equals("5-3")));
                    assertNotNull(seat(balcony, "Side-3"));
                }).andThen().whenQuery(new GetSeats(RECITAL, "stalls", 300, 100))
                .expectResult((SeatPage page) -> page.seats().size() == 78 && !page.hasMore());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void sameRowAndSeatNumberInDifferentSectionsHaveIndependentInventory(boolean async) {
        var selections = List.of(new Selection("stalls", "1-1"), new Selection("balcony", "1-1"));
        fixture(async).whenCommandByUser(ALICE, new ReserveTickets(R, RECITAL, selections))
                .expectSuccessfulResult().andThen()
                .whenCommandByUser(BOB, new ReserveTickets(new ReservationId("second"), RECITAL,
                        List.of(new Selection("balcony", "1-1"))))
                .expectExceptionalResult().expectNoEvents().andThen()
                .whenQuery(new GetAvailability(RECITAL))
                .expectResult((Availability a) -> a.sections().getFirst().remaining() == 377
                        && a.sections().getLast().remaining() == 61);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aMissingNumberInTheSourcePlanCannotBeSold(boolean async) {
        fixture(async).whenCommandByUser(ALICE, new ReserveTickets(R, RECITAL,
                        List.of(new Selection("balcony", "5-3"))))
                .expectExceptionalResult().expectNoEvents();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void invalidCoordinatesAreRejectedThroughTheDomainCommand(boolean async) {
        var invalid = new Seat("1-1", "1", "1", new SeatPosition(101, 50), Seat.Kind.STANDARD);
        fixture(async).whenCommandByUser(OPERATOR, new RegisterSeatingPlan(new SeatingPlanId("invalid-coordinates"),
                        new HallId("concertgebouw-main"), new SeatingPlanDetails("Invalid", "1", DemoCatalog.NOTICE,
                        List.of(new Section("stalls", "Stalls", AdmissionMode.RESERVED_SEATING, 1, List.of(invalid))))))
                .expectExceptionalResult().expectNoEvents();
    }

    private static Seat seat(Section section, String id) {
        return section.seats().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }
}
