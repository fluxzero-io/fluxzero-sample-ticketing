package io.fluxzero.ticketing.catalog;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.booking.api.GetAvailability;
import io.fluxzero.ticketing.booking.api.ReserveTickets;
import io.fluxzero.ticketing.booking.api.model.Availability;
import io.fluxzero.ticketing.booking.api.model.Selection;
import io.fluxzero.ticketing.catalog.api.*;
import io.fluxzero.ticketing.catalog.api.model.*;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SeatingPlanTest extends TicketingTestSupport {
    private static final HallId HALL = new HallId("concertgebouw-main");
    private static final SeatingPlanId STANDING = new SeatingPlanId("main-standing-v2");
    private static final PerformanceId STANDING_SHOW = new PerformanceId("standing-show");
    private static final SeatingPlanDetails STANDING_LAYOUT = new SeatingPlanDetails("Standing", "2",
            DemoCatalog.NOTICE, List.of(new Section("floor", "Floor", AdmissionMode.GENERAL_ADMISSION, 50, List.of())));

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aNewConfigurationDoesNotMoveExistingSalesAndBothPlansBelongToTheHall(boolean async) {
        fixture(async).givenCommandsByUser(ALICE, seats(R, "A1"))
                .givenCommandsByUser(OPERATOR, new RegisterSeatingPlan(STANDING, HALL, STANDING_LAYOUT))
                .whenCommandByUser(OPERATOR, schedule(STANDING_SHOW, STANDING, "floor"))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(2, Fluxzero.loadGraph(HALL).childModels(SeatingPlan.class).size());
                    assertEquals(List.of(SHOW), Fluxzero.loadGraph(DemoCatalog.MAIN_PLAN).childModels(Performance.class)
                            .stream().map(Performance::performanceId).toList());
                    assertEquals(List.of(STANDING_SHOW), Fluxzero.loadGraph(STANDING).childModels(Performance.class)
                            .stream().map(Performance::performanceId).toList());
                    assertEquals(DemoCatalog.MAIN_PLAN, Fluxzero.loadModel(SHOW).get().seatingPlanId());
                    assertEquals(List.of(new Selection("stalls", "A1")), reservation().admissions().stream().map(a -> new Selection(a.sectionId(), a.seatId())).toList());
                    assertEquals(3, ((Availability) Fluxzero.queryAndWait(new GetAvailability(SHOW))).sections().getFirst().remaining());
                    assertEquals(50, ((Availability) Fluxzero.queryAndWait(new GetAvailability(STANDING_SHOW))).sections().getFirst().remaining());
                    assertTrue(((GetProgramme.Page) Fluxzero.queryAndWait(new GetProgramme(0, 100, null, "Amsterdam", null)))
                            .items().stream().anyMatch(s -> s.performance().performanceId().equals(STANDING_SHOW)));
                });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aRegisteredRevisionCannotBeReplacedEvenBeforeItsFirstPerformance(boolean async) {
        fixture(async).givenCommandsByUser(OPERATOR, new RegisterSeatingPlan(STANDING, HALL, STANDING_LAYOUT))
                .whenCommandByUser(OPERATOR, new RegisterSeatingPlan(STANDING, HALL, ConcertgebouwRecitalHall.LAYOUT))
                .expectExceptionalResult().expectNoEvents().expectThat(f ->
                        assertEquals(STANDING_LAYOUT, Fluxzero.loadModel(STANDING).get().details()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aPlanRequiresAnExistingHallAndOperatorAccess(boolean async) {
        fixture(async).whenCommandByUser(OPERATOR, new RegisterSeatingPlan(STANDING, new HallId("missing"), STANDING_LAYOUT))
                .expectExceptionalResult().expectNoEvents().andThen()
                .whenCommandByUser(ALICE, new RegisterSeatingPlan(STANDING, HALL, STANDING_LAYOUT))
                .expectExceptionalResult().expectNoEvents();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void schedulingRequiresAnExistingPlanAndPricesItsExactSections(boolean async) {
        fixture(async).whenCommandByUser(OPERATOR, schedule(STANDING_SHOW, STANDING, "floor"))
                .expectExceptionalResult().expectNoEvents().andThen()
                .whenCommandByUser(OPERATOR, schedule(STANDING_SHOW, DemoCatalog.MAIN_PLAN, "floor"))
                .expectExceptionalResult().expectNoEvents();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aPerformanceCannotBeReboundToAnotherPlanAfterReservation(boolean async) {
        fixture(async).givenCommandsByUser(ALICE, seats(R, "A1"))
                .givenCommandsByUser(OPERATOR, new RegisterSeatingPlan(STANDING, HALL, STANDING_LAYOUT))
                .whenCommandByUser(OPERATOR, schedule(SHOW, STANDING, "floor"))
                .expectExceptionalResult().expectNoEvents().andThen()
                .whenCommandByUser(BOB, new ReserveTickets(new io.fluxzero.ticketing.booking.api.ReservationId("competing"),
                        SHOW, List.of(new Selection("stalls", "A1"))))
                .expectExceptionalResult().expectNoEvents();
    }

    private static SchedulePerformance schedule(PerformanceId id, SeatingPlanId plan, String section) {
        return new SchedulePerformance(id, new EventId("night-lights"), plan,
                new PerformanceDetails(NOW.plus(Duration.ofDays(2)), ZoneId.of("Europe/Amsterdam"),
                        Map.of(section, new Money(2500, "EUR"))));
    }
}
