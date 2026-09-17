package io.fluxzero.ticketing.booking;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.booking.api.model.Selection;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.catalog.PerformanceCancellation;
import io.fluxzero.ticketing.catalog.api.*;
import io.fluxzero.ticketing.catalog.api.model.*;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceCancellationTest extends TicketingTestSupport {
    @Test
    void theGateClosesBeforeAnyPurchaseSettlementRuns() {
        TestFixture.createAsync(builder()).atFixedTime(NOW)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1", "A2"), new StartPayment(P, R))
                .whenCommandByUser(OPERATOR, new CancelPerformance(SHOW)).expectSuccessfulResult()
                .expectThat(f -> {
                    assertTrue(Fluxzero.loadModel(SHOW).get().cancelled());
                    assertEquals(ReservationStatus.HELD, reservation().status());
                }).andThen().whenQueryByUser(ALICE, new GetReservation(R))
                .expectResult((io.fluxzero.ticketing.booking.api.model.Purchase p) -> p.performanceCancelled())
                .andThen().whenCommandByUser(BOB, seats(new ReservationId("too-late"), "B1"))
                .expectExceptionalResult().andThen().whenCommandByUser(PAYMENTS, success())
                .expectSuccessfulResult().expectThat(f -> assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status()));
    }

    @Test
    void moreThanOnePageOfPurchasesSettlesInBoundedIndependentCommits() {
        var client = new InventoryScaleTest.MeasuringClient();
        var performanceId = new PerformanceId("large-demo");
        var hallId = new HallId("large-demo");
        var fixture = TestFixture.createAsync(builder(), client, new ReservationDeadlines(), new PerformanceCancellation()).atFixedTime(NOW)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(OPERATOR,
                        new CreateHall(hallId, new VenueId("concertgebouw"), new HallDetails("Capacity test", DemoCatalog.NOTICE,
                                List.of(new Section("floor", "Floor", AdmissionMode.GENERAL_ADMISSION, 1000, List.of())))),
                        new SchedulePerformance(performanceId, new EventId("night-lights"), hallId,
                                new PerformanceDetails(NOW.plus(Duration.ofDays(1)), ZoneId.of("Europe/Amsterdam"),
                                        Map.of("floor", new Money(1000, "EUR")))));
        fixture = fixture.givenCommandsByUser(ALICE, java.util.stream.IntStream.range(0, 205)
                .mapToObj(i -> new ReserveTickets(new ReservationId("cancel-" + i), performanceId,
                        List.of(new Selection("floor", null)))).toArray());
        client.measured.clear();
        fixture.whenCommandByUser(OPERATOR, new CancelPerformance(performanceId)).expectSuccessfulResult().expectNoErrors()
                .expectNoSchedules().expectThat(f -> {
                    assertTrue(Fluxzero.search(Reservation.class).match(performanceId, true, "performanceId").fetchAll().stream()
                            .allMatch(r -> r.status() == ReservationStatus.CANCELLED));
                    assertEquals(0, Fluxzero.loadModel(new SectionInventoryId(performanceId, "floor")).get().occupiedAt(NOW));
                    long settled = client.measured.stream().filter(c -> c.getSubsteps().stream().anyMatch(s ->
                            s.getEvent().getData().getType().endsWith("ReservationCancelled"))).count();
                    assertEquals(205, settled);
                    assertTrue(client.measured.stream().allMatch(c -> c.getSubsteps().size() <= 2));
                    System.out.printf("Cancellation purchases=205 commits=%d maxSubsteps=%d%n", client.measured.size(),
                            client.measured.stream().mapToInt(c -> c.getSubsteps().size()).max().orElseThrow());
                });
    }
}
