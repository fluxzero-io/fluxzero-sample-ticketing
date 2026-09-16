package io.fluxzero.ticketing;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.ticketing.commands.*;
import io.fluxzero.ticketing.domain.*;
import io.fluxzero.ticketing.queries.GetAvailability;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;
import static org.junit.jupiter.api.Assertions.*;

/** Concurrent calls use the actual command gateway and the SDK's shared runtime store. */
class ConcurrencyTest extends TicketingTestSupport {
    @Test
    void concurrentSeatRequestsHaveExactlyOneWinner() {
        fixture(false).whenExecuting(f -> {
            long wins = compete(12, i -> f.apply(fc -> ALICE.apply(() -> {
                Fluxzero.sendCommandAndWait(seats(new ReservationId("racer-" + i), "A1"));
                return true;
            })));
            assertEquals(1, wins);
            assertEquals(1, Fluxzero.loadGraph(SHOW).childModels(Reservation.class).size());
        }).expectOnlyActiveScheduledCommands(ExpireReservation.class)
                .andThen().whenQuery(new GetAvailability(SHOW))
                .expectResult((GetAvailability.Availability a) -> a.sections().getFirst().remaining() == 3);
    }
    @Test
    void concurrentGeneralAdmissionGroupsNeverExceedCapacity() {
        fixture(false).whenExecuting(f -> {
            assertEquals(2, compete(8, i -> f.apply(fc -> ALICE.apply(() -> {
                Fluxzero.sendCommandAndWait(floor(new ReservationId("ga-racer-" + i), 3));
                return true;
            }))));
            assertEquals(6, Fluxzero.loadGraph(GA).childModels(Reservation.class).stream()
                    .mapToInt(r -> r.admissions().size()).sum());
        }).andThen().whenQuery(new GetAvailability(GA))
                .expectResult((GetAvailability.Availability a) -> a.sections().getFirst().remaining() == 0);
    }
    @Test
    void competingPaymentAttemptsCannotBothBePending() {
        held(false).whenExecuting(f -> {
            assertEquals(1, compete(8, i -> f.apply(fc -> ALICE.apply(() -> {
                Fluxzero.sendCommandAndWait(new StartPayment(new PaymentId("pay-racer-" + i), R));
                return true;
            }))));
            assertEquals(1, Fluxzero.loadGraph(R).childModels(Payment.class).size());
        });
    }
    @Test
    void concurrentCaptureAndCancellationNeverLeaveUsableTicketsOnACancelledReservation() {
        pending(false).whenExecuting(f -> {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                CountDownLatch start = new CountDownLatch(1);
                var capture = executor.submit(() -> { start.await(); return f.apply(fc -> PAYMENTS.apply(() -> Fluxzero.sendCommandAndWait(success()))); });
                var cancel = executor.submit(() -> { start.await(); return f.apply(fc -> ALICE.apply(() -> Fluxzero.sendCommandAndWait(new CancelReservation(R)))); });
                start.countDown(); capture.get(10, TimeUnit.SECONDS); cancel.get(10, TimeUnit.SECONDS);
            }
            assertEquals(ReservationStatus.CANCELLED, reservation().status());
            assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
            assertTrue(Fluxzero.loadGraph(R).childModels(Ticket.class).stream().noneMatch(t -> t.status() == TicketStatus.VALID));
        }).expectNoSchedules();
    }
    private static long compete(int contenders, java.util.function.IntFunction<Boolean> action) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch ready = new CountDownLatch(contenders), start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                int contender = i;
                futures.add(executor.submit(() -> {
                    ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS));
                    try { return action.apply(contender); }
                    catch (IllegalCommandException expectedConflict) { return false; }
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            long wins = 0;
            for (Future<Boolean> future : futures) if (future.get(10, TimeUnit.SECONDS)) wins++;
            return wins;
        }
    }
}
