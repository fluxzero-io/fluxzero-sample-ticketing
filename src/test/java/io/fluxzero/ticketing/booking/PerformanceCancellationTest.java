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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void pagesResumeAfterALostContinuationWithoutRepeatingSettledPurchases(boolean interruptContinuation) {
        var client = new InventoryScaleTest.MeasuringClient();
        var performanceId = new PerformanceId("large-demo");
        var hallId = new HallId("large-demo");
        var continuations = new java.util.concurrent.atomic.AtomicInteger();
        var configured = builder().addDispatchInterceptor((message, type, topic) -> {
            if (message.getPayload() instanceof io.fluxzero.ticketing.catalog.privateapi.SettlePerformanceCancellation
                    && continuations.incrementAndGet() == 2 && interruptContinuation)
                throw new IllegalStateException("Injected lost cancellation continuation");
            return message;
        }, io.fluxzero.common.MessageType.EVENT);
        var fixture = TestFixture.createAsync(configured, client, new ReservationDeadlines(), new PerformanceCancellation())
                .consumerTimeout(Duration.ofSeconds(30)).atFixedTime(NOW)
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
        var result = fixture.whenCommandByUser(OPERATOR, new CancelPerformance(performanceId)).expectSuccessfulResult();
        if (interruptContinuation) result.expectError(IllegalStateException.class);
        else result.expectNoErrors();
        result.expectNoSchedules().expectThat(f -> {
                    assertTrue(Fluxzero.search(Reservation.class).match(performanceId, true, "performanceId").fetchAll().stream()
                            .allMatch(r -> r.status() == ReservationStatus.CANCELLED));
                    assertEquals(0, Fluxzero.loadModel(new SectionInventoryId(performanceId, "floor")).get().occupiedAt(NOW));
                    long settled = client.measured.stream().filter(c -> c.getSubsteps().stream().anyMatch(s ->
                            s.getEvent().getData().getType().endsWith("ReservationCancelled"))).count();
                    assertEquals(205, settled);
                    assertEquals(Performance.Cancellation.SETTLED, Fluxzero.loadModel(performanceId).get().cancellation());
                    assertEquals(4, continuations.get());
                    assertTrue(client.measured.stream().allMatch(c -> c.getSubsteps().size() <= 2));
                    System.out.printf("Cancellation purchases=205 commits=%d maxSubsteps=%d%n", client.measured.size(),
                            client.measured.stream().mapToInt(c -> c.getSubsteps().size()).max().orElseThrow());
                });
    }
}
